package com.example.commerce.cart.web;

import com.example.commerce.cart.domain.CartId;
import com.example.commerce.cart.domain.ProductId;
import com.example.commerce.cart.service.AddItemCommand;
import com.example.commerce.cart.service.CartApplicationService;
import com.example.commerce.cart.service.CartResult;
import com.example.commerce.security.CommercePrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CartController {

  private final CartApplicationService service;

  CartController(CartApplicationService service) {
    this.service = service;
  }

  @GetMapping("/api/cart")
  CartResponse currentCart(@RequestAttribute("commercePrincipal") CommercePrincipal principal) {
    return CartResponse.from(service.getCurrentCart(principal));
  }

  @GetMapping("/api/carts/{cartId}")
  CartResponse cart(
      @RequestAttribute("commercePrincipal") CommercePrincipal principal,
      @PathVariable String cartId) {
    return CartResponse.from(service.getCart(principal, new CartId(cartId)));
  }

  @PostMapping("/api/cart/items")
  @ResponseStatus(HttpStatus.CREATED)
  CartResponse addItem(
      @RequestAttribute("commercePrincipal") CommercePrincipal principal,
      @Valid @RequestBody CartRequest.AddItem request) {
    AddItemCommand command = new AddItemCommand(
        new ProductId(request.productId()),
        request.quantityValue(),
        request.unitPriceValue());
    return CartResponse.from(service.addItem(principal, command));
  }

  @DeleteMapping("/api/cart/items/{productId}")
  CartResponse removeItem(
      @RequestAttribute("commercePrincipal") CommercePrincipal principal,
      @PathVariable String productId) {
    return CartResponse.from(service.removeItem(principal, new ProductId(productId)));
  }
}
