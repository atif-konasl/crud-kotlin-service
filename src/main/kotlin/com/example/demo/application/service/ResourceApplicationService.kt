package com.example.demo.application.service

import com.example.demo.application.port.`in`.BatchSyncCommand
import com.example.demo.application.port.`in`.BatchSyncResourcesUseCase
import com.example.demo.application.port.`in`.BatchSyncResult
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
import com.example.demo.domain.model.ResourceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ResourceApplicationService(
    private val repositoryPort: ResourceRepositoryPort,
    private val externalClientPort: ExternalClientPort,
) : CreateResourceUseCase,
    GetResourceUseCase,
    UpdateResourceUseCase,
    DeleteResourceUseCase,
    BatchSyncResourcesUseCase {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun createResource(command: CreateResourceCommand): Resource {
        logger.info("Creating new resource: name='{}', referenceId='{}'", command.name, command.externalReferenceId)

        // 1. Fetch external enrichment data via outbound client port
        val enrichmentData = externalClientPort.fetchEnrichmentData(command.externalReferenceId)

        // 2. Instantiate and validate domain entity (enforces domain invariants)
        val newResource =
            Resource(
                name = command.name,
                description = command.description,
                externalEnrichmentData = enrichmentData,
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

    @Transactional
    override fun updateResource(command: UpdateResourceCommand): Resource {
        logger.info("Updating resource ID: {}", command.id.value)
        val existing = getResourceById(command.id)
        val patched = existing.applyPatch(command.name, command.description)
        return repositoryPort.save(patched)
    }

    @Transactional
    override fun deleteResource(id: ResourceId) {
        logger.info("Deleting resource ID: {}", id.value)
        if (!repositoryPort.deleteById(id)) {
            throw ResourceNotFoundException(id.value)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override suspend fun batchSync(command: BatchSyncCommand): BatchSyncResult =
        coroutineScope {
            val startTime = System.currentTimeMillis()
            val targetIds =
                command.referenceIds.ifEmpty {
                    (1..command.count).map { "${command.referencePrefix}-$it" }
                }

            logger.info(
                "Starting batch sync for {} external API calls with parallelism limit {}",
                targetIds.size,
                command.parallelism,
            )

            // Bulkhead pattern: throttle concurrent outbound calls
            val limitedDispatcher = Dispatchers.IO.limitedParallelism(command.parallelism)

            val deferredResources =
                targetIds.map { refId ->
                    async(limitedDispatcher) {
                        try {
                            val enrichment = externalClientPort.fetchEnrichmentDataAsync(refId)
                            Resource(
                                name = "Resource for $refId",
                                description = "Batch synced resource for external reference $refId",
                                status = ResourceStatus.ACTIVE,
                                externalEnrichmentData = enrichment,
                            )
                        } catch (ex: Exception) {
                            logger.error("Failed to fetch enrichment for refId='{}'", refId, ex)
                            null
                        }
                    }
                }

            val resourcesToSave = deferredResources.awaitAll().filterNotNull()

            logger.info("External calls completed. Saving {} entities to database...", resourcesToSave.size)
            val savedResources = repositoryPort.saveAll(resourcesToSave)

            val durationMs = System.currentTimeMillis() - startTime
            logger.info("Batch sync finished in {}ms. Saved {} entities.", durationMs, savedResources.size)

            BatchSyncResult(
                totalRequested = targetIds.size,
                totalPersisted = savedResources.size,
                durationMs = durationMs,
                resources = savedResources,
            )
        }
}
