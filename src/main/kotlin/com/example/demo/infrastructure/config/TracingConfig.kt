package com.example.demo.infrastructure.config

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TracingConfig(
    @param:Value("\${management.otlp.tracing.endpoint:http://localhost:4318/v1/traces}")
    private val otlpEndpoint: String,
) {
    @Bean
    @ConditionalOnProperty(prefix = "management.otlp.tracing", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun otlpHttpSpanExporter(): OtlpHttpSpanExporter =
        OtlpHttpSpanExporter
            .builder()
            .setEndpoint(otlpEndpoint)
            .build()
}
