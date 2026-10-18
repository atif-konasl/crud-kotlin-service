package com.example.demo.infrastructure.adapter.out.persistence

import com.example.demo.domain.model.Resource
import com.example.demo.domain.model.ResourceId
import org.springframework.stereotype.Component

@Component
class ResourcePersistenceMapper {

    fun toJpaEntity(domain: Resource): ResourceJpaEntity = ResourceJpaEntity(
        id = domain.id.value,
        name = domain.name,
        description = domain.description,
        status = domain.status,
        externalEnrichmentData = domain.externalEnrichmentData,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt
    )

    fun toDomain(entity: ResourceJpaEntity): Resource = Resource(
        id = ResourceId(entity.id),
        name = entity.name,
        description = entity.description,
        status = entity.status,
        externalEnrichmentData = entity.externalEnrichmentData,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )
}
