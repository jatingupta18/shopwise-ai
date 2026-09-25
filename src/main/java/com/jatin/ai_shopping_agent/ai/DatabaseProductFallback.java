package com.jatin.ai_shopping_agent.ai;

import com.jatin.ai_shopping_agent.dto.ChatResponse;
import com.jatin.ai_shopping_agent.dto.ProductSummary;
import com.jatin.ai_shopping_agent.entity.Product;
import com.jatin.ai_shopping_agent.service.ProductService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Resolves product detail and comparison requests from persisted catalog data when AI is unavailable. */
@Component
public class DatabaseProductFallback {
    private static final Pattern DETAIL_INTENT = Pattern.compile(
            "(?:detail|details|tell\\s+me\\s+more|about|show\\s+(?:the\\s+)?details|ke\\s+baare\\s+me|baare\\s+me\\s+batao|detail\\s+(?:toh|do|batao))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPARISON_INTENT = Pattern.compile(
            "(?:compare|comparison|versus|\\bvs\\.?|best\\s+between|which\\s+is\\s+better)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OTHER_AVAILABLE = Pattern.compile("(?:other\\s+available|other\\s+products|available\\s+products)", Pattern.CASE_INSENSITIVE);
    private static final int MAX_COMPARISON_PRODUCTS = 4;

    private final ProductService productService;

    public DatabaseProductFallback(ProductService productService) {
        this.productService = productService;
    }

    public Optional<ChatResponse> handle(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        if (COMPARISON_INTENT.matcher(message).find()) {
            return Optional.of(compare(message));
        }
        if (DETAIL_INTENT.matcher(message).find()) {
            return Optional.of(details(message));
        }
        return Optional.empty();
    }

    private ChatResponse details(String message) {
        Optional<Product> product = resolveOneProduct(message, productService.getAllProducts());
        if (product.isEmpty()) {
            return response("I could not identify one exact catalog product. Please include the product name for its details.", List.of());
        }
        Product value = product.get();
        String answer = value.getName() + " details — Category: " + value.getCategory()
                + "; Price: ₹" + value.getPrice()
                + "; Description: " + safeDescription(value) + ".";
        return response(answer, List.of(ProductSummary.from(value)));
    }

    private ChatResponse compare(String message) {
        List<Product> catalog = productService.getAllProducts();
        List<Product> namedProducts = resolveNamedProducts(message, catalog);
        List<Product> products;

        if (OTHER_AVAILABLE.matcher(message).find()) {
            if (namedProducts.size() != 1) {
                return response("Please include one exact product name to compare with other available products.", List.of());
            }
            Product selected = namedProducts.getFirst();
            products = new ArrayList<>();
            products.add(selected);
            catalog.stream()
                    .filter(product -> !product.getId().equals(selected.getId()))
                    .filter(product -> sameCategory(product, selected))
                    .sorted(Comparator.comparing(Product::getPrice))
                    .limit(MAX_COMPARISON_PRODUCTS - 1)
                    .forEach(products::add);
            if (products.size() < 2) {
                return response("I found " + selected.getName() + " but no other products in the same catalog category to compare it with.",
                        List.of(ProductSummary.from(selected)));
            }
        } else {
            products = namedProducts;
            if (products.size() < 2) {
                return response("I could not identify two exact catalog products to compare. Please include both product names.",
                        products.stream().map(ProductSummary::from).toList());
            }
        }

        List<ProductSummary> summaries = products.stream().map(ProductSummary::from).toList();
        String comparison = products.stream()
                .map(product -> "- " + product.getName() + " | Category: " + product.getCategory()
                        + " | Price: ₹" + product.getPrice() + " | Description: " + safeDescription(product))
                .collect(Collectors.joining("\n"));
        return response("Database catalog comparison:\n" + comparison, summaries);
    }

    private List<Product> resolveNamedProducts(String message, List<Product> catalog) {
        String withoutIntent = message.replaceFirst("(?i).*(?:compare|comparison|best\\s+between|which\\s+is\\s+better)\\s*", "");
        String[] namedPhrases = withoutIntent.split("(?i)\\s+(?:and|with|vs\\.?|versus|aur)\\s+");
        if (namedPhrases.length >= 2) {
            List<Product> resolved = new ArrayList<>();
            for (String phrase : namedPhrases) {
                resolveOneProduct(phrase, catalog).ifPresent(resolved::add);
            }
            if (resolved.size() == namedPhrases.length) {
                return resolved.stream().distinct().toList();
            }
        }

        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        List<ScoredProduct> matches = catalog.stream()
                .map(product -> new ScoredProduct(product, score(normalizedMessage, product.getName())))
                .filter(match -> match.score() >= 2 || containsFullName(normalizedMessage, match.product().getName()))
                .sorted(Comparator.comparingInt(ScoredProduct::score).reversed())
                .toList();

        return matches.stream()
                .map(ScoredProduct::product)
                .limit(MAX_COMPARISON_PRODUCTS)
                .toList();
    }

    private Optional<Product> resolveOneProduct(String message, List<Product> catalog) {
        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        List<ScoredProduct> matches = catalog.stream()
                .map(product -> new ScoredProduct(product, containsFullName(normalizedMessage, product.getName()) ? 100 : score(normalizedMessage, product.getName())))
                .filter(match -> match.score() > 0)
                .sorted(Comparator.comparingInt(ScoredProduct::score).reversed())
                .toList();
        if (matches.isEmpty() || (matches.size() > 1 && matches.getFirst().score() == matches.get(1).score())) {
            return Optional.empty();
        }
        return Optional.of(matches.getFirst().product());
    }

    private int score(String message, String productName) {
        if (productName == null) {
            return 0;
        }
        int score = 0;
        for (String token : productName.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() >= 2 && message.matches(".*\\b" + Pattern.quote(token) + "\\b.*")) {
                score++;
            }
        }
        return score;
    }

    private boolean containsFullName(String message, String productName) {
        return productName != null && message.contains(productName.toLowerCase(Locale.ROOT));
    }

    private boolean sameCategory(Product first, Product second) {
        return first.getCategory() != null && first.getCategory().equalsIgnoreCase(second.getCategory());
    }

    private String safeDescription(Product product) {
        return product.getDescription() == null || product.getDescription().isBlank()
                ? "No description is available in the catalog" : product.getDescription();
    }

    private ChatResponse response(String answer, List<ProductSummary> products) {
        return new ChatResponse(answer, products, "database-fallback", true);
    }

    private record ScoredProduct(Product product, int score) {
    }
}
