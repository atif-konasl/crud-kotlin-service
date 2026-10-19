# Hibernate 6 Performance, JDBC Batching & The $N+1$ Problem

In high-throughput Spring Boot microservices, database interaction is almost always the primary latency bottleneck. Hibernate 6 (the default JPA provider in Spring Boot 3) includes powerful optimizations that can increase write and read performance by an order of magnitude.

This guide explores JDBC statement batching, how to eliminate the $N+1$ query problem, the `IDENTITY` generation anti-pattern, and production SQL logging practices.

---

## Table of Contents
1. [JDBC Statement Batching (`jdbc.batch_size`)](#1-jdbc-statement-batching-jdbcbatch_size)
2. [Why `order_inserts` and `order_updates` are Mandatory](#2-why-order_inserts-and-order_updates-are-mandatory)
3. [The `GenerationType.IDENTITY` Batching Trap](#3-the-generationtypeidentity-batching-trap)
4. [The $N+1$ Query Problem & 3 Ways to Eliminate It](#4-the-n1-query-problem--3-ways-to-eliminate-it)
   - [Detection & Symptoms](#detection--symptoms)
   - [Solution 1: `JOIN FETCH`](#solution-1-join-fetch)
   - [Solution 2: JPA 2.1 `@EntityGraph`](#solution-2-jpa-21-entitygraph)
   - [Solution 3: Global Batch Fetching (`default_batch_fetch_size`)](#solution-3-global-batch-fetching-default_batch_fetch_size)
5. [Production SQL Logging & Observability](#5-production-sql-logging--observability)
6. [Complete Production-Hardened JPA Configuration](#6-complete-production-hardened-jpa-configuration)

---

## 1. JDBC Statement Batching (`jdbc.batch_size`)

### The Problem without Batching:
When your application saves 1,000 entities in a loop:
```kotlin
@Transactional
fun bulkImport(resources: List<Resource>) {
    resources.forEach { resourceRepository.save(it) }
}
```
Without batching enabled, Hibernate executes 1,000 individual `java.sql.PreparedStatement.executeUpdate()` calls. That means:
- **1,000 separate TCP network round trips** between the application and PostgreSQL.
- Heavy network latency and CPU overhead on both ends.

### The Solution:
Enable Hibernate JDBC batching in `application.yml`:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 25 # Groups 25 INSERTs or UPDATEs into a single network packet
```

Combined with PostgreSQL's `reWriteBatchedInserts: true` in HikariCP, the PostgreSQL JDBC driver merges the 25 individual insert statements into a single multi-row SQL command:
```sql
INSERT INTO resources (id, name, created_at) VALUES 
('c1...', 'Item 1', '...'),
('c2...', 'Item 2', '...'),
...
('c25...', 'Item 25', '...');
```
**Impact:** Reduces network round trips from 1,000 down to 40, slashing bulk operation time by **up to 80%**!

---

## 2. Why `order_inserts` and `order_updates` are Mandatory

Enabling `batch_size: 25` alone is often **not enough**!

### How Hibernate Batches Statements:
Hibernate can only batch statements that execute the **exact same prepared SQL statement string**. As soon as a different SQL statement is encountered, the current batch is flushed to the database, and a new batch is started.

#### Example Without Ordering:
Suppose you save alternating `Parent` and `Child` entities:
1. `INSERT INTO parent ...` (Batch 1 started)
2. `INSERT INTO child ...`  (Batch 1 broken and flushed! Batch 2 started)
3. `INSERT INTO parent ...` (Batch 2 broken and flushed! Batch 3 started)
4. `INSERT INTO child ...`  (Batch 3 broken and flushed! Batch 4 started)

**Result:** Despite `batch_size: 25`, batching is broken on every statement, resulting in 0% batching efficiency!

#### The Fix:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        order_inserts: true # Sorts INSERT statements by entity type before flushing
        order_updates: true # Sorts UPDATE statements by entity type before flushing
```
Hibernate sorts the execution queue so all `Parent` inserts execute together in one batch, followed by all `Child` inserts in another batch.

---

## 3. The `GenerationType.IDENTITY` Batching Trap

One of the most famous traps in JPA/Hibernate is:

```kotlin
// ❌ ANTI-PATTERN: Completely disables Hibernate JDBC Batching!
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
val id: Long? = null
```

### Why `IDENTITY` Disables Batching:
1. With `GenerationType.IDENTITY`, the database auto-increment/serial column assigns the primary key during the `INSERT` execution.
2. Hibernate **must know the entity's ID immediately** to associate it with the Persistence Context (First-Level Cache).
3. Therefore, Hibernate **cannot postpone the `INSERT` to the end of the transaction for batching**—it is forced to execute an immediate `INSERT` statement for every entity and read back the generated ID using `getGeneratedKeys()`.
4. Hibernate explicitly disables JDBC batching when `GenerationType.IDENTITY` is used!

### The Two High-Performance Alternatives:

#### Alternative A: Application-Assigned UUIDs (Our Project's Architecture)
```kotlin
@Entity
@Table(name = "resources")
class ResourceJpaEntity(
    @Id
    val id: UUID, // Pre-assigned before persistence!
    // ...
)
```
Because the domain layer generates `UUID.randomUUID()` before calling `save()`, Hibernate has the identifier upfront and can batch inserts with 100% efficiency.

#### Alternative B: `GenerationType.SEQUENCE` (with `allocationSize = 50`)
```java
@Id
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "res_seq")
@SequenceGenerator(name = "res_seq", sequenceName = "resources_seq", allocationSize = 50)
private Long id;
```
Hibernate calls `nextval('resources_seq')` once and reserves a block of 50 IDs in JVM memory. It does not hit the database again for the next 49 inserts, enabling full JDBC batching!

---

## 4. The $N+1$ Query Problem & 3 Ways to Eliminate It

### Detection & Symptoms:
The $N+1$ problem occurs when an application executes 1 initial query to fetch $N$ parent records, followed by $N$ additional queries to fetch child associations for each parent.

```
Query 1: SELECT * FROM resources; (Returns 50 rows)
Query 2: SELECT * FROM audit_logs WHERE resource_id = '1';
Query 3: SELECT * FROM audit_logs WHERE resource_id = '2';
...
Query 51: SELECT * FROM audit_logs WHERE resource_id = '50';
```
Total queries: $1 + 50 = 51$ queries!

---

### Solution 1: `JOIN FETCH`
In JPQL / HQL, use the `FETCH` keyword:
```kotlin
@Query("SELECT r FROM ResourceJpaEntity r LEFT JOIN FETCH r.auditLogs")
fun findAllWithAuditLogs(): List<ResourceJpaEntity>
```
Executes a single SQL query with an `INNER JOIN` or `LEFT JOIN`, populating the parent entities and their collections in a single round trip.

---

### Solution 2: JPA 2.1 `@EntityGraph`
Using Spring Data JPA declarative entity graphs:
```kotlin
@EntityGraph(attributePaths = ["auditLogs", "tags"])
override fun findAll(): List<ResourceJpaEntity>
```
Instructs Hibernate to generate the appropriate outer join dynamically without writing custom JPQL strings.

---

### Solution 3: Global Batch Fetching (`default_batch_fetch_size`)
For situations where writing custom `JOIN FETCH` queries for every endpoint is impractical, Hibernate provides global batch fetching:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 30
```

#### How it works:
When lazy loading is triggered for child collections, instead of executing 50 individual queries with `WHERE resource_id = ?`, Hibernate executes **2 batch queries** using the SQL `IN` operator:
```sql
SELECT * FROM audit_logs WHERE resource_id IN (?, ?, ..., ?); -- 30 IDs
SELECT * FROM audit_logs WHERE resource_id IN (?, ?, ..., ?); -- 20 IDs
```
Reduces 51 queries to **3 queries**!

---

## 5. Production SQL Logging & Observability

### Why `spring.jpa.show-sql: true` is Banned in Production:
- It writes directly to `System.out`, bypassing Logback/SLF4J.
- Lacks timestamps, log level (`DEBUG`, `INFO`), thread names, and MDC tracing context.
- Cannot be redirected or formatted by structured JSON log appenders.

### The Production Best Practice:
Use Logback logging levels for Hibernate in `application.yml`:

```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG                    # Logs SQL statements through SLF4J
    org.hibernate.orm.jdbc.bind: TRACE          # Logs bind parameter values (? -> 'abc') in Hibernate 6
    org.hibernate.stat: DEBUG                   # Logs session statistics (query execution time, cache hits)
```

In production, keep `org.hibernate.SQL` set to `WARN` or `ERROR` to prevent log volume saturation, and turn it on dynamically via Spring Boot Actuator (`POST /actuator/loggers/org.hibernate.SQL`) only when actively diagnosing production incidents.

---

## 6. Complete Production-Hardened JPA Configuration

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate            # Flyway manages DDL; Hibernate validates schema
    open-in-view: false             # Mandatory: frees DB connections as soon as transaction commits
    properties:
      hibernate:
        format_sql: false           # Clean log output
        default_batch_fetch_size: 30 # Solves N+1 query problem globally
        jdbc:
          batch_size: 25            # Statement batching
        order_inserts: true         # Optimizes batching order
        order_updates: true         # Optimizes batching order
        generate_statistics: false  # Enable only in test/staging for performance profiling
```
