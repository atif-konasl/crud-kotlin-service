package com.example.demo.infrastructure.adapter.`in`.rest

import com.example.demo.application.port.`in`.CreateResourceUseCase
import com.example.demo.application.port.`in`.DeleteResourceUseCase
import com.example.demo.application.port.`in`.GetResourceUseCase
import com.example.demo.application.port.`in`.UpdateResourceUseCase
import com.example.demo.domain.model.Resource
import com.example.demo.domain.model.ResourceId
import com.example.demo.domain.model.ResourceStatus
import com.example.demo.infrastructure.adapter.`in`.rest.dto.CreateResourceRequest
import com.example.demo.infrastructure.adapter.`in`.rest.dto.UpdateResourceRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
@WebMvcTest(ResourceController::class)
@AutoConfigureMockMvc(addFilters = false)
class ResourceControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var createResourceUseCase: CreateResourceUseCase

    @MockkBean
    private lateinit var getResourceUseCase: GetResourceUseCase

    @MockkBean
    private lateinit var updateResourceUseCase: UpdateResourceUseCase

    @MockkBean
    private lateinit var deleteResourceUseCase: DeleteResourceUseCase

    @Test
    fun `POST should create resource and return 201 Created`() {
        val request = CreateResourceRequest(
            name = "Test Resource",
            description = "Description",
            externalReferenceId = "ref-123"
        )
        val created = Resource(
            name = request.name,
            description = request.description,
            externalEnrichmentData = "enriched"
        )
        every { createResourceUseCase.createResource(any()) } returns created

        mockMvc.perform(
            post("/api/v1/resources")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(created.id.value.toString()))
            .andExpect(jsonPath("$.name").value("Test Resource"))
            .andExpect(jsonPath("$.status").value("DRAFT"))

        verify(exactly = 1) { createResourceUseCase.createResource(any()) }
    }

    @Test
    fun `GET by id should return 200 OK with resource`() {
        val id = UUID.randomUUID()
        val resource = Resource(
            id = ResourceId(id),
            name = "Found Resource",
            description = "Desc",
            status = ResourceStatus.ACTIVE
        )
        every { getResourceUseCase.getResourceById(ResourceId(id)) } returns resource

        mockMvc.perform(get("/api/v1/resources/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.name").value("Found Resource"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))

        verify(exactly = 1) { getResourceUseCase.getResourceById(ResourceId(id)) }
    }

    @Test
    fun `GET all should return 200 OK with list of resources`() {
        val list = listOf(
            Resource(name = "Resource 1", description = "Desc 1"),
            Resource(name = "Resource 2", description = "Desc 2")
        )
        every { getResourceUseCase.getAllResources() } returns list

        mockMvc.perform(get("/api/v1/resources"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Resource 1"))
            .andExpect(jsonPath("$[1].name").value("Resource 2"))

        verify(exactly = 1) { getResourceUseCase.getAllResources() }
    }

    @Test
    fun `PATCH should update resource and return 200 OK`() {
        val id = UUID.randomUUID()
        val request = UpdateResourceRequest(name = "Updated Name", description = null)
        val updated = Resource(
            id = ResourceId(id),
            name = "Updated Name",
            description = "Existing Desc"
        )
        every { updateResourceUseCase.updateResource(any()) } returns updated

        mockMvc.perform(
            patch("/api/v1/resources/$id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.name").value("Updated Name"))

        verify(exactly = 1) { updateResourceUseCase.updateResource(any()) }
    }

    @Test
    fun `DELETE should return 204 No Content`() {
        val id = UUID.randomUUID()
        justRun { deleteResourceUseCase.deleteResource(ResourceId(id)) }

        mockMvc.perform(delete("/api/v1/resources/$id"))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { deleteResourceUseCase.deleteResource(ResourceId(id)) }
    }
}
