package com.example.demo.application.service

import com.example.demo.application.port.`in`.CreateResourceCommand
import com.example.demo.application.port.`in`.UpdateResourceCommand
import com.example.demo.application.port.out.ExternalClientPort
import com.example.demo.application.port.out.ResourceRepositoryPort
import com.example.demo.domain.exception.ResourceNotFoundException
import com.example.demo.domain.model.Resource
import com.example.demo.domain.model.ResourceId
import com.example.demo.domain.model.ResourceStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ResourceApplicationServiceTest {
    private val repositoryPort: ResourceRepositoryPort = mockk()
    private val externalClientPort: ExternalClientPort = mockk()
    private lateinit var service: ResourceApplicationService

    @BeforeEach
    fun setUp() {
        service = ResourceApplicationService(repositoryPort, externalClientPort)
    }

    @Test
    fun `createResource should fetch external enrichment data and persist resource`() {
        val command =
            CreateResourceCommand(
                name = "Test Resource",
                description = "Test Description",
                externalReferenceId = "ext-ref-123",
            )
        val enrichmentData = "{\"riskScore\": 98}"
        every { externalClientPort.fetchEnrichmentData("ext-ref-123") } returns enrichmentData
        every { repositoryPort.save(any()) } answers { firstArg() }

        val result = service.createResource(command)

        assertNotNull(result.id)
        assertEquals("Test Resource", result.name)
        assertEquals("Test Description", result.description)
        assertEquals(ResourceStatus.DRAFT, result.status)
        assertEquals(enrichmentData, result.externalEnrichmentData)
        verify(exactly = 1) { externalClientPort.fetchEnrichmentData("ext-ref-123") }
        verify(exactly = 1) { repositoryPort.save(any()) }
    }

    @Test
    fun `getResourceById should return resource when found`() {
        val resourceId = ResourceId()
        val expected = Resource(id = resourceId, name = "Sample", description = "Desc")
        every { repositoryPort.findById(resourceId) } returns expected

        val result = service.getResourceById(resourceId)

        assertEquals(expected, result)
        verify(exactly = 1) { repositoryPort.findById(resourceId) }
    }

    @Test
    fun `getResourceById should throw ResourceNotFoundException when not found`() {
        val resourceId = ResourceId()
        every { repositoryPort.findById(resourceId) } returns null

        assertThrows<ResourceNotFoundException> {
            service.getResourceById(resourceId)
        }
    }

    @Test
    fun `getAllResources should return all items from repository`() {
        val resources =
            listOf(
                Resource(name = "Res 1", description = "Desc 1"),
                Resource(name = "Res 2", description = "Desc 2"),
            )
        every { repositoryPort.findAll() } returns resources

        val result = service.getAllResources()

        assertEquals(2, result.size)
        verify(exactly = 1) { repositoryPort.findAll() }
    }

    @Test
    fun `updateResource should patch fields and save updated entity`() {
        val resourceId = ResourceId()
        val existing = Resource(id = resourceId, name = "Old Name", description = "Old Desc")
        val command = UpdateResourceCommand(id = resourceId, name = "New Name", description = null)

        every { repositoryPort.findById(resourceId) } returns existing
        every { repositoryPort.save(any()) } answers { firstArg() }

        val result = service.updateResource(command)

        assertEquals("New Name", result.name)
        assertEquals("Old Desc", result.description) // preserved
        verify(exactly = 1) { repositoryPort.save(match { it.name == "New Name" && it.description == "Old Desc" }) }
    }

    @Test
    fun `deleteResource should succeed when repository returns true`() {
        val resourceId = ResourceId()
        every { repositoryPort.deleteById(resourceId) } returns true

        service.deleteResource(resourceId)

        verify(exactly = 1) { repositoryPort.deleteById(resourceId) }
    }

    @Test
    fun `deleteResource should throw ResourceNotFoundException when repository returns false`() {
        val resourceId = ResourceId()
        every { repositoryPort.deleteById(resourceId) } returns false

        assertThrows<ResourceNotFoundException> {
            service.deleteResource(resourceId)
        }
    }

    @Test
    fun `batchSync should call external API concurrently and save all entities to repository`() =
        kotlinx.coroutines.test.runTest {
            val command =
                com.example.demo.application.port.`in`
                    .BatchSyncCommand(count = 5, referencePrefix = "item", parallelism = 2)
            io.mockk.coEvery { externalClientPort.fetchEnrichmentDataAsync(any()) } answers {
                "source=external-api;ref=${firstArg<String>()};status=OK"
            }
            every { repositoryPort.saveAll(any()) } answers { firstArg() }

            val result = service.batchSync(command)

            assertEquals(5, result.totalRequested)
            assertEquals(5, result.totalPersisted)
            assertEquals(5, result.resources.size)
            io.mockk.coVerify(exactly = 5) { externalClientPort.fetchEnrichmentDataAsync(any()) }
            verify(exactly = 1) { repositoryPort.saveAll(match { it.size == 5 }) }
        }
}
