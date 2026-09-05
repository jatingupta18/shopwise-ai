package com.jatin.ai_shopping_agent.controller;

import com.jatin.ai_shopping_agent.dto.AddToCartRequest;
import com.jatin.ai_shopping_agent.dto.CartResponse;
import com.jatin.ai_shopping_agent.dto.UpdateCartItemRequest;
import com.jatin.ai_shopping_agent.service.CartService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    public static final String CART_COOKIE = "shopwise_cart";

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse getCart(
            @CookieValue(value = CART_COOKIE, required = false) String guestToken,
            HttpServletResponse response) {
        return cartService.getCart(ensureGuestToken(guestToken, response));
    }

    @PostMapping("/items")
    public CartResponse addItem(
            @CookieValue(value = CART_COOKIE, required = false) String guestToken,
            @Valid @RequestBody AddToCartRequest request,
            HttpServletResponse response) {
        return cartService.addProduct(ensureGuestToken(guestToken, response), request.productId(), request.resolvedQuantity());
    }

    @PatchMapping("/items/{itemId}")
    public CartResponse updateItem(
            @CookieValue(value = CART_COOKIE, required = false) String guestToken,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request,
            HttpServletResponse response) {
        return cartService.updateQuantity(ensureGuestToken(guestToken, response), itemId, request.quantity());
    }

    @DeleteMapping("/items/{itemId}")
    public CartResponse removeItem(
            @CookieValue(value = CART_COOKIE, required = false) String guestToken,
            @PathVariable Long itemId,
            HttpServletResponse response) {
        return cartService.removeItem(ensureGuestToken(guestToken, response), itemId);
    }

    @DeleteMapping
    public CartResponse clearCart(
            @CookieValue(value = CART_COOKIE, required = false) String guestToken,
            HttpServletResponse response) {
        return cartService.clearCart(ensureGuestToken(guestToken, response));
    }

    private String ensureGuestToken(String guestToken, HttpServletResponse response) {
        if (guestToken != null && !guestToken.isBlank()) {
            return guestToken;
        }
        String token = UUID.randomUUID().toString();
        ResponseCookie cookie = ResponseCookie.from(CART_COOKIE, token)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofDays(30))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return token;
    }
}
