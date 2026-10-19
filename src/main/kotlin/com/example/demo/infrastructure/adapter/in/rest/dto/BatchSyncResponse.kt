package com.example.demo.infrastructure.adapter.`in`.rest.dto

import com.example.demo.application.port.`in`.BatchSyncResult
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Response summary for batch sync operation")
data class BatchSyncResponse(
    @field:Schema(description = "Total number of external API calls requested", example = "100")
    val totalRequested: Int,
    @field:Schema(description = "Total number of resources successfully saved into database", example = "100")
    val totalPersisted: Int,
    @field:Schema(description = "Time taken for the entire batch operation in milliseconds", example = "350")
    val durationMs: Long,
    @field:Schema(description = "List of persisted resources")
    val items: List<ResourceResponse>,
) {
    companion object {
        fun fromResult(result: BatchSyncResult): BatchSyncResponse =
            BatchSyncResponse(
                totalRequested = result.totalRequested,
                totalPersisted = result.totalPersisted,
                durationMs = result.durationMs,
                items = result.resources.map { ResourceResponse.fromDomain(it) },
            )
    }
}
