package com.example.demo.infrastructure.adapter.`in`.rest

import com.example.demo.domain.exception.BusinessRuleValidationException
import com.example.demo.domain.exception.DomainException
import com.example.demo.domain.exception.InvalidResourceStateException
import com.example.demo.domain.exception.ResourceNotFoundException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
private class ExceptionTestController {

    @GetMapping("/test/not-found")
    fun notFound(): Nothing = throw ResourceNotFoundException(UUID.fromString("00000000-0000-0000-0000-000000000001"))

    @GetMapping("/test/conflict")
    fun conflict(): Nothing = throw InvalidResourceStateException("Resource already active.")

    @GetMapping("/test/validation")
    fun validation(): Nothing = throw BusinessRuleValidationException("Name must be unique.")

    @GetMapping("/test/illegal-argument")
    fun illegalArgument(): Nothing = throw IllegalArgumentException("Invalid argument provided.")

    @GetMapping("/test/generic-domain")
    fun genericDomain(): Nothing = throw object : DomainException("Custom domain error") {}

    @GetMapping("/test/unhandled")
    fun unhandled(): Nothing = throw RuntimeException("Database connection timeout")
}

class GlobalExceptionHandlerTest {

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(ExceptionTestController())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun `should map ResourceNotFoundException to 404 NOT_FOUND`() {
        mockMvc.perform(get("/test/not-found").accept(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.title").value("Not Found"))
            .andExpect(jsonPath("$.detail").value("Resource with id '00000000-0000-0000-0000-000000000001' was not found."))
            .andExpect(jsonPath("$.timestamp").exists())
    }

    @Test
    fun `should map InvalidResourceStateException to 409 CONFLICT`() {
        mockMvc.perform(get("/test/conflict").accept(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.title").value("Conflict"))
            .andExpect(jsonPath("$.detail").value("Resource already active."))
    }

    @Test
    fun `should map BusinessRuleValidationException to 422 UNPROCESSABLE_ENTITY`() {
        mockMvc.perform(get("/test/validation").accept(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.status").value(422))
            .andExpect(jsonPath("$.title").value("Unprocessable Entity"))
            .andExpect(jsonPath("$.detail").value("Name must be unique."))
    }

    @Test
    fun `should map IllegalArgumentException to 400 BAD_REQUEST`() {
        mockMvc.perform(get("/test/illegal-argument").accept(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.title").value("Bad Request"))
            .andExpect(jsonPath("$.detail").value("Invalid argument provided."))
    }

    @Test
    fun `should map unhandled DomainException to 400 BAD_REQUEST`() {
        mockMvc.perform(get("/test/generic-domain").accept(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Custom domain error"))
    }

    @Test
    fun `should map unexpected Exception to 500 INTERNAL_SERVER_ERROR with sanitized message`() {
        mockMvc.perform(get("/test/unhandled").accept(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.title").value("Internal Server Error"))
            .andExpect(jsonPath("$.detail").value("An unexpected internal error occurred. Please contact system administrator."))
    }
}
