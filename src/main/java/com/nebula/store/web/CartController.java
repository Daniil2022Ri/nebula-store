package com.nebula.store.web;

import com.nebula.store.service.CartService;
import com.nebula.store.web.dto.AddItemRequest;
import com.nebula.store.web.dto.CartDto;
import com.nebula.store.web.dto.UpdateQuantityRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cart endpoints. The cart id travels in the {@code X-Cart-Id} header; when it is missing
 * or malformed the server starts a fresh cart and returns its id in the response body, so
 * the client only has to persist it once.
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartDto view(@RequestHeader(name = ApiHeaders.CART_ID, required = false) String cartId) {
        return cartService.view(cartId);
    }

    @PostMapping("/items")
    public CartDto addItem(@RequestHeader(name = ApiHeaders.CART_ID, required = false) String cartId,
                           @Valid @RequestBody AddItemRequest request) {
        return cartService.addItem(cartId, request.productId(), request.quantity());
    }

    @PutMapping("/items/{productId}")
    public CartDto updateItem(@RequestHeader(name = ApiHeaders.CART_ID, required = false) String cartId,
                              @PathVariable long productId,
                              @Valid @RequestBody UpdateQuantityRequest request) {
        return cartService.setQuantity(cartId, productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public CartDto removeItem(@RequestHeader(name = ApiHeaders.CART_ID, required = false) String cartId,
                              @PathVariable long productId) {
        return cartService.removeItem(cartId, productId);
    }

    @DeleteMapping
    public CartDto clear(@RequestHeader(name = ApiHeaders.CART_ID, required = false) String cartId) {
        return cartService.clear(cartId);
    }
}
