package com.example.demo.infrastructure.adapter.out.persistence

import com.example.demo.domain.model.Resource
import com.example.demo.domain.model.ResourceId
import com.example.demo.domain.model.ResourceStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import java.util.UUID

@DataJpaTest
class ResourcePersistenceAdapterTest {
    @Autowired
    private lateinit var springDataRepository: SpringDataResourceRepository

    private lateinit var adapter: ResourcePersistenceAdapter
    private val mapper = ResourcePersistenceMapper()

    @BeforeEach
    fun setUp() {
        adapter = ResourcePersistenceAdapter(springDataRepository, mapper)
    }

    @Test
    fun `save and findById should persist and retrieve domain entity`() {
        val resource =
            Resource(
                name = "Test DB Resource",
                description = "Persistence test",
                status = ResourceStatus.ACTIVE,
                externalEnrichmentData = "enriched-data",
            )

        val saved = adapter.save(resource)
        val retrieved = adapter.findById(saved.id)

        assertNotNull(retrieved)
        assertEquals(saved.id, retrieved?.id)
        assertEquals("Test DB Resource", retrieved?.name)
        assertEquals("Persistence test", retrieved?.description)
        assertEquals(ResourceStatus.ACTIVE, retrieved?.status)
        assertEquals("enriched-data", retrieved?.externalEnrichmentData)
    }

    @Test
    fun `findAll should return all persisted resources`() {
        val r1 = Resource(name = "Res 1", description = "D1")
        val r2 = Resource(name = "Res 2", description = "D2")

        adapter.save(r1)
        adapter.save(r2)

        val all = adapter.findAll()
        assertEquals(2, all.size)
    }

    @Test
    fun `deleteById should return true when exists and remove record`() {
        val resource = Resource(name = "To Delete", description = "Delete me")
        val saved = adapter.save(resource)

        val deleted = adapter.deleteById(saved.id)
        assertTrue(deleted)

        val afterDelete = adapter.findById(saved.id)
        assertNull(afterDelete)
    }

    @Test
    fun `deleteById should return false when entity does not exist`() {
        val deleted = adapter.deleteById(ResourceId(UUID.randomUUID()))
        assertFalse(deleted)
    }

    @Test
    fun `saveAll should persist multiple entities in batch`() {
        val resources =
            (1..5).map {
                Resource(name = "Batch Res $it", description = "Desc $it")
            }

        val saved = adapter.saveAll(resources)
        assertEquals(5, saved.size)

        val all = adapter.findAll()
        assertTrue(all.size >= 5)
    }
}
