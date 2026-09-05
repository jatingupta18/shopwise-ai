package com.jatin.ai_shopping_agent.controller;

import com.jatin.ai_shopping_agent.ai.ShoppingAgentService;
import com.jatin.ai_shopping_agent.dto.ChatRequest;
import com.jatin.ai_shopping_agent.dto.ChatResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private static final String CART_COOKIE = "shopwise_cart";
    private final ShoppingAgentService shoppingAgentService;

    public ChatController(ShoppingAgentService shoppingAgentService) {
        this.shoppingAgentService = shoppingAgentService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request,
                                                 @CookieValue(value = CART_COOKIE, required = false) String guestToken,
                                                 HttpServletResponse response) {
        String resolvedToken = ensureGuestToken(guestToken, response);
        ChatResponse chatResponse = shoppingAgentService.chat(request.message(), resolvedToken);
        return ResponseEntity.ok(chatResponse);
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
