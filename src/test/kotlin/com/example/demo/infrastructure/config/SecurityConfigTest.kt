package com.example.demo.infrastructure.config

import com.example.demo.application.port.`in`.CreateResourceUseCase
import com.example.demo.application.port.`in`.DeleteResourceUseCase
import com.example.demo.application.port.`in`.GetResourceUseCase
import com.example.demo.application.port.`in`.UpdateResourceUseCase
import com.example.demo.infrastructure.adapter.`in`.rest.ResourceController
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(ResourceController::class)
@Import(SecurityConfig::class)
class SecurityConfigTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var createResourceUseCase: CreateResourceUseCase

    @MockkBean
    private lateinit var getResourceUseCase: GetResourceUseCase

    @MockkBean
    private lateinit var updateResourceUseCase: UpdateResourceUseCase

    @MockkBean
    private lateinit var deleteResourceUseCase: DeleteResourceUseCase

    @Test
    fun `protected API endpoints should reject unauthenticated requests with 401 Unauthorized`() {
        mockMvc
            .perform(get("/api/v1/resources"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `protected API endpoints should accept authenticated requests with valid JWT`() {
        every { getResourceUseCase.getAllResources() } returns emptyList()

        mockMvc
            .perform(get("/api/v1/resources").with(jwt()))
            .andExpect(status().isOk)
    }
}
