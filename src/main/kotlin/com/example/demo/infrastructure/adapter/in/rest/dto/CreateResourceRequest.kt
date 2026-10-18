package com.example.demo.infrastructure.adapter.`in`.rest.dto

import com.example.demo.application.port.`in`.CreateResourceCommand

data class CreateResourceRequest(
    val name: String,
    val description: String,
    val externalReferenceId: String,
) {
    fun toCommand(): CreateResourceCommand =
        CreateResourceCommand(
            name = name,
            description = description,
            externalReferenceId = externalReferenceId,
        )
}
