# CI/CD Workflows Guide

This guide documents the GitHub Actions workflows configured in [`.github/workflows/`](file:///.github/workflows/).

---

## 1. Overview

The repository includes three straightforward workflows focused on building, testing, and packaging the application without external deployment dependencies:

| Workflow File | Trigger | Purpose |
| :--- | :--- | :--- |
| [`.github/workflows/ci.yml`](file:///.github/workflows/ci.yml) | `push` (main), `pull_request` (main) | Compiles Kotlin code, runs unit & integration tests (`./gradlew build`). |
| [`.github/workflows/cd-manual.yml`](file:///.github/workflows/cd-manual.yml) | `workflow_dispatch` (manual) | Builds the Spring Boot fat JAR (`bootJar`) and packages the Docker container image. |
| [`.github/workflows/cd-scheduled.yml`](file:///.github/workflows/cd-scheduled.yml) | `schedule` (Daily 02:00 UTC), `workflow_dispatch` | Runs scheduled/nightly verification and packages a `nightly` Docker image. |

---

## 2. Workflows Breakdown

### 2.1 CI (`ci.yml`)
- Automatically triggered on `push` and `pull_request` to `main`.
- Sets up Java 21 (Temurin) with built-in Gradle dependency caching (`cache: 'gradle'`).
- Runs two parallel jobs:
  - **`lint`**: Executes `./gradlew ktlintCheck --no-daemon` to enforce Kotlin code style via KtLint.
  - **`test`**: Executes `./gradlew test --no-daemon` to run unit and integration test suites.

### 2.2 CD Manual (`cd-manual.yml`)
- Triggered manually on demand from the GitHub Actions tab (`workflow_dispatch`).
- Builds the executable fat JAR (`./gradlew bootJar --no-daemon`).
- Builds the Docker image tagged with the commit SHA (`crud-kotlin-service:<SHA>`).

### 2.3 CD Scheduled (`cd-scheduled.yml`)
- Runs automatically on a daily schedule (`0 2 * * *`).
- Runs full build & tests to detect any dependency or build issues.
- Packages the Docker image tagged as `crud-kotlin-service:nightly`.
