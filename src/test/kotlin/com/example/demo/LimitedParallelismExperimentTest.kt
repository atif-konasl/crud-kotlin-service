package com.example.demo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.junit.jupiter.api.Test
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import java.time.Duration

class LimitedParallelismExperimentTest {
    private fun createWebClient(): WebClient {
        val connectionProvider =
            ConnectionProvider
                .builder("test-pool")
                .maxConnections(200)
                .pendingAcquireMaxCount(1000)
                .pendingAcquireTimeout(Duration.ofSeconds(45))
                .build()

        val httpClient = HttpClient.create(connectionProvider)
        return WebClient
            .builder()
            .baseUrl("http://localhost:8081")
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `compare limitedParallelism(10) vs Semaphore(10)`() {
        val webClient = createWebClient()
        val totalRequests = 100
        val parallelism = 10

        // Warmup connection pool
        runBlocking {
            webClient
                .get()
                .uri("/api/v1/enrichment/warmup")
                .retrieve()
                .bodyToMono(String::class.java)
                .awaitSingleOrNull()
        }

        // --- TEST 1: limitedParallelism(10) ---
        val limitedDispatcher = Dispatchers.IO.limitedParallelism(parallelism)
        val startLimited = System.currentTimeMillis()
        runBlocking {
            val deferredList =
                (1..totalRequests).map { i ->
                    async(limitedDispatcher) {
                        webClient
                            .get()
                            .uri("/api/v1/enrichment/lp-$i")
                            .retrieve()
                            .bodyToMono(String::class.java)
                            .awaitSingleOrNull()
                    }
                }
            deferredList.awaitAll()
        }
        val durationLimited = System.currentTimeMillis() - startLimited

        // --- TEST 2: Semaphore(10) ---
        val semaphore = Semaphore(parallelism)
        val startSemaphore = System.currentTimeMillis()
        runBlocking {
            val deferredList =
                (1..totalRequests).map { i ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            webClient
                                .get()
                                .uri("/api/v1/enrichment/sem-$i")
                                .retrieve()
                                .bodyToMono(String::class.java)
                                .awaitSingleOrNull()
                        }
                    }
                }
            deferredList.awaitAll()
        }
        val durationSemaphore = System.currentTimeMillis() - startSemaphore

        println("=================================================================")
        println("EMPIRICAL COMPARISON (Netty 200 pool + WireMock 200 threads):")
        println("1. limitedParallelism(10) Duration: ${durationLimited}ms")
        println("2. Semaphore(10)          Duration: ${durationSemaphore}ms")
        println("=================================================================")
    }
}
