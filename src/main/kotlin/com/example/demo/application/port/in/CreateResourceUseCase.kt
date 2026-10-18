package com.example.demo.application.port.`in`

import com.example.demo.domain.model.Resource

data class CreateResourceCommand(
    val name: String,
    val description: String,
    val externalReferenceId: String,
)

interface CreateResourceUseCase {
    fun createResource(command: CreateResourceCommand): Resource
}
