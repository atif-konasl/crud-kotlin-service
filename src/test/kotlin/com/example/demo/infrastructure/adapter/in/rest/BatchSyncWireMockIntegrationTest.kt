package com.example.demo.infrastructure.adapter.`in`.rest

import com.example.demo.infrastructure.adapter.out.persistence.SpringDataResourceRepository
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class BatchSyncWireMockIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var springDataResourceRepository: SpringDataResourceRepository

    companion object {
        private lateinit var wireMockServer: WireMockServer

        @BeforeAll
        @JvmStatic
        fun startWireMock() {
            wireMockServer =
                WireMockServer(
                    wireMockConfig()
                        .dynamicPort()
                        .containerThreads(50)
                        .asynchronousResponseEnabled(true)
                        .asynchronousResponseThreads(50),
                )
            wireMockServer.start()
        }

        @AfterAll
        @JvmStatic
        fun stopWireMock() {
            wireMockServer.stop()
        }

        @DynamicPropertySource
        @JvmStatic
        fun dynamicProperties(registry: DynamicPropertyRegistry) {
            registry.add("external.client.base-url") { "http://localhost:${wireMockServer.port()}" }
        }
    }

    @BeforeEach
    fun cleanDatabase() {
        springDataResourceRepository.deleteAll()
        wireMockServer.resetAll()
    }

    @Test
    fun `should call 100 external APIs concurrently via WireMock and persist all 100 resources into DB`() {
        val totalCalls = 100

        val simulatedLatencyMs = 60 // 60ms simulated network latency per call

        // 1. Stub 100 unique endpoints in WireMock with network latency
        for (i in 1..totalCalls) {
            val refId = "ref-$i"
            val category = if (i % 2 == 0) "electronics" else "machinery"
            val riskScore = (i * 7) % 100

            wireMockServer.stubFor(
                get(urlEqualTo("/api/v1/enrichment/$refId"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withFixedDelay(simulatedLatencyMs)
                            .withBody(
                                """
                                {
                                    "referenceId": "$refId",
                                    "category": "$category",
                                    "riskScore": $riskScore,
                                    "verified": true,
                                    "tags": ["batch", "wiremock", "tag-$i"]
                                }
                                """.trimIndent(),
                            ),
                    ),
            )
        }

        // 2. Trigger batch-sync endpoint requesting 100 items with parallelism limit of 20
        val requestPayload =
            """
            {
                "count": $totalCalls,
                "referencePrefix": "ref",
                "parallelism": 20
            }
            """.trimIndent()

        val mvcResult =
            mockMvc
                .perform(
                    post("/api/v1/resources/batch-sync")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload),
                ).andExpect(request().asyncStarted())
                .andReturn()

        // 3. Verify HTTP 201 Created and JSON summary response
        val asyncResult =
            mockMvc
                .perform(asyncDispatch(mvcResult))
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.totalRequested").value(totalCalls))
                .andExpect(jsonPath("$.totalPersisted").value(totalCalls))
                .andExpect(jsonPath("$.items.length()").value(totalCalls))
                .andReturn()

        // Verify parallel execution: sequential would take 100 * 60ms = 6000ms.
        // With parallelism = 20 (5 waves of 20), it finishes in ~300ms - 1500ms.
        val actualDurationMs =
            com.jayway.jsonpath.JsonPath
                .read<Number>(asyncResult.response.contentAsString, "$.durationMs")
                .toLong()
        assertTrue(
            actualDurationMs < 4500,
            "Expected parallel execution (< 4500ms) but sequential execution took ${actualDurationMs}ms",
        )

        // 4. Verify WireMock received exactly 100 external HTTP requests
        wireMockServer.verify(totalCalls, getRequestedFor(urlPathMatching("/api/v1/enrichment/.*")))

        // 5. Verify database contains all 100 persisted resources
        val allEntities = springDataResourceRepository.findAll()
        assertEquals(totalCalls, allEntities.size)

        // Verify a specific entity has normalized enrichment data
        val entityRef10 = allEntities.find { it.name == "Resource for ref-10" }
        assertTrue(entityRef10 != null, "Entity for ref-10 should be saved")
        assertTrue(entityRef10!!.externalEnrichmentData!!.contains("category=ELECTRONICS"))
        assertTrue(entityRef10.externalEnrichmentData!!.contains("ref=ref-10"))
    }
}
