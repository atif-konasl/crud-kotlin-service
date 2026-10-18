package com.example.demo.infrastructure.adapter.out.persistence

import com.example.demo.application.port.out.ResourceRepositoryPort
import com.example.demo.domain.model.Resource
import com.example.demo.domain.model.ResourceId
import org.springframework.stereotype.Component

@Component
class ResourcePersistenceAdapter(
    private val springDataRepository: SpringDataResourceRepository,
    private val mapper: ResourcePersistenceMapper
) : ResourceRepositoryPort {

    override fun save(resource: Resource): Resource {
        val entity = mapper.toJpaEntity(resource)
        val saved = springDataRepository.save(entity)
        return mapper.toDomain(saved)
    }

    override fun findById(id: ResourceId): Resource? {
        return springDataRepository.findById(id.value).map { mapper.toDomain(it) }.orElse(null)
    }

    override fun findAll(): List<Resource> {
        return springDataRepository.findAll().map { mapper.toDomain(it) }
    }

    override fun deleteById(id: ResourceId): Boolean {
        return if (springDataRepository.existsById(id.value)) {
            springDataRepository.deleteById(id.value)
            true
        } else {
            false
        }
    }
}
