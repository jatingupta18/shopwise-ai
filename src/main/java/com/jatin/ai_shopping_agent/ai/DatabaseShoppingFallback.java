package com.jatin.ai_shopping_agent.ai;

import com.jatin.ai_shopping_agent.dto.ProductSummary;
import com.jatin.ai_shopping_agent.entity.Product;
import com.jatin.ai_shopping_agent.service.ProductService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** A deterministic, database-only result set used as agent grounding and offline fallback. */
@Component
public class DatabaseShoppingFallback {
    private static final Pattern BUDGET = Pattern.compile("(?:under|below|less than|within|budget(?:\\s+of)?|maximum|max)\\s*(?:₹|rs\\.?|inr)?\\s*([0-9][0-9,]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPARISON_INTENT = Pattern.compile(
            "\\b(?:compare|comparison|versus|vs\\.?|which\\s+is\\s+better|which\\s+are\\s+better)\\b",
            Pattern.CASE_INSENSITIVE);
    
    private static final Pattern CART_OPERATION_INTENT = Pattern.compile(
            "(?:cart|add\\s+to\\s+cart|cart\\s+me|remove\\s+from\\s+cart|cart\\s+se|show\\s+my\\s+cart|mera\\s+cart|cart\\s+dikhao)",
            Pattern.CASE_INSENSITIVE);
    private static final String COUNT_TOKEN = "(\\d+|two|three|four|five|six|seven|eight|nine|ten)";
    private static final String NOT_SPEC_UNIT = "(?!\\s*(?:gb|tb|mb|kb|ghz|mhz|hz|inch(?:es)?|mp|mah|ram|ssd|hdd|nvme|cores?)\\b)";
    /**
     * A number is the requested comparison size only when the wording marks it as a product count
     * (best/top N, compare N, compare the best N). Model names and RAM/storage figures are ignored.
     */
    private static final Pattern NUMBERED_COMPARISON = Pattern.compile(
            "\\b(?:best|top)\\s+" + COUNT_TOKEN + "\\b" + NOT_SPEC_UNIT
                    + "|\\b(?:compare|comparison|versus|vs\\.?)\\b(?:\\s+(?:the|these|those|of))*(?:\\s+(?:best|top))?"
                    + "\\s+" + COUNT_TOKEN + "\\b" + NOT_SPEC_UNIT,
            Pattern.CASE_INSENSITIVE);
    private static final Map<String, Integer> NUMBER_WORDS = Map.of(
            "two", 2, "three", 3, "four", 4, "five", 5,
            "six", 6, "seven", 7, "eight", 8, "nine", 9, "ten", 10);
    private static final int RESULT_LIMIT = 10;

    private final ProductService productService;

    public DatabaseShoppingFallback(ProductService productService) {
        this.productService = productService;
    }

    public List<ProductSummary> findCandidates(String message) {
        List<Product> allProducts = productService.getAllProducts();
        Optional<BigDecimal> budget = extractBudget(message);
        String normalized = message.toLowerCase(Locale.ROOT);

        List<Product> candidates = allProducts.stream()
                .filter(product -> budget.map(value -> product.getPrice().compareTo(value) <= 0).orElse(true))
                .filter(product -> matchesKnownCategory(product, normalized) || matchesProductText(product, normalized))
                .sorted(Comparator.comparing(Product::getPrice))
                .distinct()
                .limit(RESULT_LIMIT)
                .toList();

        if (candidates.isEmpty() && budget.isPresent()) {
            candidates = productService.getProductsWithinBudget(budget.get()).stream().limit(RESULT_LIMIT).toList();
        }
        return candidates.stream().map(ProductSummary::from).distinct().toList();
    }

    public String fallbackAnswer(List<ProductSummary> products) {
        if (products.isEmpty()) {
            return "I could not find matching products in the current catalog. Try a product name, category, or a different budget.";
        }
        return "I found " + products.size() + " matching product(s) in the database. The listed products and prices are from the current catalog.";
    }

