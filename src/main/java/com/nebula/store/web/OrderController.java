package com.nebula.store.web;

import com.nebula.store.service.OrderService;
import com.nebula.store.web.dto.CheckoutRequest;
import com.nebula.store.web.dto.OrderDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Checkout and order lookup.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Places an order for the cart identified by {@code X-Cart-Id} and returns
     * {@code 201 Created} with a {@code Location} pointing at the new order.
     */
    @PostMapping
    public ResponseEntity<OrderDto> checkout(
            @RequestHeader(name = ApiHeaders.CART_ID, required = false) String cartId,
            @Valid @RequestBody CheckoutRequest request) {
        OrderDto order = orderService.checkout(cartId, request);
        return ResponseEntity.created(URI.create("/api/orders/" + order.code())).body(order);
    }

    @GetMapping("/{code}")
    public OrderDto getByCode(@PathVariable String code) {
        return orderService.get(code);
    }
}
