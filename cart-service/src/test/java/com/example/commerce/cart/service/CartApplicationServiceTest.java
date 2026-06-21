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
import com.example.commerce.security.AuthorizationUnavailableException;
import com.example.commerce.security.CommercePrincipal;
import com.example.commerce.security.DecisionTrace;
import com.example.commerce.security.InMemoryAuthorizationClient;
import com.example.commerce.security.Permission;
import com.example.commerce.security.Relationship;
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
import org.springframework.dao.DuplicateKeyException;

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
  void first_add_for_user_without_cart_provisions_owner_for_authenticated_subject_then_saves() {
    RecordingCartRepository repository = new RecordingCartRepository(List.of());
    RecordingAuthorizationClient authorizationClient = recordingClient(new InMemoryAuthorizationClient());
    CartApplicationService service = service(
        repository,
        authorizationClient,
        () -> new CartId("generated-cart"));

    CartResult result = service.addItem(principal("carol", "cart:write"), addItemCommand());

    assertThat(result.cart().id()).isEqualTo(new CartId("generated-cart"));
    assertThat(result.cart().ownerSub()).isEqualTo("carol");
    assertThat(result.cart().items()).hasSize(1);
    assertThat(authorizationClient.requests()).isEmpty();
    assertThat(authorizationClient.relationshipWrites())
        .containsExactly(new Relationship(
            new ResourceRef("cart", "generated-cart"), "owner", SubjectRef.user("carol")));
    assertThat(repository.saveCount()).isEqualTo(1);
    assertThat(repository.findByOwnerSub("carol")).isPresent();
  }

  @Test
  void first_add_mints_a_thirteen_char_tsid_style_cart_id_from_the_injected_supplier() {
    RecordingCartRepository repository = new RecordingCartRepository(List.of());
    RecordingAuthorizationClient authorizationClient = recordingClient(new InMemoryAuthorizationClient());
    // Stand-in for TsidGenerator.newId(): a fixed-width 13-char Crockford base32 string.
    CartApplicationService service = service(
        repository,
        authorizationClient,
        () -> new CartId("0ABCDEFGHJKMN"));

    CartResult result = service.addItem(principal("carol", "cart:write"), addItemCommand());

    assertThat(result.cart().id().value()).hasSize(13);
    assertThat(result.cart().id()).isEqualTo(new CartId("0ABCDEFGHJKMN"));
    assertThat(repository.findByOwnerSub("carol")).isPresent();
  }

  @Test
  void first_add_re_resolves_existing_cart_when_concurrent_create_raises_duplicate_key() {
    // First save throws DuplicateKeyException (a concurrent first-add won the owner_sub unique);
    // the service must fall through, re-read the existing cart, and add to it.
    Cart concurrentlyCreated = new Cart(new CartId("existing-cart"), "carol", List.of());
    DuplicateKeyOnFirstSaveRepository repository =
        new DuplicateKeyOnFirstSaveRepository(concurrentlyCreated);
    RecordingAuthorizationClient authorizationClient = recordingClient(new InMemoryAuthorizationClient()
        .grant(SubjectRef.user("carol"), new ResourceRef("cart", "existing-cart"), WRITE));
    CartApplicationService service = service(
        repository,
        authorizationClient,
        () -> new CartId("generated-cart"));

    CartResult result = service.addItem(principal("carol", "cart:write"), addItemCommand());

    // Ends up on the concurrently-created cart, not the discarded generated id.
    assertThat(result.cart().id()).isEqualTo(new CartId("existing-cart"));
    assertThat(result.cart().items()).hasSize(1);
    // Two save attempts: the failed create, then the successful add to the existing cart.
    assertThat(repository.saveAttempts()).isEqualTo(2);
    // A resource (write) check ran against the re-resolved existing cart.
    assertThat(authorizationClient.requests())
        .containsExactly(new CheckRequest("user:carol", "cart:existing-cart", "write"));
  }

  @Test
  void first_add_create_path_ignores_attacker_chosen_cart_ids_by_only_accepting_add_item_command() {
    RecordingCartRepository repository = new RecordingCartRepository(List.of());
    RecordingAuthorizationClient authorizationClient = recordingClient(new InMemoryAuthorizationClient());
    CartApplicationService service = service(
        repository,
        authorizationClient,
        () -> new CartId("server-generated-cart"));

    CartResult result = service.addItem(
        principal("alice", "cart:write"),
        new AddItemCommand(new ProductId("bob-cart"), new Quantity(1), Money.usd("12.50")));

    assertThat(result.cart().id()).isEqualTo(new CartId("server-generated-cart"));
    assertThat(authorizationClient.relationshipWrites())
        .containsExactly(new Relationship(
            new ResourceRef("cart", "server-generated-cart"), "owner", SubjectRef.user("alice")));
  }

  @Test
  void first_add_fails_closed_when_relationship_provisioning_fails_and_does_not_save() {
    RecordingCartRepository repository = new RecordingCartRepository(List.of());
    RecordingAuthorizationClient authorizationClient = recordingClient(new AuthorizationClient() {
      @Override
      public AuthorizationDecision check(
          SubjectRef subject, ResourceRef resource, Permission permission) {
        return AuthorizationDecision.deny(DecisionTrace.resource(
            false, subject, resource, permission, "relationship_missing"));
      }

      @Override
      public void writeRelationship(Relationship relationship) {
        throw new AuthorizationUnavailableException("SpiceDB unavailable");
      }
    });
    CartApplicationService service = service(
        repository,
        authorizationClient,
        () -> new CartId("generated-cart"));

    assertThatThrownBy(() -> service.addItem(principal("carol", "cart:write"), addItemCommand()))
        .isInstanceOf(AuthorizationDeniedException.class)
        .hasMessageContaining("unavailable");

    assertThat(authorizationClient.relationshipWrites())
        .containsExactly(new Relationship(
            new ResourceRef("cart", "generated-cart"), "owner", SubjectRef.user("carol")));
    assertThat(repository.saveCount()).isZero();
    assertThat(repository.findByOwnerSub("carol")).isEmpty();
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
    return service(repository, authorizationClient, () -> new CartId("generated-cart"));
  }

  private static CartApplicationService service(
      CartRepository repository,
      AuthorizationClient authorizationClient,
      java.util.function.Supplier<CartId> cartIdGenerator) {
    return new CartApplicationService(
        repository,
        new ScopeAuthorizer(),
        new ResourceAuthorizer(authorizationClient),
        cartIdGenerator);
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
    private final List<Relationship> relationshipWrites = new ArrayList<>();

    private RecordingAuthorizationClient(AuthorizationClient delegate) {
      this.delegate = delegate;
    }

    @Override
    public AuthorizationDecision check(SubjectRef subject, ResourceRef resource, Permission permission) {
      requests.add(new CheckRequest(subject.toString(), resource.toString(), permission.value()));
      return delegate.check(subject, resource, permission);
    }

    @Override
    public void writeRelationship(Relationship relationship) {
      relationshipWrites.add(relationship);
      delegate.writeRelationship(relationship);
    }

    @Override
    public void deleteRelationship(Relationship relationship) {
      delegate.deleteRelationship(relationship);
    }

    List<CheckRequest> requests() {
      return List.copyOf(requests);
    }

    List<Relationship> relationshipWrites() {
      return List.copyOf(relationshipWrites);
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

  /**
   * Simulates the concurrent-first-add race: {@code findByOwnerSub} is empty until a save is
   * attempted, the first {@code save} throws {@link DuplicateKeyException} (a competing thread already
   * created the owner's cart on the {@code owner_sub} unique), and from then on the
   * concurrently-created cart is visible so the service can re-resolve and add to it.
   */
  private static final class DuplicateKeyOnFirstSaveRepository implements CartRepository {

    private final Cart concurrentlyCreated;
    private boolean firstSaveAttempted;
    private @org.jspecify.annotations.Nullable Cart stored;
    private int saveAttempts;

    private DuplicateKeyOnFirstSaveRepository(Cart concurrentlyCreated) {
      this.concurrentlyCreated = concurrentlyCreated;
    }

    @Override
    public Optional<Cart> findById(CartId cartId) {
      return Optional.ofNullable(stored).filter(cart -> cart.id().equals(cartId)).map(this::copy);
    }

    @Override
    public Optional<Cart> findByOwnerSub(String ownerSub) {
      return Optional.ofNullable(stored)
          .filter(cart -> cart.ownerSub().equals(ownerSub))
          .map(this::copy);
    }

    @Override
    public Cart save(Cart cart) {
      saveAttempts++;
      if (!firstSaveAttempted) {
        firstSaveAttempted = true;
        // The competing create becomes visible exactly as it would after the unique violation.
        stored = copy(concurrentlyCreated);
        throw new DuplicateKeyException("owner_sub unique violation");
      }
      stored = copy(cart);
      return copy(cart);
    }

    int saveAttempts() {
      return saveAttempts;
    }

    private Cart copy(Cart cart) {
      return new Cart(cart.id(), cart.ownerSub(), cart.items());
    }
  }
}
