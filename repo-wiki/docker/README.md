# Docker & Docker Compose Architecture Guide

This guide explains the design, security hardening, and orchestration mechanics behind our [`Dockerfile`](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/Dockerfile) and [`docker-compose.yml`](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/docker-compose.yml).

---

## 1. Multi-Stage Dockerfile Architecture

Instead of shipping a monolithic image containing the entire JDK, Kotlin compiler, and build tools, we use a **two-stage build**:

```mermaid
flowchart LR
    subgraph Stage1 [Stage 1: Builder (eclipse-temurin:21-jdk-alpine)]
        Gradle[Gradle Wrapper + build.gradle.kts] --> Download[Resolve & Cache Dependencies]
        Download --> Compile[Compile Kotlin Code & Run bootJar]
        Compile --> Jar[app.jar ~50MB]
    end

    subgraph Stage2 [Stage 2: Runtime (eclipse-temurin:21-jre-alpine)]
        JRE[Minimal JRE ~180MB] --> NonRoot[Create non-root appuser]
        NonRoot --> DumbInit[Install dumb-init & curl]
        DumbInit --> CopyJar[COPY --from=builder app.jar]
        CopyJar --> Run[Execute via dumb-init]
    end

    Jar -->|Only the JAR is transferred| CopyJar
```

### Why Multi-Stage Builds Matter:
- **Reduced Attack Surface:** The final production container has no compiler, no Gradle cache, and no Git history.
- **Smaller Image Size:** The JDK is ~400MB; the JRE is ~180MB. Only runtime binaries are included in production.
- **Fast Build Times via Layer Caching:**
  - Notice the execution order in our [`Dockerfile`](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/Dockerfile):
    1. `COPY gradlew build.gradle.kts settings.gradle.kts ./`
    2. `RUN ./gradlew dependencies --no-daemon || true`
    3. `COPY src/ src/`
  - Docker caches steps 1 and 2! If you only edit Kotlin code inside `src/`, Docker skips downloading dependencies and starts directly at compilation.

> [!TIP]
> For a full breakdown of the Kitchen Analogy, security trade-offs, and single-stage anti-patterns, see the dedicated guide: [multi-stage-builds.md](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/repo-wiki/docker/multi-stage-builds.md).

---

## 2. Base Image Selection: Why `eclipse-temurin:21-jre-alpine`?

