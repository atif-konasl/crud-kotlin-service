package com.example.demo.infrastructure.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun customOpenAPI(): OpenAPI {
        val securitySchemeName = "BearerAuth"
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
                    ),
            )
    }
}
