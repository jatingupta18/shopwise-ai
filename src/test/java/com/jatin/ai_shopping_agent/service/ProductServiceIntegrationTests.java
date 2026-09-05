package com.jatin.ai_shopping_agent.service;

import com.jatin.ai_shopping_agent.ai.DatabaseShoppingFallback;
import com.jatin.ai_shopping_agent.entity.Product;
import com.jatin.ai_shopping_agent.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductServiceIntegrationTests {
    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseShoppingFallback databaseShoppingFallback;

    @Test
    void findsProductsWithinMaximumBudgetInPriceOrder() {
        productRepository.save(new Product("Premium Laptop", "High performance", new BigDecimal("65000"), "Electronics"));
        productRepository.save(new Product("Budget Laptop", "Everyday work", new BigDecimal("45000"), "Electronics"));

        assertThat(productService.getProductsWithinBudget(new BigDecimal("60000")))
                .extracting(Product::getName)
                .containsExactly("Budget Laptop");
    }

    @Test
    void groundsNaturalLanguageBudgetSearchInDatabaseProducts() {
        productRepository.save(new Product("Office Laptop", "Portable work computer", new BigDecimal("58000"), "Electronics"));
        productRepository.save(new Product("Gaming Laptop", "High performance", new BigDecimal("85000"), "Electronics"));

        assertThat(databaseShoppingFallback.findCandidates("I need a laptop under 60000"))
                .extracting(product -> product.name())
                .contains("Office Laptop")
                .doesNotContain("Gaming Laptop");
    }

    @Test
    void comparisonRequestsDoNotInventWhenFewerCatalogMatchesExist() {
        productRepository.save(new Product("Office Laptop", "Portable work computer", new BigDecimal("58000"), "Electronics"));
        productRepository.save(new Product("Gaming Laptop", "High performance", new BigDecimal("85000"), "Electronics"));

        var matches = databaseShoppingFallback.findCandidates("Compare the best 2 laptops under 60000");
        assertThat(matches).extracting(product -> product.name()).containsExactly("Office Laptop");
        assertThat(databaseShoppingFallback.requestedComparisonCount("Compare the best 2 laptops under 60000"))
                .hasValue(2);
        assertThat(databaseShoppingFallback.needsComparisonShortCircuit("Compare the best 2 laptops under 60000", matches))
                .isTrue();
        assertThat(databaseShoppingFallback.comparisonShortCircuitAnswer(matches, 2))
                .contains("only 1 matching product")
                .contains("Office Laptop")
                .doesNotContain("Gaming Laptop");
    }
}
