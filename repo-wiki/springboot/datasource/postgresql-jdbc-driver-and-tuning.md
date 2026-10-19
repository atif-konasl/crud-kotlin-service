# PostgreSQL JDBC Driver Tuning & Production Best Practices

In Spring Boot applications connecting to PostgreSQL, the **PostgreSQL JDBC Driver** (`org.postgresql:postgresql`) is the low-level communication conduit implementing the PostgreSQL Frontend/Backend Protocol 3.0.

While HikariCP manages the pool of connections, the JDBC driver controls socket-level timeouts, SSL/TLS validation, prepared statement caching, and batch query execution.

This guide explains how to tune PostgreSQL JDBC parameters to achieve maximum throughput, resilience, and security.

---

## Table of Contents
1. [PostgreSQL JDBC Architecture & Protocol 3.0](#1-postgresql-jdbc-architecture--protocol-30)
2. [Critical JDBC URL Parameters](#2-critical-jdbc-url-parameters)
   - [2.1 `sslmode` & Mutual TLS](#21-sslmode--mutual-tls)
   - [2.2 Socket & Connect Timeouts (`connectTimeout`, `socketTimeout`)](#22-socket--connect-timeouts-connecttimeout-sockettimeout)
   - [2.3 `reWriteBatchedInserts=true` (The 5x Batch Boost)](#23-rewritebatchedinsertstrue-the-5x-batch-boost)
   - [2.4 Prepared Statement Caching (`prepareThreshold`)](#24-prepared-statement-caching-preparethreshold)
3. [Configuring Driver Properties via `spring.datasource.hikari.data-source-properties`](#3-configuring-driver-properties-via-springdatasourcehikaridata-source-properties)
4. [Security: Least-Privilege Flyway vs. Application Users](#4-security-least-privilege-flyway-vs-application-users)
5. [Complete Production-Hardened Configuration](#5-complete-production-hardened-configuration)

---

## 1. PostgreSQL JDBC Architecture & Protocol 3.0

The PostgreSQL JDBC driver translates Java SQL statements (`PreparedStatement.executeQuery()`) into binary messages sent across a TCP socket:

```
┌─────────────────────────────────────────────────────────────┐
│                    Java Application (Spring)                │
│                               │                             │
│                  Hibernate / JPA Entity Manager             │
│                               │                             │
│                       HikariCP Pool                         │
│                               │                             │
│                   PostgreSQL JDBC Driver                    │
│           (Protocol 3.0 / SSL / Statement Cache)            │
└───────────────────────────────┬─────────────────────────────┘
                                │ TCP Socket
┌───────────────────────────────▼─────────────────────────────┐
│                    PostgreSQL Postmaster                    │
│                               │                             │
│               Dedicated Backend Process (PID)               │
│          (Parser -> Planner -> Executor -> Storage)         │
└─────────────────────────────────────────────────────────────┘
```

### Key Differences in PostgreSQL:
1. **Process-per-Connection:** PostgreSQL does not use threads for connections; each connection is an isolated operating system process.
2. **Catalog Queries:** Upon initial connection, the driver queries `pg_catalog` to determine server version, datatypes, and capabilities. Reusing connections via HikariCP avoids repeating these expensive startup catalog queries.

---

## 2. Critical JDBC URL Parameters

### 2.1 `sslmode` & Mutual TLS
In cloud deployments, database traffic must be encrypted in transit:

| `sslmode` Value | Encryption | Certificate Validation | Risk |
| :--- | :---: | :---: | :--- |
| `disable` | ❌ No | ❌ No | Plaintext credentials sent over network |
| `prefer` *(Default)* | ⚠️ If server allows | ❌ No | Vulnerable to SSL stripping attacks |
| `require` | ✅ Yes | ❌ No | Encrypted, but vulnerable to Man-In-The-Middle (MITM) spoofing |
| `verify-ca` | ✅ Yes | ⚠️ Verifies CA only | Validates certificate issuer |
| **`verify-full`** *(Prod Best Practice)* | ✅ Yes | ✅ Validates CA + Hostname | Fully secure against MITM and DNS spoofing |

```yaml
spring:
  datasource:
    url: jdbc:postgresql://db.prod.internal:5432/crud_db?sslmode=verify-full&sslrootcert=/etc/ssl/certs/rds-combined-ca-bundle.pem
```

---

### 2.2 Socket & Connect Timeouts (`connectTimeout`, `socketTimeout`)

By default, standard Java TCP sockets block indefinitely unless an explicit timeout is configured:

```yaml
url: jdbc:postgresql://localhost:5432/crud_db?connectTimeout=5&socketTimeout=30
```

- **`connectTimeout=5` (5 seconds):**
  If the database host IP is unreachable (e.g. cloud security group misconfigured, network partition, or DNS failure), the TCP connection attempt fails fast after 5 seconds instead of hanging for the OS default of 127 seconds.
- **`socketTimeout=30` (30 seconds):**
  If a query is sent to PostgreSQL and the database server abruptly loses power, freezes, or encounters an infinite disk lock, the client socket will throw a `SocketTimeoutException` after 30 seconds rather than blocking the worker thread forever.

---

### 2.3 `reWriteBatchedInserts=true` (The 5x Batch Boost)

By default, when Hibernate executes a batch insert of 1,000 rows:
```sql
INSERT INTO resources (id, name, created_at) VALUES ('c1...', 'Item 1', '...');
INSERT INTO resources (id, name, created_at) VALUES ('c2...', 'Item 2', '...');
... (1000 separate INSERT statements)
```
Each statement requires its own round-trip communication over the network to the database backend.

When `reWriteBatchedInserts=true` is enabled:
The PostgreSQL JDBC driver intercepts the batched statements in memory and **rewrites them into a single multi-row SQL statement**:
```sql
INSERT INTO resources (id, name, created_at) VALUES 
('c1...', 'Item 1', '...'),
('c2...', 'Item 2', '...'),
...
('c1000...', 'Item 1000', '...');
```
**Impact:** Reduces 1,000 network roundtrips to **1 single network roundtrip**, boosting bulk insert throughput by **5x to 10x**!

---

### 2.4 Prepared Statement Caching (`prepareThreshold`)

When an application executes a `PreparedStatement`:
1. **Client-Side Preparation (`prepareThreshold: 5` default):** For the first 4 executions, the driver sends the query using simple query mode.
2. **Server-Side Prepared Statement:** On the 5th execution, the driver tells PostgreSQL to compile the execution plan (`PREPARE stmt_name AS SELECT ...`) and assign a server-side handle.
3. Subsequent executions only send the parameters and statement handle, eliminating SQL parsing and query planning overhead on the PostgreSQL server!

To optimize this in HikariCP:
```yaml
spring:
  datasource:
    hikari:
      data-source-properties:
        prepareThreshold: 5
        preparedStatementCacheQueries: 256
        preparedStatementCacheSizeMiB: 5
```

---

## 3. Configuring Driver Properties via `spring.datasource.hikari.data-source-properties`

Rather than cramming every parameter into the JDBC URL string, HikariCP provides `data-source-properties` to pass driver-specific properties cleanly:

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/crud_db}
    username: ${SPRING_DATASOURCE_USERNAME:app_user}
    password: ${SPRING_DATASOURCE_PASSWORD:app_password}
    driver-class-name: org.postgresql.Driver
    hikari:
      pool-name: ResourceHikariCP
      maximum-pool-size: 10
      minimum-idle: 10
      connection-timeout: 3000
      max-lifetime: 1740000
      keepalive-time: 120000
      leak-detection-threshold: 2000
      data-source-properties:
        reWriteBatchedInserts: true
        prepareThreshold: 5
        preparedStatementCacheQueries: 256
        preparedStatementCacheSizeMiB: 5
        tcpKeepAlive: true
```

---

## 4. Security: Least-Privilege Flyway vs. Application Users

In our [`application.yml`](file:///src/main/resources/application.yml#L60-L67), Flyway runs automated migrations on startup:

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations:
      - classpath:db/migration
```

### The Security Principle of Least Privilege:
- **Application User (`app_user`):** Should only have `DML` privileges (`SELECT`, `INSERT`, `UPDATE`, `DELETE`). It should **never** have permission to `DROP TABLE`, `ALTER TABLE`, or create schema objects!
- **Migration User (`migration_user`):** Has `DDL` permissions (`CREATE TABLE`, `ALTER TABLE`, `DROP INDEX`) to apply schema changes during deployment.

### Spring Boot Support:
Spring Boot allows Flyway to run with its own dedicated administrative credentials:
```yaml
spring:
  datasource:
    username: app_user          # Only DML privileges (SELECT, INSERT, UPDATE, DELETE)
    password: ${APP_USER_PASS}
  flyway:
    enabled: true
    user: migration_admin       # DDL privileges for migrations only
    password: ${MIGRATION_ADMIN_PASS}
```
If an SQL injection vulnerability ever occurred in an application endpoint, the attacker would be unable to execute destructive DDL commands (`DROP TABLE`) because `app_user` lacks DDL permissions.

---

## 5. Complete Production-Hardened Configuration

Here is the recommended production-grade configuration combining HikariCP and PostgreSQL driver tuning:

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/crud_db?connectTimeout=5&socketTimeout=30}
    username: ${SPRING_DATASOURCE_USERNAME:app_user}
    password: ${SPRING_DATASOURCE_PASSWORD:app_password}
    driver-class-name: org.postgresql.Driver
    hikari:
      pool-name: ResourceHikariCP
      maximum-pool-size: 10
      minimum-idle: 10              # Fixed pool size (no dynamic spikes)
      connection-timeout: 3000      # Fail fast after 3 seconds
      idle-timeout: 300000          # 5 minutes
      max-lifetime: 1740000         # 29 minutes (retire before 30-min cloud firewall drops)
      keepalive-time: 120000        # 2 minutes (heartbeat to keep stateful NAT alive)
      leak-detection-threshold: 2000 # 2 seconds (capture stack trace of leaked connections)
      auto-commit: false            # Managed by Spring JpaTransactionManager
      data-source-properties:
        reWriteBatchedInserts: true # 5x-10x batch insert boost
        prepareThreshold: 5         # Server-side prepared statement optimization
        preparedStatementCacheQueries: 256
        preparedStatementCacheSizeMiB: 5
        tcpKeepAlive: true
```
