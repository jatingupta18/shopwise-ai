package com.jatin.ai_shopping_agent.controller;

import com.jatin.ai_shopping_agent.dto.CheckoutRequest;
import com.jatin.ai_shopping_agent.dto.OrderResponse;
import com.jatin.ai_shopping_agent.exception.ResourceNotFoundException;
import com.jatin.ai_shopping_agent.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    public OrderResponse checkout(
            @CookieValue(value = "shopwise_cart", required = false) String guestToken,
            @Valid @RequestBody CheckoutRequest checkoutRequest) {
        if (guestToken == null || guestToken.isBlank()) {
            throw new ResourceNotFoundException("Guest token is required for checkout");
        }
        return orderService.createOrder(guestToken, checkoutRequest);
    }

    @GetMapping("/{orderNumber}")
    public OrderResponse getOrderByNumber(@PathVariable String orderNumber) {
        return orderService.getOrderByOrderNumber(orderNumber);
    }

    @GetMapping
    public List<OrderResponse> getOrdersByGuestToken(
            @CookieValue(value = "shopwise_cart", required = false) String guestToken) {
        if (guestToken == null || guestToken.isBlank()) {
            throw new ResourceNotFoundException("Guest token is required to view order history");
        }
        return orderService.getOrdersByGuestToken(guestToken);
    }
}