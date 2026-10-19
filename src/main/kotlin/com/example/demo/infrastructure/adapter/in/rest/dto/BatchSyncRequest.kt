package com.example.demo.infrastructure.adapter.`in`.rest.dto

import com.example.demo.application.port.`in`.BatchSyncCommand
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Request payload for batch syncing resources from external API")
data class BatchSyncRequest(
    @field:Schema(description = "Total number of items to fetch if referenceIds is omitted (default: 100)", example = "100")
    val count: Int? = 100,
    @field:Schema(description = "Prefix for generated reference IDs (default: 'ref')", example = "ref")
    val referencePrefix: String? = "ref",
    @field:Schema(description = "Explicit list of reference IDs to fetch (overrides count if provided)")
    val referenceIds: List<String>? = null,
    @field:Schema(description = "Concurrency limit (parallelism) for outbound HTTP calls (default: 20)", example = "20")
    val parallelism: Int? = 20,
) {
    fun toCommand(): BatchSyncCommand =
        BatchSyncCommand(
            count = count ?: 100,
            referencePrefix = referencePrefix ?: "ref",
            referenceIds = referenceIds ?: emptyList(),
            parallelism = parallelism ?: 20,
        )
}
