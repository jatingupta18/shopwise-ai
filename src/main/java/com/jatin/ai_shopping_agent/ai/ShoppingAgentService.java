package com.jatin.ai_shopping_agent.ai;

import com.jatin.ai_shopping_agent.dto.ChatResponse;
import com.jatin.ai_shopping_agent.dto.ProductSummary;
import com.jatin.ai_shopping_agent.exception.AiProviderUnavailableException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.OptionalInt;

@Service
public class ShoppingAgentService {
    private static final String SYSTEM_PROMPT = """
            You are a shopping assistant for this store. You must only discuss products returned by the database catalog context or by the provided product tools.
            Never invent a product, price, specification, availability, or category. If the catalog has no relevant product, say so clearly.
            Use the product tools whenever you need more details, a name search, a category search, budget results, or a comparison.
            Recommendations and comparisons must explicitly be based on returned database fields. Keep answers concise and factual.
            If the customer asks to compare N products but fewer catalog matches exist, compare only the real matches and state the actual count. Never invent a second product to fill the request.

            CART OPERATIONS ARE CRITICAL:
            - When the customer explicitly asks to add a product to cart (e.g., "add to cart", "cart me add karo", "add this product"), you MUST:
              1. First identify the product ID from the catalog candidates or use getProductDetails to get the exact ID
              2. Call the addToCart tool with the guest token, product ID, and quantity (default to 1 if not specified)
              3. Report the result to the customer
            - When the customer asks to view cart (e.g., "show my cart", "mera cart dikhao"), you MUST call the getCart tool with the guest token
            - When the customer asks to remove items (e.g., "remove from cart", "cart se remove karo"), you MUST:
              1. First call getCart to see the current cart contents
              2. Identify the correct ITEM ID (not product ID) from the cart response
              3. Call removeCartItem with the guest token and the ITEM ID (itemId field in cart response)
              4. NEVER use product ID for removeCartItem - always use the itemId from the cart response
            - CRITICAL DISTINCTION: Product ID identifies the product in the catalog. Item ID identifies the specific cart item. They are different numbers.
            - The guest token will be provided in the system prompt if available. If no guest token is provided, inform the customer that cart operations may not work.
            - Always inform the customer of the result of cart operations (success/failure and current cart state).
            - Cart tools require specific parameters: guestToken (string), productId (long), quantity (int), itemId (long).
            - IMPORTANT: For cart operations, you MUST call the actual tools. Do not just say you added something - actually use the addToCart tool.
            """;

    private final ChatClient chatClient;
    private final ProductTools productTools;
    private final DatabaseShoppingFallback databaseFallback;
    private final boolean fallbackEnabled;
    private final String provider;

    public ShoppingAgentService(ObjectProvider<ChatModel> chatModelProvider,
                                ProductTools productTools,
                                DatabaseShoppingFallback databaseFallback,
                                @Value("${app.ai.fallback-enabled:true}") boolean fallbackEnabled,
                                @Value("${app.ai.provider:ollama}") String provider) {
        ChatModel chatModel = chatModelProvider.getIfUnique();
        this.chatClient = chatModel == null ? null : ChatClient.create(chatModel);
        this.productTools = productTools;
        this.databaseFallback = databaseFallback;
        this.fallbackEnabled = fallbackEnabled;
        this.provider = provider;
    }

    public ChatResponse chat(String message) {
        return chat(message, null);
    }

    public ChatResponse chat(String message, String guestToken) {
        List<ProductSummary> databaseCandidates = databaseFallback.findCandidates(message);
        OptionalInt requestedCount = databaseFallback.requestedComparisonCount(message);
        if (databaseFallback.needsComparisonShortCircuit(message, databaseCandidates)) {
            String answer = databaseFallback.comparisonShortCircuitAnswer(
                    databaseCandidates, requestedCount.orElse(2));
            return new ChatResponse(answer, databaseCandidates, "database-catalog", true);
        }
        if (chatClient == null) {
            return fallbackOrThrow(databaseCandidates, null);
        }

        try {
            String groundedMessage = "Customer request: " + message
                    + "\n\nCatalog candidates already retrieved from the database: " + databaseCandidates
                    + "\n\nHard rule: Discuss and compare ONLY these catalog products. "
                    + "Never invent a product name, price, processor, RAM, storage, display specification, or any other detail. "
                    + "If fewer products are listed than the customer asked for, say so and use only the listed products.";

            String systemPromptWithToken = SYSTEM_PROMPT;
            if (guestToken != null && !guestToken.isBlank()) {
                systemPromptWithToken += "\n\nIMPORTANT: The customer's guest token for cart operations is: " + guestToken
                        + "\nWhen the customer asks to add/remove/view cart items, you MUST use this exact guest token in the cart tool calls.";
                groundedMessage += "\n\nCustomer guest token for cart operations: " + guestToken;
                groundedMessage += "\n\nAVAILABLE CART TOOLS: addToCart, getCart, updateCartItemQuantity, removeCartItem, clearCart";
                groundedMessage += "\nIf the customer's request involves cart operations, you MUST call the appropriate tool function.";
            } else {
                systemPromptWithToken += "\n\nIMPORTANT: No guest token is available. Cart operations may not work properly. "
                        + "Inform the customer if they need to have a cart session.";
            }

            // Enable function calling explicitly
            String answer = chatClient.prompt()
                    .system(systemPromptWithToken)
                    .user(groundedMessage)
                    .tools(productTools)
                    .call()
                    .content();
            return new ChatResponse(answer, databaseCandidates, provider, false);
        } catch (RuntimeException exception) {
            return fallbackOrThrow(databaseCandidates, exception);
        }
    }

    private ChatResponse fallbackOrThrow(List<ProductSummary> candidates, RuntimeException cause) {
        if (fallbackEnabled) {
            return new ChatResponse(databaseFallback.fallbackAnswer(candidates), candidates, "database-fallback", true);
        }
        throw new AiProviderUnavailableException("The configured AI provider is unavailable. Configure Ollama or enable the database fallback.", cause);
    }
}
