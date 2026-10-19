package com.example.demo.infrastructure.adapter.out.client

import com.example.demo.application.port.out.ExternalClientPort
import com.example.demo.infrastructure.adapter.out.client.dto.ExternalApiResponse
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Duration

@Component
class ExternalWebClientAdapter(
    private val externalApiWebClient: WebClient,
) : ExternalClientPort {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun fetchEnrichmentData(referenceId: String): String {
        logger.info("Calling external API to fetch enrichment data for referenceId='{}'", referenceId)

        return try {
            val response =
                externalApiWebClient
                    .get()
                    .uri("/api/v1/enrichment/{referenceId}", referenceId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError) { clientResponse ->
                        logger.error("External API returned error status: {}", clientResponse.statusCode())
                        clientResponse.createException()
                    }.bodyToMono(ExternalApiResponse::class.java)
                    .timeout(Duration.ofSeconds(5))
                    .block()

            processEnrichmentResponse(response, referenceId)
        } catch (ex: WebClientResponseException) {
            logger.error("External client HTTP error: status={}, body={}", ex.statusCode, ex.responseBodyAsString, ex)
            "source=external-api;referenceId=$referenceId;status=UNAVAILABLE;details=${ex.statusCode}"
        } catch (ex: Exception) {
            logger.error("Failed to fetch enrichment data from external client for referenceId='{}'", referenceId, ex)
            "source=external-api;referenceId=$referenceId;status=FAILED;details=${ex.message}"
        }
    }

    override suspend fun fetchEnrichmentDataAsync(referenceId: String): String {
        val threadBefore = Thread.currentThread().name
        logger.info(
            "[COROUTINE-SUSPEND] refId='{}' preparing non-blocking HTTP call on thread=[{}]",
            referenceId,
            threadBefore,
        )

        return try {
            val response =
                externalApiWebClient
                    .get()
                    .uri("/api/v1/enrichment/{referenceId}", referenceId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError) { clientResponse ->
                        logger.error("External API returned error status: {}", clientResponse.statusCode())
                        clientResponse.createException()
                    }.bodyToMono(ExternalApiResponse::class.java)
                    .timeout(Duration.ofSeconds(5))
                    .awaitSingleOrNull()

            val threadAfter = Thread.currentThread().name
            logger.info(
                "[COROUTINE-RESUME] refId='{}' resumed after I/O on thread=[{}] (threadSwitched={})",
                referenceId,
                threadAfter,
                threadBefore != threadAfter,
            )

            processEnrichmentResponse(response, referenceId)
        } catch (ex: WebClientResponseException) {
            val threadAfter = Thread.currentThread().name
            logger.error(
                "[COROUTINE-RESUME-HTTP-ERROR] refId='{}' resumed on thread=[{}]: status={}, body={}",
                referenceId,
                threadAfter,
                ex.statusCode,
                ex.responseBodyAsString,
            )
            "source=external-api;referenceId=$referenceId;status=UNAVAILABLE;details=${ex.statusCode}"
        } catch (ex: Exception) {
            val threadAfter = Thread.currentThread().name
            logger.error(
                "[COROUTINE-RESUME-EXCEPTION] refId='{}' resumed on thread=[{}]: {}",
                referenceId,
                threadAfter,
                ex.message,
            )
            "source=external-api;referenceId=$referenceId;status=FAILED;details=${ex.message}"
        }
    }

    /**
     * Performs business operations and normalization on the returned third-party data.
     */
    private fun processEnrichmentResponse(
        response: ExternalApiResponse?,
        referenceId: String,
    ): String {
        if (response == null) {
            return "source=external-api;referenceId=$referenceId;status=EMPTY"
        }

        val riskLevel =
            when {
                (response.riskScore ?: 0) >= 80 -> "HIGH"
                (response.riskScore ?: 0) >= 40 -> "MEDIUM"
                else -> "LOW"
            }

        val normalizedCategory = response.category?.uppercase() ?: "GENERAL"
        val isVerified = response.verified ?: false
        val formattedTags = if (response.tags.isNotEmpty()) response.tags.joinToString(",") else "none"

        return "source=external-api;ref=$referenceId;category=$normalizedCategory;riskScore=${response.riskScore ?: 0};riskLevel=$riskLevel;verified=$isVerified;tags=[$formattedTags]"
    }
}
