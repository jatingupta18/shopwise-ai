package com.jatin.ai_shopping_agent.ai;

import com.jatin.ai_shopping_agent.dto.CartItemResponse;
import com.jatin.ai_shopping_agent.dto.CartResponse;
import com.jatin.ai_shopping_agent.dto.ChatResponse;
import com.jatin.ai_shopping_agent.dto.ProductSummary;
import com.jatin.ai_shopping_agent.entity.Product;
import com.jatin.ai_shopping_agent.service.CartService;
import com.jatin.ai_shopping_agent.service.ProductService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Executes safe, database-backed cart actions when an AI provider is unavailable. */
@Component
public class DatabaseCartFallback {
    private static final Pattern VIEW_CART = Pattern.compile(
            "(?:show\\s+(?:me\\s+)?(?:my\\s+)?cart|view\\s+(?:my\\s+)?cart|cart\\s+dikhao|mera\\s+cart|cart\\s+me\\s+kya\\s+hai|what(?:'s|\\s+is)\\s+in\\s+(?:my\\s+)?cart)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ADD_TO_CART = Pattern.compile(
            "(?:add|daalo|dalo|rakh(?:o)?|include).*(?:cart)|(?:cart).*(?:add|daalo|dalo|rakh(?:o)?|include)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REMOVE_FROM_CART = Pattern.compile(
            "(?:remove|delete|hatao|nikalo|nikal).*(?:cart)|(?:cart).*(?:remove|delete|hatao|nikalo|nikal)",
            Pattern.CASE_INSENSITIVE);

    private final ProductService productService;
    private final CartService cartService;

    public DatabaseCartFallback(ProductService productService, CartService cartService) {
        this.productService = productService;
        this.cartService = cartService;
    }

    public Optional<ChatResponse> handle(String message, String guestToken) {
        CartIntent intent = detectIntent(message);
        if (intent == CartIntent.NONE) {
            return Optional.empty();
        }
        if (guestToken == null || guestToken.isBlank()) {
            return Optional.of(response("A cart session is required before I can manage your cart.", List.of()));
        }

        return switch (intent) {
            case VIEW -> Optional.of(viewCart(guestToken));
            case ADD -> Optional.of(addToCart(message, guestToken));
            case REMOVE -> Optional.of(removeFromCart(message, guestToken));
            case NONE -> Optional.empty();
        };
    }

    private ChatResponse addToCart(String message, String guestToken) {
        Optional<Product> product = resolveProduct(message, productService.getAllProducts());
        if (product.isEmpty()) {
            return response("I could not identify one exact catalog product to add. Please include the product name.", List.of());
        }

        CartResponse cart = cartService.addProduct(guestToken, product.get().getId(), 1);
        return response("Added " + product.get().getName() + " to your cart. " + cartSummary(cart), productsFromCart(cart));
    }

    private ChatResponse viewCart(String guestToken) {
        CartResponse cart = cartService.getCart(guestToken);
        return response(cartSummary(cart), productsFromCart(cart));
    }

    private ChatResponse removeFromCart(String message, String guestToken) {
        CartResponse currentCart = cartService.getCart(guestToken);
        Optional<CartItemResponse> item = resolveCartItem(message, currentCart.items());
        if (item.isEmpty()) {
            String answer = currentCart.items().isEmpty()
                    ? "Your cart is empty, so there is nothing to remove."
                    : "I could not identify one exact cart item to remove. Please include the product name.";
            return response(answer, productsFromCart(currentCart));
        }

        // CartService requires the cart item ID, never the catalog product ID.
        CartResponse updatedCart = cartService.removeItem(guestToken, item.get().id());
        return response("Removed " + item.get().name() + " from your cart. " + cartSummary(updatedCart), productsFromCart(updatedCart));
    }

    private CartIntent detectIntent(String message) {
        if (message == null || message.isBlank()) {
            return CartIntent.NONE;
        }
        if (REMOVE_FROM_CART.matcher(message).find()) {
            return CartIntent.REMOVE;
        }
        if (ADD_TO_CART.matcher(message).find()) {
            return CartIntent.ADD;
        }
        if (VIEW_CART.matcher(message).find()) {
            return CartIntent.VIEW;
        }
        return CartIntent.NONE;
    }

    private Optional<Product> resolveProduct(String message, List<Product> products) {
        return bestUniqueMatch(message, products, Product::getName);
    }

    private Optional<CartItemResponse> resolveCartItem(String message, List<CartItemResponse> items) {
        return bestUniqueMatch(message, items, CartItemResponse::name);
    }

    private <T> Optional<T> bestUniqueMatch(String message, List<T> values, java.util.function.Function<T, String> nameExtractor) {
        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        List<ScoredValue<T>> matches = new ArrayList<>();
        for (T value : values) {
            String name = nameExtractor.apply(value);
            if (name == null || name.isBlank()) {
                continue;
            }
            String normalizedName = name.toLowerCase(Locale.ROOT);
            int score = normalizedMessage.contains(normalizedName) ? 100 : matchingNameTokenCount(normalizedMessage, normalizedName);
            if (score > 0) {
                matches.add(new ScoredValue<>(value, score));
            }
        }
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        matches.sort(Comparator.comparingInt(ScoredValue<T>::score).reversed());
        if (matches.size() > 1 && matches.getFirst().score() == matches.get(1).score()) {
            return Optional.empty();
        }
        return Optional.of(matches.getFirst().value());
    }

    private int matchingNameTokenCount(String normalizedMessage, String normalizedName) {
        int count = 0;
        for (String token : normalizedName.split("[^a-z0-9]+")) {
            if (token.length() >= 2 && normalizedMessage.matches(".*\\b" + Pattern.quote(token) + "\\b.*")) {
                count++;
            }
        }
        return count;
    }

    private List<ProductSummary> productsFromCart(CartResponse cart) {
        return cart.items().stream()
                .map(item -> new ProductSummary(item.productId(), item.name(), item.description(), item.unitPrice(), item.category()))
                .toList();
    }

    private String cartSummary(CartResponse cart) {
        if (cart.items().isEmpty()) {
            return "Your cart is empty.";
        }
        String items = cart.items().stream()
                .map(item -> item.name() + " x" + item.quantity())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        return "Your cart has " + cart.itemCount() + " item(s): " + items + ". Subtotal: ₹" + cart.subtotal() + ".";
    }

    private ChatResponse response(String answer, List<ProductSummary> products) {
        return new ChatResponse(answer, products, "database-fallback", true);
    }

    private enum CartIntent {
        ADD, VIEW, REMOVE, NONE
    }

    private record ScoredValue<T>(T value, int score) {
    }
}
