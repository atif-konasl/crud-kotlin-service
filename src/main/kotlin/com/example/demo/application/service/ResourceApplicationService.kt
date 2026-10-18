package com.example.demo.application.service

import com.example.demo.application.port.`in`.CreateResourceCommand
import com.example.demo.application.port.`in`.CreateResourceUseCase
import com.example.demo.application.port.`in`.DeleteResourceUseCase
import com.example.demo.application.port.`in`.GetResourceUseCase
import com.example.demo.application.port.`in`.UpdateResourceCommand
import com.example.demo.application.port.`in`.UpdateResourceUseCase
import com.example.demo.application.port.out.ExternalClientPort
import com.example.demo.application.port.out.ResourceRepositoryPort
import com.example.demo.domain.exception.ResourceNotFoundException
import com.example.demo.domain.model.Resource
import com.example.demo.domain.model.ResourceId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ResourceApplicationService(
    private val repositoryPort: ResourceRepositoryPort,
    private val externalClientPort: ExternalClientPort
) : CreateResourceUseCase, GetResourceUseCase, UpdateResourceUseCase, DeleteResourceUseCase {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun createResource(command: CreateResourceCommand): Resource {
        logger.info("Creating new resource: name='{}', referenceId='{}'", command.name, command.externalReferenceId)
        
        // 1. Fetch external enrichment data via outbound client port
        val enrichmentData = externalClientPort.fetchEnrichmentData(command.externalReferenceId)

        // 2. Instantiate and validate domain entity (enforces domain invariants)
        val newResource = Resource(
            name = command.name,
            description = command.description,
            externalEnrichmentData = enrichmentData
        )

        // 3. Persist via outbound repository port
        return repositoryPort.save(newResource)
    }

    @Transactional(readOnly = true)
    override fun getResourceById(id: ResourceId): Resource {
        logger.debug("Retrieving resource by ID: {}", id.value)
        return repositoryPort.findById(id)
            ?: throw ResourceNotFoundException(id.value)
    }

    @Transactional(readOnly = true)
    override fun getAllResources(): List<Resource> {
        logger.debug("Retrieving all resources")
        return repositoryPort.findAll()
    }

    override fun updateResource(command: UpdateResourceCommand): Resource {
        logger.info("Updating resource ID: {}", command.id.value)
        val existing = getResourceById(command.id)
        val patched = existing.applyPatch(command.name, command.description)
        return repositoryPort.save(patched)
    }

    override fun deleteResource(id: ResourceId) {
        logger.info("Deleting resource ID: {}", id.value)
        if (!repositoryPort.deleteById(id)) {
            throw ResourceNotFoundException(id.value)
        }
    }
}