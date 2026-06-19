package com.example.commerce.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commerce.cart.domain.Cart;
import com.example.commerce.cart.domain.CartId;
import com.example.commerce.cart.domain.CartRepository;
import com.example.commerce.cart.domain.Money;
import com.example.commerce.cart.domain.ProductId;
import com.example.commerce.cart.domain.Quantity;
import com.example.commerce.security.AuthorizationClient;
import com.example.commerce.security.AuthorizationDecision;
import com.example.commerce.security.AuthorizationDeniedException;
import com.example.commerce.security.CommercePrincipal;
import com.example.commerce.security.DecisionTrace;
import com.example.commerce.security.InMemoryAuthorizationClient;
import com.example.commerce.security.Permission;
import com.example.commerce.security.ResourceAuthorizer;
import com.example.commerce.security.ResourceRef;
import com.example.commerce.security.ScopeAuthorizer;
import com.example.commerce.security.SubjectRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CartApplicationServiceTest {

  private static final ResourceRef ALICE_CART = new ResourceRef("cart", "alice-cart");
  private static final Permission READ = new Permission("read");
  private static final Permission WRITE = new Permission("write");

  @Test
  void get_current_cart_resolves_server_side_but_still_checks_resource_authorizer() {
    RecordingAuthorizationClient authorizationClient = authorizationClientWithAliceCartGrants();
    CartApplicationService service = service(authorizationClient);

    CartResult result = service.getCurrentCart(principal("alice", "cart:read"));

    assertThat(result.cart().id()).isEqualTo(new CartId("alice-cart"));
    assertThat(authorizationClient.requests())
        .containsExactly(new CheckRequest("user:alice", "cart:alice-cart", "read"));
    assertThat(result.traces())
        .extracting(DecisionTrace::gate)
        .containsExactly("scope", "resource");
  }

  @Test
  void alice_with_scope_cannot_read_bob_cart_when_resource_authorizer_denies() {
    RecordingAuthorizationClient authorizationClient = authorizationClientWithAliceCartGrants();
    CartApplicationService service = service(authorizationClient);

    assertThatThrownBy(() -> service.getCart(principal("alice", "cart:read"), new CartId("bob-cart")))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasMessageContaining("resource authorization denied")
        .extracting("trace")
        .satisfies(trace -> {
          DecisionTrace decision = (DecisionTrace) trace;
          assertThat(decision.gate()).isEqualTo("resource");
          assertThat(decision.allowed()).isFalse();
          assertThat(decision.reason()).isEqualTo("relationship_missing");
          assertThat(decision.evidence()).containsEntry("resource", "cart:bob-cart");
        });
    assertThat(authorizationClient.requests())
        .containsExactly(new CheckRequest("user:alice", "cart:bob-cart", "read"));
  }

  @Test
  void relationship_without_scope_denies_before_resource_check() {
    RecordingAuthorizationClient authorizationClient = authorizationClientWithAliceCartGrants();
    CartApplicationService service = service(authorizationClient);

    assertThatThrownBy(() -> service.getCurrentCart(principal("alice")))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasMessageContaining("missing required scope cart:read")
        .extracting("trace")
        .satisfies(trace -> {
          DecisionTrace decision = (DecisionTrace) trace;
          assertThat(decision.gate()).isEqualTo("scope");
          assertThat(decision.allowed()).isFalse();
          assertThat(decision.reason()).isEqualTo("scope_missing");
        });
    assertThat(authorizationClient.requests()).isEmpty();
  }

  @Test
  void add_item_without_write_scope_denies_before_resource_check_and_save() {
    RecordingCartRepository repository = repository();
    RecordingAuthorizationClient authorizationClient = authorizationClientWithAliceCartGrants();
    CartApplicationService service = service(repository, authorizationClient);

    assertThatThrownBy(() -> service.addItem(principal("alice"), addItemCommand()))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasMessageContaining("missing required scope cart:write");

    assertThat(authorizationClient.requests()).isEmpty();
    assertThat(repository.saveCount()).isZero();
    assertThat(repository.findById(new CartId("alice-cart")).orElseThrow().items()).isEmpty();
  }

  @Test
  void add_item_with_write_scope_but_denied_relationship_does_not_save() {
    RecordingCartRepository repository = repository();
    RecordingAuthorizationClient authorizationClient = recordingClient(new InMemoryAuthorizationClient());
    CartApplicationService service = service(repository, authorizationClient);

    assertThatThrownBy(() -> service.addItem(principal("alice", "cart:write"), addItemCommand()))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasMessageContaining("resource authorization denied");

    assertThat(authorizationClient.requests())
        .containsExactly(new CheckRequest("user:alice", "cart:alice-cart", "write"));
    assertThat(repository.saveCount()).isZero();
    assertThat(repository.findById(new CartId("alice-cart")).orElseThrow().items()).isEmpty();
  }

  @Test
  void remove_item_without_write_scope_denies_before_resource_check_and_save() {
    RecordingCartRepository repository = repositoryWithAliceItem();
    RecordingAuthorizationClient authorizationClient = authorizationClientWithAliceCartGrants();
    CartApplicationService service = service(repository, authorizationClient);

    assertThatThrownBy(() -> service.removeItem(principal("alice"), new ProductId("sku-1")))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasMessageContaining("missing required scope cart:write");

    assertThat(authorizationClient.requests()).isEmpty();
    assertThat(repository.saveCount()).isZero();
    assertThat(repository.findById(new CartId("alice-cart")).orElseThrow().items()).hasSize(1);
  }

  @Test
  void remove_item_with_write_scope_but_denied_relationship_does_not_save() {
    RecordingCartRepository repository = repositoryWithAliceItem();
    RecordingAuthorizationClient authorizationClient = recordingClient(new InMemoryAuthorizationClient());
    CartApplicationService service = service(repository, authorizationClient);

    assertThatThrownBy(() -> service.removeItem(principal("alice", "cart:write"), new ProductId("sku-1")))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasMessageContaining("resource authorization denied");

    assertThat(authorizationClient.requests())
        .containsExactly(new CheckRequest("user:alice", "cart:alice-cart", "write"));
    assertThat(repository.saveCount()).isZero();
    assertThat(repository.findById(new CartId("alice-cart")).orElseThrow().items()).hasSize(1);
  }

  private static CartApplicationService service(AuthorizationClient authorizationClient) {
    return service(repository(), authorizationClient);
  }

  private static CartApplicationService service(
      CartRepository repository, AuthorizationClient authorizationClient) {
    return new CartApplicationService(
        repository,
        new ScopeAuthorizer(),
        new ResourceAuthorizer(authorizationClient));
  }

  private static CommercePrincipal principal(String subject, String... scopes) {
    return new CommercePrincipal(subject, Set.of(scopes), "fingerprint-" + subject);
  }

  private static AddItemCommand addItemCommand() {
    return new AddItemCommand(new ProductId("sku-1"), new Quantity(1), Money.usd("12.50"));
  }

  private static RecordingCartRepository repository() {
    return new RecordingCartRepository(List.of(
        new Cart(new CartId("alice-cart"), "alice", List.of()),
        new Cart(new CartId("bob-cart"), "bob", List.of())));
  }

  private static RecordingCartRepository repositoryWithAliceItem() {
    Cart aliceCart = new Cart(new CartId("alice-cart"), "alice", List.of());
    aliceCart.addItem(new ProductId("sku-1"), new Quantity(1), Money.usd("12.50"));
    return new RecordingCartRepository(List.of(
        aliceCart,
        new Cart(new CartId("bob-cart"), "bob", List.of())));
  }

  private static RecordingAuthorizationClient authorizationClientWithAliceCartGrants() {
    return recordingClient(new InMemoryAuthorizationClient()
        .grant(SubjectRef.user("alice"), ALICE_CART, READ)
        .grant(SubjectRef.user("alice"), ALICE_CART, WRITE));
  }

  private static RecordingAuthorizationClient recordingClient(AuthorizationClient delegate) {
    return new RecordingAuthorizationClient(delegate);
  }

  private record CheckRequest(String subject, String resource, String permission) {
  }

  private static final class RecordingAuthorizationClient implements AuthorizationClient {

    private final AuthorizationClient delegate;
    private final List<CheckRequest> requests = new ArrayList<>();

    private RecordingAuthorizationClient(AuthorizationClient delegate) {
      this.delegate = delegate;
    }

    @Override
    public AuthorizationDecision check(SubjectRef subject, ResourceRef resource, Permission permission) {
      requests.add(new CheckRequest(subject.toString(), resource.toString(), permission.value()));
      return delegate.check(subject, resource, permission);
    }

    List<CheckRequest> requests() {
      return List.copyOf(requests);
    }
  }

  private static final class RecordingCartRepository implements CartRepository {

    private final Map<CartId, Cart> carts = new HashMap<>();
    private int saveCount;

    private RecordingCartRepository(List<Cart> initialCarts) {
      initialCarts.forEach(cart -> carts.put(cart.id(), copy(cart)));
    }

    @Override
    public Optional<Cart> findById(CartId cartId) {
      return Optional.ofNullable(carts.get(cartId)).map(RecordingCartRepository::copy);
    }

    @Override
    public Optional<Cart> findByOwnerSub(String ownerSub) {
      return carts.values().stream()
          .filter(cart -> cart.ownerSub().equals(ownerSub))
          .findFirst()
          .map(RecordingCartRepository::copy);
    }

    @Override
    public Cart save(Cart cart) {
      saveCount++;
      carts.put(cart.id(), copy(cart));
      return copy(cart);
    }

    int saveCount() {
      return saveCount;
    }

    private static Cart copy(Cart cart) {
      return new Cart(cart.id(), cart.ownerSub(), cart.items());
    }
  }
}
