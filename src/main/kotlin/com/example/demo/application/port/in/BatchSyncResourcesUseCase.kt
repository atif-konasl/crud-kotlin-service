package com.example.demo.application.port.`in`

import com.example.demo.domain.model.Resource

data class BatchSyncCommand(
    val count: Int = 100,
    val referencePrefix: String = "ref",
    val referenceIds: List<String> = emptyList(),
    val parallelism: Int = 20,
)

data class BatchSyncResult(
    val totalRequested: Int,
    val totalPersisted: Int,
    val durationMs: Long,
    val resources: List<Resource>,
)

interface BatchSyncResourcesUseCase {
    suspend fun batchSync(command: BatchSyncCommand): BatchSyncResult
}
