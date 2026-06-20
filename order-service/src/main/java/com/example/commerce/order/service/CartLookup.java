package com.example.commerce.order.service;

import java.util.Optional;

public interface CartLookup {

  Optional<CartSnapshot> findCurrentCartForSubject(String subject);
}
