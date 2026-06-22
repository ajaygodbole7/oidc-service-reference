package com.example.commerce.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.commerce.order.domain.CartId;
import com.example.commerce.order.domain.Money;
import com.example.commerce.order.domain.Order;
import com.example.commerce.order.domain.OrderId;
import com.example.commerce.order.domain.OrderLine;
import com.example.commerce.order.domain.OrderRepository;
import com.example.commerce.order.domain.ProductId;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.QueryCount;
import net.ttddyy.dsproxy.QueryCountHolder;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * N+1 guard. The order aggregate owns a {@code List<OrderLine>} child collection; a naive mapping
 * would issue one query per line. datasource-proxy counts the SQL actually run so we can assert that
 * loading a multi-line order stays a bounded number of statements (root + child collection), and
 * does NOT grow with the line count. WRITE-only here — the orchestrator runs Testcontainers.
 */
// Default (MOCK) web environment so the full context wires: order-service runs as a web application
// and the starter's TsidGenerator (needed by orderApplicationService) is @ConditionalOnWebApplication,
// which WebEnvironment.NONE would skip. MOCK does not bind a port.
@Testcontainers
@SpringBootTest
@Import(OrderQueryCountTest.ProxyConfig.class)
// "test" is in the SecretSentinelGuard local-profile allow-list, so the committed dev-default
// secrets downgrade to a WARN instead of failing the context boot.
@ActiveProfiles("test")
class OrderQueryCountTest {

  @Container
  @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:18.4"));

  @Autowired
  private OrderRepository orderRepository;

  @BeforeEach
  void resetCounts() {
    QueryCountHolder.clear();
  }

  @AfterEach
  void clearCounts() {
    QueryCountHolder.clear();
  }

  @Test
  void loadingAMultiLineOrderStaysBoundedAndDoesNotScaleWithLineCount() {
    orderRepository.save(threeLineOrder("qc-order-1"));
    QueryCountHolder.clear();

    Order reloaded = orderRepository.findById(new OrderId("qc-order-1")).orElseThrow();

    assertThat(reloaded.lines()).hasSize(3);
    QueryCount counts = QueryCountHolder.getGrandTotal();
    // Root + child-collection load. Spring Data JDBC does this in 2 selects regardless of line
    // count; allow a small ceiling but assert it is NOT 1-per-line (which would be 4+ for 3 lines).
    assertThat(counts.getSelect())
        .as("selects to load a 3-line order")
        .isLessThanOrEqualTo(2);
  }

  private static Order threeLineOrder(String id) {
    return Order.confirmed(
        new OrderId(id),
        "alice",
        new CartId("alice-cart"),
        java.util.List.of(
            new OrderLine(new ProductId("6801HWW000000"), 1, Money.usd("12.50")),
            new OrderLine(new ProductId("6801HWW000001"), 2, Money.usd("8.00")),
            new OrderLine(new ProductId("6801HWW000002"), 1, Money.usd("48.00"))),
        Money.usd("76.50"),
        "auth-" + id,
        java.time.Instant.parse("2026-06-20T00:00:00Z"));
  }

  /**
   * Wraps the autowired DataSource in a query-counting proxy. A BeanPostProcessor is used so the
   * real Testcontainers-backed DataSource is decorated after Boot builds it, without replacing the
   * connection-details wiring.
   */
  @TestConfiguration(proxyBeanMethods = false)
  static class ProxyConfig {

    @Bean
    static BeanPostProcessor dataSourceCountingProxy() {
      return new BeanPostProcessor() {
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
          if (bean instanceof DataSource dataSource) {
            return ProxyDataSourceBuilder.create(dataSource)
                .name("order-query-count")
                .countQuery()
                .build();
          }
          return bean;
        }
      };
    }
  }
}
