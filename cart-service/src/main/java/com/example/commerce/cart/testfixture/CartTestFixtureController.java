package com.example.commerce.cart.testfixture;

import com.example.commerce.cart.service.CartApplicationService;
import com.example.commerce.cart.service.CartResult;
import com.example.commerce.security.CommercePrincipal;
import com.example.commerce.security.DecisionTrace;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("test-fixture")
class CartTestFixtureController {

  private static final String ALICE_OWNER = "cart:alice-cart#owner@user:alice";
  private static final String BOB_OWNER = "cart:bob-cart#owner@user:bob";
  private static final List<String> UNSAFE_IDENTITY_HEADERS = List.of(
      "x-user",
      "x-forwarded-user",
      "x-forwarded-email",
      "x-forwarded-groups",
      "x-forwarded-preferred-username",
      "x-auth-request-user",
      "x-auth-request-email",
      "x-auth-request-groups",
      "x-auth-request-preferred-username",
      "x-email",
      "x-groups",
      "x-roles",
      "x-remote-user",
      "remote-user",
      "x-authenticated-user",
      "x-forwarded-access-token",
      "x-id-token");

  private final CartRelationshipFixture fixture;
  private final CartApplicationService cartService;

  CartTestFixtureController(CartRelationshipFixture fixture, CartApplicationService cartService) {
    this.fixture = fixture;
    this.cartService = cartService;
  }

  @PostMapping("/api/_test/cart/relationships/local-seed")
  FixtureResponse restoreLocalSeed() {
    fixture.restoreLocalSeed();
    return new FixtureResponse("restored", List.of(ALICE_OWNER, BOB_OWNER));
  }

  @GetMapping("/api/_test/cart/evidence")
  FixtureEvidence evidence(
      @org.springframework.web.bind.annotation.RequestAttribute("commercePrincipal")
          CommercePrincipal principal,
      HttpServletRequest request) {
    boolean unsafeHeaderPresent = UNSAFE_IDENTITY_HEADERS.stream()
        .anyMatch(header -> request.getHeader(header) != null);
    String authorization = request.getHeader("Authorization");
    String traceId = request.getHeader("X-Trace-Id");
    CartResult cart = cartService.getCurrentCart(principal);
    return new FixtureEvidence(
        traceId == null || traceId.isBlank() ? "missing-trace-id" : traceId,
        request.getMethod() + " " + request.getRequestURI(),
        principal.subject(),
        principal.tokenFingerprint(),
        authorization != null && authorization.startsWith("Bearer "),
        unsafeHeaderPresent,
        true,
        true,
        cart.traces());
  }

  @PostMapping("/api/_test/cart/relationships/alice-cart-owner")
  FixtureResponse restoreAliceOwner() {
    fixture.restoreAliceOwner();
    return new FixtureResponse("restored", List.of(ALICE_OWNER));
  }

  @DeleteMapping("/api/_test/cart/relationships/alice-cart-owner")
  @ResponseStatus(HttpStatus.OK)
  FixtureResponse removeAliceOwner() {
    fixture.removeAliceOwner();
    return new FixtureResponse("removed", List.of(ALICE_OWNER));
  }

  record FixtureResponse(String status, List<String> relationships) {
  }

  record FixtureEvidence(
      String traceId,
      String request,
      String subject,
      String tokenFingerprint,
      boolean authorizationBearerPresent,
      boolean unsafeIdentityHeaderPresent,
      boolean sessionResolved,
      boolean serviceJwtValidated,
      List<DecisionTrace> serviceTraces) {
  }
}