    /**
     * Requested comparison size when the user asks to compare / show the best N products.
     * A plain "compare … with other products" request implies 2. Empty means not a comparison request.
     */
    public OptionalInt requestedComparisonCount(String message) {
        if (message == null || message.isBlank()) {
            return OptionalInt.empty();
        }
        Matcher numbered = NUMBERED_COMPARISON.matcher(message);
        if (numbered.find()) {
            String token = firstNonNullGroup(numbered);
            if (token != null) {
                int count = parseCount(token);
                if (count >= 2 && count <= 10) {
                    return OptionalInt.of(count);
                }
            }
        }
        if (COMPARISON_INTENT.matcher(message).find()) {
            return OptionalInt.of(2);
        }
        return OptionalInt.empty();
    }

    public boolean needsComparisonShortCircuit(String message, List<ProductSummary> products) {
        // Don't short-circuit if this is a cart operation - let the AI handle it with tools
        if (isCartOperation(message)) {
            return false;
        }
        OptionalInt requested = requestedComparisonCount(message);
        return requested.isPresent() && products.size() < requested.getAsInt();
    }
    
    private boolean isCartOperation(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        // Check for cart-specific patterns
        return CART_OPERATION_INTENT.matcher(normalized).find() 
                || normalized.contains("cart me daalo")
                || normalized.contains("cart me add")
                || normalized.contains("cart se remove")
                || normalized.contains("cart dikhao")
                || normalized.contains("mera cart");
    }

    public String comparisonShortCircuitAnswer(List<ProductSummary> products, int requestedCount) {
        if (products.isEmpty()) {
            return "No matching products are available in the catalog for this comparison. "
                    + "I will not invent products, prices, or specifications.";
        }
        String catalogLines = products.stream()
                .map(product -> "- " + product.name() + " (₹" + product.price() + ", " + product.category() + "): "
                        + (product.description() == null ? "" : product.description()))
                .collect(Collectors.joining("\n"));
        return "You asked to compare " + requestedCount + " products, but only " + products.size()
                + " matching product(s) are available in the catalog. "
                + "I will compare only these real catalog products and will not invent names, prices, processors, RAM, storage, display specs, or other details.\n"
                + catalogLines;
    }

    private boolean matchesKnownCategory(Product product, String normalizedMessage) {
        return product.getCategory() != null && normalizedMessage.contains(product.getCategory().toLowerCase(Locale.ROOT));
    }

    private boolean matchesProductText(Product product, String normalizedMessage) {
        String name = product.getName() == null ? "" : product.getName().toLowerCase(Locale.ROOT);
        String description = product.getDescription() == null ? "" : product.getDescription().toLowerCase(Locale.ROOT);
        String category = product.getCategory() == null ? "" : product.getCategory().toLowerCase(Locale.ROOT);
        
        String[] messageWords = normalizedMessage.split("[^a-z0-9]+");
        if (messageWords.length == 0) {
            return false;
        }
        
        return java.util.Arrays.stream(messageWords)
                .filter(word -> word.length() >= 3)
                .anyMatch(word -> {
                    String singular = word.endsWith("s") ? word.substring(0, word.length() - 1) : word;
                    return name.contains(word) || name.contains(singular)
                            || description.contains(word) || description.contains(singular)
                            || category.contains(word) || category.contains(singular);
                });
    }

    private Optional<BigDecimal> extractBudget(String message) {
        Matcher matcher = BUDGET.matcher(message);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(new BigDecimal(matcher.group(1).replace(",", "")));
    }

    private static String firstNonNullGroup(Matcher matcher) {
        for (int index = 1; index <= matcher.groupCount(); index++) {
            if (matcher.group(index) != null) {
                return matcher.group(index);
            }
        }
        return null;
    }

    private static int parseCount(String token) {
        Integer wordValue = NUMBER_WORDS.get(token.toLowerCase(Locale.ROOT));
        if (wordValue != null) {
            return wordValue;
        }
        return Integer.parseInt(token);
    }
}
