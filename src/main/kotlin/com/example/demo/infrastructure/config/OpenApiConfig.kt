package com.example.demo.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.core.jackson.ModelResolver
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun modelResolver(objectMapper: ObjectMapper): ModelResolver = ModelResolver(objectMapper)

    @Bean
    fun customOpenAPI(): OpenAPI {
        val securitySchemeName = "BearerAuth"
        val problemDetailSchema =
            Schema<Any>()
                .type("object")
                .description("RFC 7807 standardized Problem Detail error response")
                .addProperty("type", StringSchema().example("about:blank"))
                .addProperty("title", StringSchema().example("Not Found"))
                .addProperty("status", IntegerSchema().example(404))
                .addProperty("detail", StringSchema().example("Resource with ID was not found."))
                .addProperty("instance", StringSchema().example("/api/v1/resources/c1f7a0b5-74c6-43b5-9013-14a5e70dd7f8"))
                .addProperty("timestamp", StringSchema().example("2026-10-19T11:38:00Z"))

        return OpenAPI()
            .info(
                Info()
                    .title("CRUD Kotlin Resource Service API")
                    .description("Hexagonal Architecture REST API in Kotlin & Spring Boot with PostgreSQL and OAuth2 JWT authentication.")
                    .version("v1.0.0")
                    .contact(Contact().name("API Engineering Team").email("api-team@example.com"))
                    .license(License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")),
            ).addSecurityItem(SecurityRequirement().addList(securitySchemeName))
            .components(
                Components()
                    .addSecuritySchemes(
                        securitySchemeName,
                        SecurityScheme()
                            .name(securitySchemeName)
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("Provide a valid OAuth2 JWT Bearer token to authorize API requests."),
                    ).addSchemas("ProblemDetail", problemDetailSchema),
            )
    }

    @Bean
    fun globalProblemDetailResponses(): OperationCustomizer =
        OperationCustomizer { operation, _ ->
            val problemDetailRef = Schema<Any>().`$ref`("#/components/schemas/ProblemDetail")
            val problemContent =
                Content().addMediaType(
                    "application/problem+json",
                    MediaType().schema(problemDetailRef),
                )

            val responses = operation.responses
            if (!responses.containsKey("400")) {
                responses.addApiResponse(
                    "400",
                    ApiResponse().description("Bad Request / Validation Error").content(problemContent),
                )
            }
            if (!responses.containsKey("401")) {
                responses.addApiResponse(
                    "401",
                    ApiResponse().description("Unauthorized - Missing, expired, or invalid OAuth2 Bearer token").content(problemContent),
                )
            }
            if (!responses.containsKey("403")) {
                responses.addApiResponse(
                    "403",
                    ApiResponse().description("Forbidden - Insufficient permissions or scopes").content(problemContent),
                )
            }
            if (!responses.containsKey("404")) {
                responses.addApiResponse(
                    "404",
                    ApiResponse().description("Resource Not Found").content(problemContent),
                )
            }
            if (!responses.containsKey("409")) {
                responses.addApiResponse(
                    "409",
                    ApiResponse().description("Conflict - Invalid resource state").content(problemContent),
                )
            }
            if (!responses.containsKey("422")) {
                responses.addApiResponse(
                    "422",
                    ApiResponse().description("Unprocessable Entity - Business rule violation").content(problemContent),
                )
            }
            if (!responses.containsKey("500")) {
                responses.addApiResponse(
                    "500",
                    ApiResponse().description("Internal Server Error").content(problemContent),
                )
            }
            operation
        }
}
