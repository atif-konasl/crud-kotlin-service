package com.example.demo.infrastructure.adapter.`in`.rest.dto

import com.example.demo.application.port.`in`.UpdateResourceCommand
import com.example.demo.domain.model.ResourceId
import java.util.UUID

data class UpdateResourceRequest(
    val name: String? = null,
    val description: String? = null,
) {
    fun toCommand(id: UUID): UpdateResourceCommand =
        UpdateResourceCommand(
            id = ResourceId(id),
            name = name,
            description = description,
        )
}
