package com.example.demo.domain.exception

/**
 * Thrown when domain-level invariants or business constraints are violated.
 */
class BusinessRuleValidationException(
    message: String,
) : DomainException(message)
