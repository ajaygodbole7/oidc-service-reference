package com.example.commerce.order.integration;

import java.time.Duration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds the order-service service-to-service {@link RestClient} with finite transport timeouts.
 *
 * <p>A bare {@code RestClient.create()} inherits infinite connect/read timeouts, so a hung Keycloak
 * (token endpoint) or payment-service would pin the checkout request thread indefinitely; under load
 * that drains the request pool from one stalled downstream. Mirrors auth-service's {@code IdpHttp}:
 * the timeouts are configuration knobs (see {@code OrderProperties.Payment}), not constants. A read
 * timeout surfaces as a {@code ResourceAccessException}, which {@link HttpPaymentClient} classifies
 * as a {@link TransientPaymentException}.
 */
public final class OrderHttp {

  private OrderHttp() {
  }

  public static RestClient paymentClient(Duration connectTimeout, Duration readTimeout) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeout);
    factory.setReadTimeout(readTimeout);
    ClientHttpRequestFactory requestFactory = factory;
    return RestClient.builder().requestFactory(requestFactory).build();
  }
}
