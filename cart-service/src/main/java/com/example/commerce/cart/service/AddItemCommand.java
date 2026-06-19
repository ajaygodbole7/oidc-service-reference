package com.example.commerce.cart.service;

import com.example.commerce.cart.domain.Money;
import com.example.commerce.cart.domain.ProductId;
import com.example.commerce.cart.domain.Quantity;

public record AddItemCommand(ProductId productId, Quantity quantity, Money unitPrice) {
}
