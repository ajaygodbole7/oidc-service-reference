package com.example.commerce.cart.domain;

import java.util.Optional;

public interface CartRepository {

  Optional<Cart> findById(CartId cartId);

  Optional<Cart> findByOwnerSub(String ownerSub);

  Cart save(Cart cart);
}
