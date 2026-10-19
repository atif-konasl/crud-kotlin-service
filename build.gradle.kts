import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    kotlin("plugin.jpa") version "2.2.21"

    id("org.springframework.boot") version "3.5.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    id("org.flywaydb.flyway") version "11.17.1"
}

ktlint {
    version.set("1.5.0")
    android.set(false)
    outputToConsole.set(true)
    ignoreFailures.set(false)
}

group = "com.example.demo"
version = "0.0.1-SNAPSHOT"
description = "crud-kotlin-service"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    // Web & Reactive WebClient
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework:spring-tx")

    // Persistence (JPA, PostgreSQL & Flyway)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Kotlin JSON & Reflection
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Kotlin Coroutines & Reactive Bridge
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j")

    // Security & OAuth2 Resource Server JWT
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // OpenAPI / Swagger Documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.5")

    // Distributed Tracing (Micrometer + OpenTelemetry OTLP Exporter)
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")

    // Logging (Logstash JSON Encoder & Janino for Logback conditionals)
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    implementation("org.codehaus.janino:janino")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.mockk:mockk-jvm:1.14.6")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.wiremock:wiremock-standalone:3.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testRuntimeOnly("com.h2database:h2")
}

tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask::class.java) {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging.events =
        setOf(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED,
        )
}

tasks.bootJar {
    archiveFileName.set("app.jar")
}
