package com.example.demo.domain.exception

/**
 * Thrown when an illegal state transition is attempted on a resource.
 */
class InvalidResourceStateException(
    message: String
) : DomainException(message)
