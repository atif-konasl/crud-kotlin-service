package com.example.demo.application.port.`in`

import com.example.demo.domain.model.ResourceId

interface DeleteResourceUseCase {
    fun deleteResource(id: ResourceId)
}
