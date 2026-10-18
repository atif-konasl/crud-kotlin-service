package com.example.demo.application.port.out

import com.example.demo.domain.model.Resource
import com.example.demo.domain.model.ResourceId

interface ResourceRepositoryPort {
    fun save(resource: Resource): Resource
    fun findById(id: ResourceId): Resource?
    fun findAll(): List<Resource>
    fun deleteById(id: ResourceId): Boolean
}

interface ExternalClientPort {
    fun fetchEnrichmentData(referenceId: String): String
}