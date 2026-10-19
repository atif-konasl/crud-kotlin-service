# Kotlin & JPA / Hibernate Pitfalls: The Senior Engineer Guide

While Kotlin provides expressive, safe, and concise syntax, pairing Kotlin with JPA / Hibernate introduces subtle architectural pitfalls. JPA was designed for Java's mutable, open-by-default, JavaBean-centric model. Kotlin defaults to immutability, final-by-default classes, and auto-generated data class methods.

This guide explores the four most dangerous traps when using Kotlin with JPA and how our project avoids them.

---

## Table of Contents
1. [The Kotlin `data class` Anti-Pattern for JPA Entities](#1-the-kotlin-data-class-anti-pattern-for-jpa-entities)
   - [Pitfall A: Broken `equals()` and `hashCode()` in Collections](#pitfall-a-broken-equals-and-hashcode-in-collections)
   - [Pitfall B: Infinite Recursion & `StackOverflowError` in `toString()`](#pitfall-b-infinite-recursion--stackoverflowerror-in-tostring)
   - [Pitfall C: The `copy()` Method and Persistence Context Corruption](#pitfall-c-the-copy-method-and-persistence-context-corruption)
2. [Kotlin Final Classes & The `kotlin-jpa` Compiler Plugin](#2-kotlin-final-classes--the-kotlin-jpa-compiler-plugin)
   - [Why Hibernate Demands `open` Classes](#why-hibernate-demands-open-classes)
   - [The No-Arg Constructor Requirement](#the-no-arg-constructor-requirement)
3. [The Assigned UUID Trap: Double Queries on `save()`](#3-the-assigned-uuid-trap-double-queries-on-save)
   - [Why `save()` calls `merge()` instead of `persist()`](#why-save-calls-merge-instead-of-persist)
   - [The Solution: `Persistable<UUID>`](#the-solution-persistableuuid)
4. [Val vs. Var: Modeling Entity Immutability](#4-val-vs-var-modeling-entity-immutability)
5. [The Gold Standard Kotlin Entity Template](#5-the-gold-standard-kotlin-entity-template)

---

## 1. The Kotlin `data class` Anti-Pattern for JPA Entities

Kotlin developers frequently reach for `data class` by instinct:
```kotlin
// ❌ ANTI-PATTERN: Never use data class for JPA entities!
@Entity
@Table(name = "resources")
data class ResourceJpaEntity(
    @Id val id: UUID,
    var name: String,
    // ...
)
```

While `data class` is ideal for domain models, DTOs, and value objects, using it for JPA entities violates fundamental Hibernate mechanics in three ways:

---

### Pitfall A: Broken `equals()` and `hashCode()` in Collections
A Kotlin `data class` automatically generates `equals()` and `hashCode()` using **every property in the primary constructor**:

```kotlin
// Generated under the hood by Kotlin:
override fun hashCode(): Int = 31 * id.hashCode() + name.hashCode() + status.hashCode()
```

#### What goes wrong:
1. You insert an entity into a Java `HashSet<ResourceJpaEntity>`. The entity is placed in a hash bucket computed from its initial field values.
2. Inside a `@Transactional` method, you update a field: `entity.name = "Updated Name"`.
3. Hibernate tracks the dirty change, but `entity.hashCode()` has now permanently changed!
4. When you later call `set.contains(entity)` or `set.remove(entity)`, Java computes the **new** hash code, looks in the wrong bucket, and reports that the entity is missing! You now have a memory leak and corrupted set behavior.

#### Proxy Equality Issue:
When Hibernate lazy-loads an entity, it creates a ByteBuddy/CGLIB subclass proxy. A `data class` `equals()` method checks `if (other !is ResourceJpaEntity)`, which fails or compares proxy fields that have not been initialized, breaking entity comparison.

---

### Pitfall B: Infinite Recursion & `StackOverflowError` in `toString()`
`data class` auto-generates `toString()` by traversing all fields:
```text
ResourceJpaEntity(id=..., name=..., items=[Item(id=..., resource=ResourceJpaEntity(...))])
```
If you have a bidirectional relationship (`@OneToMany` <-> `@ManyToOne`):
- `Resource.toString()` calls `Item.toString()`.
- `Item.toString()` calls `Resource.toString()`.
- **Result:** Calling `logger.info("Saved: {}", entity)` or viewing the entity in a debugger instantly crashes your JVM with `java.lang.StackOverflowError`!

---

### Pitfall C: The `copy()` Method and Persistence Context Corruption
`data class` generates a `.copy()` method:
```kotlin
val cloned = entity.copy(name = "New Name")
```
- `cloned` is a new Java object in memory with the **same database `@Id`**.
- The Hibernate Persistence Context enforces an absolute invariant: **Exactly one managed Java instance per database identity (ID)**.
- If you pass `cloned` to `entityManager.persist(cloned)` while `entity` is already managed, Hibernate throws:
  ```text
  org.hibernate.NonUniqueObjectException: A different object with the same identifier value was already associated with the session
  ```

---

## 2. Kotlin Final Classes & The `kotlin-jpa` Compiler Plugin

### Why Hibernate Demands `open` Classes
- By default in Kotlin, all classes and methods are **`final`** (closed for inheritance).
- Hibernate requires entity classes and getter/setter methods to be **`open`** so it can dynamically generate runtime subclasses (ByteBuddy proxies) for:
  1. Lazy loading (`FetchType.LAZY`)
  2. Dirty checking optimization
- If an entity is `final`, Hibernate cannot create a proxy. Any association marked `FetchType.LAZY` is forced to load eagerly, causing silent performance degradation.

### The No-Arg Constructor Requirement
JPA specifications require every entity to have a zero-argument constructor (`public` or `protected`) so the `EntityManager` can instantiate objects during reflection when reading rows from JDBC `ResultSet`.

### The Solution in `build.gradle.kts`:
In Kotlin, we solve both issues using the official JetBrains `kotlin-jpa` plugin:

```kotlin
plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0" // Automatically opens @Component, @Service, @Configuration
    kotlin("plugin.jpa") version "2.2.0"    // Automatically opens @Entity and adds no-arg constructors
}
```

- **`all-open`:** Automatically marks all `@Entity`, `@Embeddable`, and `@MappedSuperclass` classes and methods as `open`.
- **`no-arg`:** Synthesizes a zero-argument constructor at the bytecode level without cluttering your clean Kotlin primary constructor.

---

## 3. The Assigned UUID Trap: Double Queries on `save()`

In our service, [`ResourceJpaEntity.kt`](file:///src/main/kotlin/com/example/demo/infrastructure/adapter/out/persistence/ResourceJpaEntity.kt) uses client-assigned UUIDs:

```kotlin
@Entity
@Table(name = "resources")
class ResourceJpaEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,
    // ...
)
```

### Why `save()` Calls `merge()` instead of `persist()`
Spring Data JPA's `SimpleJpaRepository.save()` determines whether to call `persist()` (INSERT) or `merge()` (SELECT then UPDATE/INSERT) using `entityInformation.isNew(entity)`:

```java
// Spring Data JPA logic:
public boolean isNew(T entity) {
    return getId(entity) == null; // ❌ Assumes null ID means new entity!
}
```

Because our domain layer generates the UUID before persisting (`id = UUID.randomUUID()`), the `id` is **never null**.
1. Spring Data JPA concludes the entity already exists in PostgreSQL.
2. It calls `entityManager.merge(entity)`.
3. Hibernate executes an extra `SELECT` statement:
   ```sql
   select r1_0.id from resources r1_0 where r1_0.id = 'c1f7a0b5-...'
   ```
4. Only after finding that no row exists does Hibernate finally execute the `INSERT` statement!

### The Solution: Implementing `Persistable<UUID>`
By implementing `org.springframework.data.domain.Persistable<UUID>`, the entity tells Spring Data JPA directly when it is new:

```kotlin
import org.springframework.data.domain.Persistable
import jakarta.persistence.Transient
import java.util.UUID

@Entity
@Table(name = "resources")
class ResourceJpaEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private val id: UUID,
    // ...
) : Persistable<UUID> {

    @Transient
    private var isNewRecord: Boolean = false

    override fun getId(): UUID = id
    override fun isNew(): Boolean = isNewRecord

    fun markAsNew() {
        this.isNewRecord = true
    }
}
```
When `isNew()` returns `true`, Spring Data JPA calls `entityManager.persist()` directly, **completely eliminating the extra SELECT query**!

---

## 4. Val vs. Var: Modeling Entity Immutability

In Kotlin:
- `val`: Read-only property.
- `var`: Mutable property.

### In JPA Entities:
- **Use `val` for immutable fields:**
  - Database primary key (`@Id val id: UUID`)
  - Creation timestamp (`@Column(updatable = false) val createdAt: Instant`)
  - Auditing created-by user
- **Use `var` for mutable database columns:**
  - `var name: String`
  - `var status: ResourceStatus`
  - `var updatedAt: Instant`
  Hibernate's dirty-checking mechanism requires setters (or field mutation) to update state when entities are managed.

---

## 5. The Gold Standard Kotlin Entity Template

Here is how our project's [`ResourceJpaEntity.kt`](file:///src/main/kotlin/com/example/demo/infrastructure/adapter/out/persistence/ResourceJpaEntity.kt) is structured to follow all best practices:

```kotlin
package com.example.demo.infrastructure.adapter.out.persistence

import com.example.demo.domain.model.ResourceStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "resources")
class ResourceJpaEntity(
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    val id: UUID,

    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "description", nullable = false)
    var description: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: ResourceStatus,

    @Column(name = "external_enrichment_data")
    var externalEnrichmentData: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant,
) {
    // 1. Regular class (NOT a data class)
    // 2. val for immutable fields (id, createdAt)
    // 3. var for mutable business fields
    // 4. Custom proxy-safe equals and hashCode based strictly on ID:

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other !is ResourceJpaEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "ResourceJpaEntity(id=$id, name='$name', status=$status)"
}
```
