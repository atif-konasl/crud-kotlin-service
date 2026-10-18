package com.example.demo

import com.example.demo.application.port.out.ExternalClientPort
import com.example.demo.application.port.out.ResourceRepositoryPort
import com.ninjasquad.springmockk.MockkBean
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class DemoApplicationTests {

    @MockkBean
    private lateinit var repositoryPort: ResourceRepositoryPort

    @MockkBean
    private lateinit var externalClientPort: ExternalClientPort

    @Test
    fun contextLoads() {
    }

}
