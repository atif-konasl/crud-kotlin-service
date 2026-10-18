package com.example.demo.infrastructure.adapter.`in`.rest.dto

import com.example.demo.domain.model.Resource
import com.example.demo.domain.model.ResourceStatus
import java.time.Instant
import java.util.UUID

data class ResourceResponse(
    val id: UUID,
    val name: String,
    val description: String,
    val status: ResourceStatus,
    val externalEnrichmentData: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun fromDomain(resource: Resource): ResourceResponse =
            ResourceResponse(
                id = resource.id.value,
                name = resource.name,
                description = resource.description,
                status = resource.status,
                externalEnrichmentData = resource.externalEnrichmentData,
                createdAt = resource.createdAt,
                updatedAt = resource.updatedAt,
            )
    }
}
