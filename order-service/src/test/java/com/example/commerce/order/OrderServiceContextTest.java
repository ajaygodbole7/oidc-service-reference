package com.example.commerce.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Guards the full Spring context: the focused unit tests construct beans directly and never
 * exercise the application context, so a bean-wiring regression (e.g. depending on a
 * RestClient.Builder bean that order-service does not auto-configure) can pass every unit
 * test yet fail the container on boot. This loads the real context offline (all external
 * clients construct lazily) and fails closed if any bean cannot be created.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrderServiceContextTest {

  @Test
  void contextLoads() {
    // Intentionally empty: success is the application context refreshing with every bean wired.
  }
}
