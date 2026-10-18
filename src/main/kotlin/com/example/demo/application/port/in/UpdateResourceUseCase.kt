package com.example.demo.application.port.`in`

import com.example.demo.domain.model.Resource
import com.example.demo.domain.model.ResourceId

data class UpdateResourceCommand(
    val id: ResourceId,
    val name: String? = null,
    val description: String? = null,
)

interface UpdateResourceUseCase {
    fun updateResource(command: UpdateResourceCommand): Resource
}
