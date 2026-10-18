package com.example.demo.infrastructure.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SpringDataResourceRepository : JpaRepository<ResourceJpaEntity, UUID>