In enterprise Dockerfiles (such as Visable's `explore-backend`), you often see:
- `gradle:9.1.0-jdk21` used in the build stage.
- `amazoncorretto:21.0.3-alpine3.19` used in the runtime stage.

Here is the technical comparison of why we chose `eclipse-temurin:21-jre-alpine` and how the images differ:

### 2.1 Runtime Comparison: JRE vs. JDK (Temurin vs. Corretto)

| Attribute | `eclipse-temurin:21-jre-alpine` *(Our Choice)* | `amazoncorretto:21.0.3-alpine3.19` *(Reference)* |
| :--- | :--- | :--- |
| **Java Package** | **JRE (Runtime Only)** | **JDK (Full Development Kit)** |
| **Included Tools** | JVM (`java`), standard core class libraries | `java`, **`javac` (compiler)**, `javap`, `jcmd`, debuggers, C headers |
| **Base Image Size** | **~160 MB** | **~320 MB (2x larger!)** |
| **Security Surface** | **Minimal** (No compiler or build tools) | Higher (Attacker can compile exploits in-container) |
| **Vendor / Backing** | Eclipse Foundation (Adoptium - Red Hat, IBM, Microsoft) | Amazon Web Services (AWS) |
| **Primary Use Case** | Vendor-neutral, minimal production microservices | Optimized for AWS ECS/EKS/Graviton environments |

#### Key Takeaway: Never Ship a Compiler (`javac`) to Production
The biggest difference between our image and `amazoncorretto:21.0.3-alpine` is **JRE vs. JDK**:
1. **Size:** Running a Spring Boot fat JAR only requires the JVM to execute bytecode. Shipping `javac`, development header files, and debugging symbols doubles the image size unnecessarily.
2. **CIS Docker Security Benchmark:** Enterprise security compliance frameworks mandate removing build tools and compilers from production containers. If an attacker gains remote execution inside the container, having `javac` present allows them to compile custom malicious binaries on the fly.

### 2.2 Builder Stage Comparison: `eclipse-temurin-jdk` vs. `gradle:9.1.0-jdk21`

| Attribute | `eclipse-temurin:21-jdk-alpine` + `./gradlew` | `public.ecr.aws/.../gradle:9.1.0-jdk21` |
| :--- | :--- | :--- |
| **Gradle Engine** | Uses project-pinned **Gradle Wrapper (`./gradlew`)** | Uses pre-installed system Gradle `9.1.0` |
| **Base OS** | Alpine Linux (`musl` libc) | Ubuntu/Debian (`glibc`) |
| **Builder Image Size** | **~350 MB** | **~850 MB - 1 GB** |
| **Build Determinism** | 100% identical to developer machines running `./gradlew` | Risk of version drift if wrapper and image Gradle versions differ |

Using the **Gradle Wrapper (`./gradlew`)** directly inside a clean JDK image guarantees that CI and Docker builds always execute the exact Gradle version defined in `gradle/wrapper/gradle-wrapper.properties` without downloading a 1GB generic image.

---

## 3. Security Hardening: Non-Root Execution

By default, Docker containers run processes as `root` (UID 0). If an attacker discovers a remote code execution vulnerability in a web application running as root, they potentially gain root access to the host machine.

In our [`Dockerfile`](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/Dockerfile):
```dockerfile
# Create an unprivileged user and group
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Set ownership of the JAR to the unprivileged user
COPY --from=builder --chown=appuser:appgroup /app/build/libs/app.jar /app/app.jar

# Switch execution away from root
USER appuser:appgroup
```
- The application process runs as `appuser` (unprivileged UID), preventing privilege escalation and complying with enterprise container security policies.

---

## 4. The Linux PID 1 Problem & Why We Use `dumb-init`

When you launch a process in Docker, it becomes **Process ID 1 (PID 1)** inside the container namespace.

### The Problem with Java as PID 1:
In Linux, PID 1 has two critical OS responsibilities:
1. **Signal Handling:** Unhandled signals (such as `SIGTERM` sent during `docker stop` or Kubernetes pod eviction) are ignored by PID 1 unless explicitly coded. Java does not always forward kernel signals correctly when running directly as PID 1.
2. **Zombie Process Reaping:** Orphaned child processes are reparented to PID 1. Standard Java does not reap zombie processes, which can cause system process table exhaustion.

### The Solution: `dumb-init`
`dumb-init` is a lightweight (20KB) init process written in C:
```dockerfile
ENTRYPOINT ["dumb-init", "--", "java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
```
- `dumb-init` runs as PID 1.
- When Docker sends `SIGTERM` to shut down the container, `dumb-init` immediately forwards the signal to the Java process.
- Spring Boot catches `SIGTERM`, triggers its **graceful shutdown hooks** (finishing in-flight HTTP requests and closing database connection pools cleanly), and exits without being forcefully killed (`SIGKILL`).

> [!TIP]
> For a full breakdown of Linux signal handling, zombie process reaping, and visual sequence diagrams, see the dedicated guide: [linux-pid-1-and-dumb-init.md](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/repo-wiki/docker/linux-pid-1-and-dumb-init.md).

---

## 5. Container-Aware JVM Memory Settings

In containerized environments (Kubernetes, AWS ECS, Docker), Java processes can crash if they allocate more memory than the container's cgroup limit.

Our `ENTRYPOINT` configures:
```bash
java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -jar /app/app.jar
```
- **`-XX:+UseContainerSupport`**: Ensures the JVM queries Docker/cgroup memory limits instead of the total host RAM.
- **`-XX:MaxRAMPercentage=75.0`**: Dynamically calculates heap size to use at most 75% of the container memory, leaving 25% for OS, Metaspace, threads, and off-heap buffers (preventing Linux Out-Of-Memory / OOMKilled crashes).

---

## 6. Docker Compose Orchestration (`docker-compose.yml`)

Our [`docker-compose.yml`](file:///Users/atifanowar/Workspace/interview/crud-kotlin-service/docker-compose.yml) orchestrates both the Spring Boot service and PostgreSQL:

```yaml
services:
  app:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: crud-kotlin-service
    ports:
      - "8080:8080"
    environment:
      - SERVER_PORT=8080
      - SPRING_APPLICATION_NAME=crud-kotlin-service
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/crud_db
      - SPRING_DATASOURCE_USERNAME=app_user
      - SPRING_DATASOURCE_PASSWORD=app_password
    depends_on:
      postgres:
        condition: service_healthy # Wait until PostgreSQL is actually ready to receive queries!
    networks:
      - app-network

  postgres:
    image: postgres:16-alpine
    container_name: postgres-db
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: crud_db
      POSTGRES_USER: app_user
      POSTGRES_PASSWORD: app_password
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app_user -d crud_db"]
      interval: 5s
      timeout: 5s
      retries: 5
      start_period: 5s
    networks:
      - app-network

volumes:
  postgres_data:
    driver: local

networks:
  app-network:
    driver: bridge
```

### Why `condition: service_healthy` is Critical
In basic Docker Compose files, developers often write `depends_on: [postgres]`.
- That only waits for the PostgreSQL container to **start**, not for the database engine to finish initializing!
- Spring Boot starts up faster than PostgreSQL, attempts to connect immediately, and crashes with `Connection refused`.
- By pairing `healthcheck` (`pg_isready`) with `condition: service_healthy`, Docker Compose ensures PostgreSQL is fully accepting SQL queries before booting the Spring Boot application.

---

## 7. Daily Docker CLI Cheat Sheet

```bash
# Start all services in the background
docker compose up -d

# View real-time logs from both app and postgres
docker compose logs -f

# View logs specifically from the Spring Boot app
docker compose logs -f app

# Rebuild the app image after changing source code
docker compose up -d --build app

# Check container health status
docker compose ps

# Stop all containers (preserving database volume)
docker compose down

# Stop all containers and wipe the database volume (clean reset)
docker compose down -v
```

---

## 8. 60-Second Verbal Script (PREP Framework)

Use this script during technical interviews when asked: *"How do you design and optimize container images for a production Spring Boot microservice?"*

> **Point:**  
> Our production containerization strategy uses a multi-stage Dockerfile with non-root security, signal handling via `dumb-init`, and health-gated Docker Compose orchestration.
>
> **Reason:**  
> Multi-stage builds keep runtime images minimal and secure by separating the JDK builder from the JRE runner. Meanwhile, running Java as PID 1 can cause shutdown deadlocks, and booting containers concurrently without health probes causes connection refused errors.
>
> **Example:**  
> In our `Dockerfile`, Stage 1 caches dependencies and packages `app.jar`, while Stage 2 copies only the JAR into `eclipse-temurin:21-jre-alpine` under a non-root `appuser`. We use `dumb-init` as PID 1 to ensure `SIGTERM` signals trigger graceful Spring shutdown, and enforce `-XX:MaxRAMPercentage=75.0` to respect cgroup memory limits. In `docker-compose.yml`, the application uses `condition: service_healthy` with `pg_isready` so it never attempts to connect before PostgreSQL is ready.
>
> **Point (Restated):**  
> This setup guarantees fast build caching, hardened security, predictable memory consumption, and zero race conditions during service startup.
