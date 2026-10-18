package com.example.demo.application.port.`in`

import com.example.demo.domain.model.Resource
import com.example.demo.domain.model.ResourceId

interface GetResourceUseCase {
    fun getResourceById(id: ResourceId): Resource

    fun getAllResources(): List<Resource>
}
