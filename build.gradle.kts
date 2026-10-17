import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.implementation

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    kotlin("plugin.allopen") version "2.2.21"
    kotlin("plugin.noarg") version "2.2.21"

    id("org.springframework.boot") version "3.5.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    id("org.flywaydb.flyway") version "11.17.1"
    id("org.sonarqube") version "6.3.1.5724"
    jacoco
}

group = "com.visable"
version = "0.0.1-SNAPSHOT"
description = "explore-backend"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("io.awspring.cloud:spring-cloud-aws-dependencies:3.4.1")
    }
}

dependencies {
    // -- SPRING FRAMEWORK
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-oauth2-client:6.5.3")

    implementation("org.springframework.boot:spring-boot-starter-webflux")

    implementation("io.awspring.cloud:spring-cloud-aws-autoconfigure")
    implementation("io.awspring.cloud:spring-cloud-aws-starter-sqs")
    implementation("io.awspring.cloud:spring-cloud-aws-starter-sns")
    // --

    // -- Database
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.postgresql:postgresql:42.7.8")
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.10.3")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("com.zaxxer:HikariCP:7.0.2")
    // --
    implementation("net.javacrumbs.shedlock:shedlock-spring:6.10.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:6.10.0")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.1")
    implementation("org.json:json:20240303")
    implementation("com.opencsv:opencsv:5.12.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")

    // -- Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test:6.5.3")
    testImplementation("io.awspring.cloud:spring-cloud-aws-test")
    testImplementation("io.projectreactor:reactor-test:3.8.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.2.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    // ----  mockk
    testImplementation("io.mockk:mockk-jvm:1.14.6")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    // --
}

tasks.named("compileKotlin", org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask::class.java) {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    outputs.upToDateWhen { false } // make sure the tests are run all the time
    testLogging.events =
        setOf(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
}

tasks.bootJar {
    archiveFileName.set("app.jar")
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

kotlin {
    allOpen {
        annotation("jakarta.persistence.Entity")
        annotation("jakarta.persistence.Embedded")
        annotation("jakarta.persistence.MappedSuperclass")
    }
    noArg {
        annotation("jakarta.persistence.Entity")
        annotation("jakarta.persistence.Embeddable")
    }
}

ktlint {
    version.set("1.6.0")
    verbose.set(true)
    outputToConsole.set(true)
}

// disable all ktlint for build
tasks.named("check") {
    dependsOn.removeAll { it.toString().contains("ktlint") }
}
