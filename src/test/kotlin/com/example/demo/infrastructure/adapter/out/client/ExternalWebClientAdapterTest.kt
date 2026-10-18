package com.example.demo.infrastructure.adapter.out.client

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

class ExternalWebClientAdapterTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var adapter: ExternalWebClientAdapter

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val webClient = WebClient.builder()
            .baseUrl(mockWebServer.url("/").toString())
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token")
            .build()

        adapter = ExternalWebClientAdapter(webClient)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `fetchEnrichmentData should call external API with auth and process high risk response`() {
        val jsonResponse = """
            {
                "referenceId": "ref-100",
                "category": "finance",
                "riskScore": 85,
                "verified": true,
                "tags": ["vip", "kyc"]
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(jsonResponse)
        )

        val result = adapter.fetchEnrichmentData("ref-100")

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/api/v1/enrichment/ref-100", recordedRequest.path)
        assertEquals("Bearer test-token", recordedRequest.getHeader(HttpHeaders.AUTHORIZATION))

        assertTrue(result.contains("category=FINANCE"))
        assertTrue(result.contains("riskScore=85"))
        assertTrue(result.contains("riskLevel=HIGH"))
        assertTrue(result.contains("verified=true"))
        assertTrue(result.contains("tags=[vip,kyc]"))
    }

    @Test
    fun `fetchEnrichmentData should process low risk response correctly`() {
        val jsonResponse = """
            {
                "referenceId": "ref-200",
                "category": "marketing",
                "riskScore": 15,
                "verified": false,
                "tags": []
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(jsonResponse)
        )

        val result = adapter.fetchEnrichmentData("ref-200")

        assertTrue(result.contains("category=MARKETING"))
        assertTrue(result.contains("riskScore=15"))
        assertTrue(result.contains("riskLevel=LOW"))
        assertTrue(result.contains("verified=false"))
        assertTrue(result.contains("tags=[none]"))
    }

    @Test
    fun `fetchEnrichmentData should gracefully fallback on external error response`() {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("Service Temporarily Unavailable")
        )

        val result = adapter.fetchEnrichmentData("ref-error")

        assertTrue(result.contains("status=UNAVAILABLE"))
        assertTrue(result.contains("referenceId=ref-error"))
    }
}
