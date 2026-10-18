package com.example.demo.infrastructure.adapter.out.client.config

import io.netty.channel.ChannelOption
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Configuration
class WebClientConfig(
    @param:Value("\${external.client.base-url:http://localhost:8081}")
    private val baseUrl: String,

    @param:Value("\${external.client.auth-token:default-auth-token}")
    private val authToken: String,

    @param:Value("\${external.client.connect-timeout-ms:3000}")
    private val connectTimeoutMs: Int,

    @param:Value("\${external.client.read-timeout-ms:5000}")
    private val readTimeoutMs: Long
) {

    @Bean
    fun externalApiWebClient(webClientBuilder: WebClient.Builder): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
            .responseTimeout(Duration.ofMillis(readTimeoutMs))

        return webClientBuilder
            .baseUrl(baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $authToken")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }
}
