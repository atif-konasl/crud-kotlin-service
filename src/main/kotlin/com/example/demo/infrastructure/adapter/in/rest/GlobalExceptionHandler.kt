package com.example.demo.infrastructure.adapter.`in`.rest

import com.example.demo.domain.exception.BusinessRuleValidationException
import com.example.demo.domain.exception.DomainException
import com.example.demo.domain.exception.InvalidResourceStateException
import com.example.demo.domain.exception.ResourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFound(ex: ResourceNotFoundException): ResponseEntity<ProblemDetail> {
        logger.warn("Resource not found: {}", ex.message)
        return buildProblemResponse(HttpStatus.NOT_FOUND, ex.message)
    }

    @ExceptionHandler(InvalidResourceStateException::class)
    fun handleInvalidResourceState(ex: InvalidResourceStateException): ResponseEntity<ProblemDetail> {
        logger.warn("Invalid resource state conflict: {}", ex.message)
        return buildProblemResponse(HttpStatus.CONFLICT, ex.message)
    }

    @ExceptionHandler(BusinessRuleValidationException::class)
    fun handleBusinessRuleValidation(ex: BusinessRuleValidationException): ResponseEntity<ProblemDetail> {
        logger.warn("Business rule validation failed: {}", ex.message)
        return buildProblemResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.message)
    }

    @ExceptionHandler(IllegalArgumentException::class, IllegalStateException::class)
    fun handleAssertionExceptions(ex: RuntimeException): ResponseEntity<ProblemDetail> {
        logger.warn("Request rejected due to illegal argument/state: {}", ex.message)
        return buildProblemResponse(HttpStatus.BAD_REQUEST, ex.message)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class, MethodArgumentTypeMismatchException::class)
    fun handleMalformedRequests(ex: Exception): ResponseEntity<ProblemDetail> {
        logger.warn("Malformed HTTP request: {}", ex.message)
        return buildProblemResponse(HttpStatus.BAD_REQUEST, "Malformed request payload or invalid parameter format.")
    }

    @ExceptionHandler(DomainException::class)
    fun handleGenericDomainException(ex: DomainException): ResponseEntity<ProblemDetail> {
        logger.warn("Domain exception caught by fallback handler: {}", ex.message)
        return buildProblemResponse(HttpStatus.BAD_REQUEST, ex.message)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnhandledException(ex: Exception): ResponseEntity<ProblemDetail> {
        logger.error("Unhandled internal server exception", ex)
        return buildProblemResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected internal error occurred. Please contact system administrator."
        )
    }

    private fun buildProblemResponse(
        status: HttpStatus,
        detail: String?
    ): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetail.forStatusAndDetail(status, detail ?: status.reasonPhrase)
        problemDetail.title = status.reasonPhrase
        problemDetail.setProperty("timestamp", Instant.now().toString())
        return ResponseEntity.status(status).body(problemDetail)
    }
}