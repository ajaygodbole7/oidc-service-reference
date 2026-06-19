package com.example.commerce.cart.service;

import com.example.commerce.cart.domain.Cart;
import com.example.commerce.cart.domain.CartId;
import com.example.commerce.cart.domain.CartRepository;
import com.example.commerce.cart.domain.ProductId;
import com.example.commerce.security.CommercePrincipal;
import com.example.commerce.security.DecisionTrace;
import com.example.commerce.security.Permission;
import com.example.commerce.security.ResourceAuthorizer;
import com.example.commerce.security.ResourceRef;
import com.example.commerce.security.ScopeAuthorizer;
import java.util.List;

public final class CartApplicationService {

  private static final String CART_READ = "cart:read";
  private static final String CART_WRITE = "cart:write";
  private static final Permission READ = new Permission("read");
  private static final Permission WRITE = new Permission("write");

  private final CartRepository repository;
  private final ScopeAuthorizer scopeAuthorizer;
  private final ResourceAuthorizer resourceAuthorizer;

  public CartApplicationService(
      CartRepository repository,
      ScopeAuthorizer scopeAuthorizer,
      ResourceAuthorizer resourceAuthorizer) {
    this.repository = repository;
    this.scopeAuthorizer = scopeAuthorizer;
    this.resourceAuthorizer = resourceAuthorizer;
  }

  public CartResult getCurrentCart(CommercePrincipal principal) {
    DecisionTrace scopeTrace = scopeAuthorizer.requireScope(principal, CART_READ);
    Cart cart = repository.findByOwnerSub(principal.subject())
        .orElseThrow(() -> new CartNotFoundException("cart not found for current user"));
    DecisionTrace resourceTrace = resourceAuthorizer.requireAllowed(principal, resource(cart.id()), READ);
    return new CartResult(cart, List.of(scopeTrace, resourceTrace));
  }

  public CartResult getCart(CommercePrincipal principal, CartId cartId) {
    DecisionTrace scopeTrace = scopeAuthorizer.requireScope(principal, CART_READ);
    DecisionTrace resourceTrace = resourceAuthorizer.requireAllowed(principal, resource(cartId), READ);
    Cart cart = repository.findById(cartId)
        .orElseThrow(() -> new CartNotFoundException("cart not found: " + cartId.value()));
    return new CartResult(cart, List.of(scopeTrace, resourceTrace));
  }

  public CartResult addItem(CommercePrincipal principal, AddItemCommand command) {
    DecisionTrace scopeTrace = scopeAuthorizer.requireScope(principal, CART_WRITE);
    Cart cart = repository.findByOwnerSub(principal.subject())
        .orElseThrow(() -> new CartNotFoundException("cart not found for current user"));
    DecisionTrace resourceTrace = resourceAuthorizer.requireAllowed(principal, resource(cart.id()), WRITE);
    cart.addItem(command.productId(), command.quantity(), command.unitPrice());
    repository.save(cart);
    return new CartResult(cart, List.of(scopeTrace, resourceTrace));
  }

  public CartResult removeItem(CommercePrincipal principal, ProductId productId) {
    DecisionTrace scopeTrace = scopeAuthorizer.requireScope(principal, CART_WRITE);
    Cart cart = repository.findByOwnerSub(principal.subject())
        .orElseThrow(() -> new CartNotFoundException("cart not found for current user"));
    DecisionTrace resourceTrace = resourceAuthorizer.requireAllowed(principal, resource(cart.id()), WRITE);
    cart.removeItem(productId);
    repository.save(cart);
    return new CartResult(cart, List.of(scopeTrace, resourceTrace));
  }

  private static ResourceRef resource(CartId cartId) {
    return new ResourceRef("cart", cartId.value());
  }
}
