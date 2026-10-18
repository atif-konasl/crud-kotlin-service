package com.example.demo.domain.exception

/**
 * Base abstract exception for all domain-level business errors.
 * Completely decoupled from HTTP status codes and Spring framework classes.
 */
abstract class DomainException(
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause)
