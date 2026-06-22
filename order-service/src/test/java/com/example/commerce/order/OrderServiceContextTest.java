package com.example.commerce.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
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
// Default (MOCK) web environment so the loaded context matches order-service's real runtime: it
// runs as a web application, and the starter's web beans (TsidGenerator, TraceIdFilter,
// GlobalExceptionHandler) are @ConditionalOnWebApplication. WebEnvironment.NONE skips those beans,
// so orderApplicationService (which needs TsidGenerator) fails to wire — a context shape production
// never uses. MOCK does not bind a port; it is the same web context the other Testcontainers tests use.
@Testcontainers
@SpringBootTest
// "test" is in the SecretSentinelGuard local-profile allow-list, so the committed dev-default
// secrets downgrade to a WARN instead of failing the context boot (the guard fails closed under
// no/non-local profile).
@ActiveProfiles("test")
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
