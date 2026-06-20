package com.example.commerce.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Guards the full Spring context: the focused unit tests construct beans directly and never
 * exercise the application context, so a bean-wiring regression (e.g. depending on a
 * RestClient.Builder bean that order-service does not auto-configure) can pass every unit
 * test yet fail the container on boot. This loads the real context against real Postgres
 * (all other external clients construct lazily) and fails closed if any bean cannot be created.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrderServiceContextTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.4"));

  @Test
  void contextLoads() {
    // Intentionally empty: success is the application context refreshing with every bean wired.
  }
}
