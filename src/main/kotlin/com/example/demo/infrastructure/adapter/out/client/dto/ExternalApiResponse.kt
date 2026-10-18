package com.example.demo.infrastructure.adapter.out.client.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExternalApiResponse(
    val referenceId: String,
    val category: String? = null,
    val riskScore: Int? = null,
    val verified: Boolean? = null,
    val tags: List<String> = emptyList(),
)
