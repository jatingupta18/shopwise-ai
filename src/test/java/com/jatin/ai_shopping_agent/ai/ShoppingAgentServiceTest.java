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
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

    @Mock
    private DatabaseCartFallback databaseCartFallback;

    @Mock
    private DatabaseProductFallback databaseProductFallback;

    private ShoppingAgentService shoppingAgentService;

    @BeforeEach
    void setUp() {
        shoppingAgentService = new ShoppingAgentService(
                chatModelProvider,
                new ProductTools(productService, cartService),
                databaseFallback,
                databaseCartFallback,
                databaseProductFallback,
                true,
                "test"
        );
        when(databaseCartFallback.handle(any(), any())).thenReturn(Optional.empty());
        when(databaseProductFallback.handle(any())).thenReturn(Optional.empty());
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

    @Test
    void chat_WhenChatModelResponds_ReturnsNormalAiResponse() {
        ChatModel chatModel = org.mockito.Mockito.mock(ChatModel.class);
        when(chatModelProvider.getIfUnique()).thenReturn(chatModel);
        when(chatModel.getOptions()).thenReturn(OllamaChatOptions.builder().build());
        when(databaseFallback.findCandidates(any()))
                .thenReturn(List.of(new ProductSummary(1L, "Budget Laptop", "Catalog laptop", new BigDecimal("55000"), "Electronics")));
        when(databaseFallback.requestedComparisonCount(any())).thenReturn(java.util.OptionalInt.empty());
        when(databaseFallback.needsComparisonShortCircuit(any(), any())).thenReturn(false);
        when(chatModel.call(any(Prompt.class))).thenReturn(new org.springframework.ai.chat.model.ChatResponse(
                List.of(new Generation(new AssistantMessage("The Budget Laptop is within your budget.")))));

        ShoppingAgentService service = new ShoppingAgentService(
                chatModelProvider, new ProductTools(productService, cartService), databaseFallback, databaseCartFallback, databaseProductFallback, true, "ollama");

        com.jatin.ai_shopping_agent.dto.ChatResponse response = service.chat("I need a laptop under 60000");

        assertThat(response.answer()).isEqualTo("The Budget Laptop is within your budget.");
        assertThat(response.products()).extracting(ProductSummary::name).containsExactly("Budget Laptop");
        assertThat(response.provider()).isEqualTo("ollama");
        assertThat(response.fallbackUsed()).isFalse();
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    void chat_WhenChatModelIsUnavailable_ReturnsDatabaseFallback() {
        ChatModel chatModel = org.mockito.Mockito.mock(ChatModel.class);
        when(chatModelProvider.getIfUnique()).thenReturn(chatModel);
        when(chatModel.getOptions()).thenReturn(OllamaChatOptions.builder().build());
        when(databaseFallback.findCandidates(any()))
                .thenReturn(List.of(new ProductSummary(1L, "Budget Laptop", "Catalog laptop", new BigDecimal("55000"), "Electronics")));
        when(databaseFallback.requestedComparisonCount(any())).thenReturn(java.util.OptionalInt.empty());
        when(databaseFallback.needsComparisonShortCircuit(any(), any())).thenReturn(false);
        when(databaseFallback.fallbackAnswer(any())).thenReturn("Database fallback answer");
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Connection refused"));

        ShoppingAgentService service = new ShoppingAgentService(
                chatModelProvider, new ProductTools(productService, cartService), databaseFallback, databaseCartFallback, databaseProductFallback, true, "ollama");

        com.jatin.ai_shopping_agent.dto.ChatResponse response = service.chat("I need a laptop under 60000");

        assertThat(response.answer()).isEqualTo("Database fallback answer");
        assertThat(response.products()).extracting(ProductSummary::name).containsExactly("Budget Laptop");
        assertThat(response.provider()).isEqualTo("database-fallback");
        assertThat(response.fallbackUsed()).isTrue();
        verify(chatModel).call(any(Prompt.class));
    }
}
