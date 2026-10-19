package com.example.demo.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverterContextImpl
import io.swagger.v3.core.jackson.ModelResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

data class TestNamingDto(
    val externalReferenceId: String,
    val userRiskScore: Int,
)

class OpenApiNamingStrategyTest {
    @Test
    fun `should generate snake_case properties in schema when ObjectMapper has SNAKE_CASE strategy`() {
        val objectMapper =
            ObjectMapper().apply {
                propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
                registerKotlinModule()
            }

        val resolver = ModelResolver(objectMapper)
        val context = ModelConverterContextImpl(listOf(resolver))
        val resolved = resolver.resolve(AnnotatedType(TestNamingDto::class.java), context, null)

        assertThat(resolved).isNotNull
        assertThat(resolved!!.properties).containsKey("external_reference_id")
        assertThat(resolved.properties).containsKey("user_risk_score")
        assertThat(resolved.properties).doesNotContainKey("externalReferenceId")
    }

    @Test
    fun `should generate camelCase properties in schema when ObjectMapper has LOWER_CAMEL_CASE strategy`() {
        val objectMapper =
            ObjectMapper().apply {
                propertyNamingStrategy = PropertyNamingStrategies.LOWER_CAMEL_CASE
                registerKotlinModule()
            }

        val resolver = ModelResolver(objectMapper)
        val context = ModelConverterContextImpl(listOf(resolver))
        val resolved = resolver.resolve(AnnotatedType(TestNamingDto::class.java), context, null)

        assertThat(resolved).isNotNull
        assertThat(resolved!!.properties).containsKey("externalReferenceId")
        assertThat(resolved.properties).containsKey("userRiskScore")
        assertThat(resolved.properties).doesNotContainKey("external_reference_id")
    }
}
