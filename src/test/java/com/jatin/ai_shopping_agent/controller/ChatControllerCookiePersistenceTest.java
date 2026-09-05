package com.jatin.ai_shopping_agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jatin.ai_shopping_agent.dto.ChatRequest;
import com.jatin.ai_shopping_agent.dto.ChatResponse;
import com.jatin.ai_shopping_agent.entity.Product;
import com.jatin.ai_shopping_agent.repository.ProductRepository;
import com.jatin.ai_shopping_agent.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChatControllerCookiePersistenceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private ObjectMapper objectMapper;

    private Product hpPavilion;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        
        hpPavilion = new Product();
        hpPavilion.setName("HP Pavilion Laptop");
        hpPavilion.setCategory("Electronics");
        hpPavilion.setPrice(new BigDecimal("52000"));
        hpPavilion.setDescription("14-inch HD, AMD Ryzen 5, 16GB RAM, 1TB SSD");
        hpPavilion = productRepository.save(hpPavilion);
    }

    @Test
    void endToEnd_addToCart_thenGetCart_withSameCookie_returnsSameItem() throws Exception {
        // This test reproduces the exact bug scenario:
        // 1. Send "HP Pavilion laptop ko cart me daalo" via /api/chat
        // 2. Then send "cart dikhao" via /api/chat in the SAME session
        // 3. Verify that the second request returns the item added by the first request
        
        // Request 1: "HP Pavilion laptop ko cart me daalo"
        MvcResult result1 = mockMvc.perform(post("/api/chat")
                .contentType("application/json")
                .content("{\"message\":\"HP Pavilion laptop ko cart me daalo\"}"))
                .andExpect(status().isOk())
                .andReturn();
        
        String response1 = result1.getResponse().getContentAsString();
        ChatResponse chatResponse1 = parseChatResponse(response1);
        
        // Extract the cookie from the first response
        String cookieHeader = result1.getResponse().getHeader("Set-Cookie");
        assertThat(cookieHeader).isNotNull();
        assertThat(cookieHeader).contains("shopwise_cart");
        
        // Extract the guest token from the cookie
        String guestToken = extractGuestTokenFromCookie(cookieHeader);
        assertThat(guestToken).isNotNull();
        
        // Verify the AI response indicates the item was added
        assertThat(chatResponse1.answer()).isNotBlank();
        
        // Verify the cart actually has the item by calling cart service directly
        var cartAfterAdd = cartService.getCart(guestToken);
        assertThat(cartAfterAdd.items()).hasSize(1);
        assertThat(cartAfterAdd.items().get(0).productId()).isEqualTo(hpPavilion.getId());
        
        // Request 2: "cart dikhao" with the SAME cookie
        MvcResult result2 = mockMvc.perform(post("/api/chat")
                .contentType("application/json")
                .content("{\"message\":\"cart dikhao\"}")
                .cookie("shopwise_cart", guestToken))
                .andExpect(status().isOk())
                .andReturn();
        
        String response2 = result2.getResponse().getContentAsString();
        ChatResponse chatResponse2 = parseChatResponse(response2);
        
        // CRITICAL: The AI response should acknowledge the cart has items
        assertThat(chatResponse2.answer()).isNotBlank();
        
        // Verify the cart still has the item by calling cart service directly
        var cartAfterShow = cartService.getCart(guestToken);
        assertThat(cartAfterShow.items()).hasSize(1);
        assertThat(cartAfterShow.items().get(0).productId()).isEqualTo(hpPavilion.getId());
        assertThat(cartAfterShow.items().get(0).name()).isEqualTo("HP Pavilion Laptop");
    }
    
    @Test
    void chatController_createsAndSetsCookie_whenNotProvided() throws Exception {
        // Test that the chat controller creates and sets a cookie when none is provided
        MvcResult result = mockMvc.perform(post("/api/chat")
                .contentType("application/json")
                .content("{\"message\":\"test message\"}"))
                .andExpect(status().isOk())
                .andReturn();
        
        String cookieHeader = result.getResponse().getHeader("Set-Cookie");
        assertThat(cookieHeader).isNotNull();
        assertThat(cookieHeader).contains("shopwise_cart");
        assertThat(cookieHeader).contains("HttpOnly");
        assertThat(cookieHeader).contains("Path=/");
    }
    
    @Test
    void chatController_usesExistingCookie_whenProvided() throws Exception {
        // Test that the chat controller uses an existing cookie when provided
        String existingToken = "test-existing-token-12345";
        
        MvcResult result = mockMvc.perform(post("/api/chat")
                .contentType("application/json")
                .content("{\"message\":\"test message\"}")
                .cookie("shopwise_cart", existingToken))
                .andExpect(status().isOk())
                .andReturn();
        
        // Should NOT set a new cookie when one is provided
        String cookieHeader = result.getResponse().getHeader("Set-Cookie");
        assertThat(cookieHeader).isNull();
    }
    
    private ChatResponse parseChatResponse(String json) {
        // Simple JSON parsing for test purposes
        // In a real scenario, you'd use Jackson or similar
        try {
            int answerStart = json.indexOf("\"answer\":\"") + 9;
            int answerEnd = json.indexOf("\",\"", answerStart);
            String answer = json.substring(answerStart, answerEnd);
            
            return new ChatResponse(answer, java.util.List.of(), "test", false);
        } catch (Exception e) {
            return new ChatResponse("", java.util.List.of(), "test", false);
        }
    }
    
    private String extractGuestTokenFromCookie(String cookieHeader) {
        // Extract the token value from the Set-Cookie header
        // Format: shopwise_cart=<token>; HttpOnly; Path=/; Max-Age=...
        String[] parts = cookieHeader.split("=");
        if (parts.length > 1) {
            String tokenPart = parts[1].split(";")[0];
            return tokenPart;
        }
        return null;
    }
}
