package com.jatin.ai_shopping_agent.ai;

import com.jatin.ai_shopping_agent.dto.ChatResponse;
import com.jatin.ai_shopping_agent.dto.ProductSummary;
import com.jatin.ai_shopping_agent.service.CartService;
import com.jatin.ai_shopping_agent.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShoppingAgentServiceTest {

    @Mock
    private ObjectProvider<ChatModel> chatModelProvider;

    @Mock
    private ProductService productService;

    @Mock
    private CartService cartService;

    @Mock
    private DatabaseShoppingFallback databaseFallback;

    private ShoppingAgentService shoppingAgentService;

    @BeforeEach
    void setUp() {
        shoppingAgentService = new ShoppingAgentService(
                chatModelProvider,
                new ProductTools(productService, cartService),
                databaseFallback,
                true,
                "test"
        );
    }

    @Test
    void chat_WhenNoChatModel_ReturnsFallbackResponse() {
        when(chatModelProvider.getIfUnique()).thenReturn(null);
        when(databaseFallback.findCandidates(any()))
                .thenReturn(List.of(new ProductSummary(1L, "Test Product", "Description", new BigDecimal("100"), "Electronics")));
        when(databaseFallback.requestedComparisonCount(any())).thenReturn(java.util.OptionalInt.empty());
        when(databaseFallback.needsComparisonShortCircuit(any(), any())).thenReturn(false);
        when(databaseFallback.fallbackAnswer(any())).thenReturn("Fallback answer");

        ChatResponse response = shoppingAgentService.chat("test message");

        assertThat(response.answer()).isEqualTo("Fallback answer");
        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.provider()).isEqualTo("database-fallback");
    }

    @Test
    void chat_WithGuestToken_IncludesTokenInFallbackResponse() {
        when(chatModelProvider.getIfUnique()).thenReturn(null);
        when(databaseFallback.findCandidates(any()))
                .thenReturn(List.of());
        when(databaseFallback.requestedComparisonCount(any())).thenReturn(java.util.OptionalInt.empty());
        when(databaseFallback.needsComparisonShortCircuit(any(), any())).thenReturn(false);
        when(databaseFallback.fallbackAnswer(any())).thenReturn("Fallback answer");

        ChatResponse response = shoppingAgentService.chat("add laptop to cart", "guest-123");

        assertThat(response.answer()).isEqualTo("Fallback answer");
        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.provider()).isEqualTo("database-fallback");
    }
}
