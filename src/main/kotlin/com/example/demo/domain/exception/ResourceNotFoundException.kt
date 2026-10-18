package com.example.demo.domain.exception

import java.util.UUID

/**
 * Thrown when a requested resource does not exist in the domain repository.
 */
class ResourceNotFoundException(
    message: String,
) : DomainException(message) {
    constructor(id: UUID) : this("Resource with id '$id' was not found.")
}
