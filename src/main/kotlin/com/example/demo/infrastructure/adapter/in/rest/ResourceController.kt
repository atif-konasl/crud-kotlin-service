package com.example.demo.infrastructure.adapter.`in`.rest

import com.example.demo.application.port.`in`.CreateResourceUseCase
import com.example.demo.application.port.`in`.DeleteResourceUseCase
import com.example.demo.application.port.`in`.GetResourceUseCase
import com.example.demo.application.port.`in`.UpdateResourceUseCase
import com.example.demo.domain.model.ResourceId
import com.example.demo.infrastructure.adapter.`in`.rest.dto.CreateResourceRequest
import com.example.demo.infrastructure.adapter.`in`.rest.dto.ResourceResponse
import com.example.demo.infrastructure.adapter.`in`.rest.dto.UpdateResourceRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/resources")
class ResourceController(
    private val createResourceUseCase: CreateResourceUseCase,
    private val getResourceUseCase: GetResourceUseCase,
    private val updateResourceUseCase: UpdateResourceUseCase,
    private val deleteResourceUseCase: DeleteResourceUseCase
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateResourceRequest): ResourceResponse {
        val command = request.toCommand()
        val created = createResourceUseCase.createResource(command)
        return ResourceResponse.fromDomain(created)
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: UUID): ResourceResponse {
        val resource = getResourceUseCase.getResourceById(ResourceId(id))
        return ResourceResponse.fromDomain(resource)
    }

    @GetMapping
    fun getAll(): List<ResourceResponse> {
        return getResourceUseCase.getAllResources().map { ResourceResponse.fromDomain(it) }
    }

    @PatchMapping("/{id}")
    fun patch(
        @PathVariable id: UUID,
        @RequestBody request: UpdateResourceRequest
    ): ResourceResponse {
        val command = request.toCommand(id)
        val updated = updateResourceUseCase.updateResource(command)
        return ResourceResponse.fromDomain(updated)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        deleteResourceUseCase.deleteResource(ResourceId(id))
    }
}