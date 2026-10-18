package com.example.demo.domain.model

import com.example.demo.domain.exception.InvalidResourceStateException
import java.time.Instant
import java.util.UUID

@JvmInline
value class ResourceId(val value: UUID = UUID.randomUUID())

enum class ResourceStatus { DRAFT, ACTIVE, ARCHIVED }

data class Resource (
    val id: ResourceId = ResourceId(),
    val name: String,
    val description: String,
    val status: ResourceStatus = ResourceStatus.DRAFT,
    val externalEnrichmentData: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    init {
        require(name.isNotBlank()) { "Resource name cannot be blank." }
    }

    fun activate(): Resource {
        if (status == ResourceStatus.ACTIVE) {
            throw InvalidResourceStateException("Cannot activate an archived resource.")
        }
        return this.copy(status = ResourceStatus.ACTIVE, updatedAt = Instant.now())
    }

    fun applyPatch(newName: String?, newDescription: String?): Resource {
        val updated = this.copy(
            name = newName ?: this.name,
            description = newDescription ?: this.description,
            updatedAt = Instant.now()
        )
        require(updated.name.isNotBlank()) { "Resource name cannot be blank." }
        return updated
    }
}