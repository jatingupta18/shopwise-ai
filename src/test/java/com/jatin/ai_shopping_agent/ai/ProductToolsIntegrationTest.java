package com.jatin.ai_shopping_agent.ai;

import com.jatin.ai_shopping_agent.dto.CartItemResponse;
import com.jatin.ai_shopping_agent.dto.CartResponse;
import com.jatin.ai_shopping_agent.dto.ProductSummary;
import com.jatin.ai_shopping_agent.entity.Cart;
import com.jatin.ai_shopping_agent.entity.CartItem;
import com.jatin.ai_shopping_agent.entity.Product;
import com.jatin.ai_shopping_agent.repository.CartItemRepository;
import com.jatin.ai_shopping_agent.repository.CartRepository;
import com.jatin.ai_shopping_agent.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductToolsIntegrationTest {

    @Autowired
    private ProductTools productTools;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    private Product testProduct1;
    private Product testProduct2;

    @BeforeEach
    void setUp() {
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();

        testProduct1 = new Product();
        testProduct1.setName("Laptop Pro");
        testProduct1.setCategory("Electronics");
        testProduct1.setPrice(new BigDecimal("50000"));
        testProduct1.setDescription("High-performance laptop");
        testProduct1 = productRepository.save(testProduct1);

        testProduct2 = new Product();
        testProduct2.setName("Smartphone X");
        testProduct2.setCategory("Electronics");
        testProduct2.setPrice(new BigDecimal("25000"));
        testProduct2.setDescription("Latest smartphone");
        testProduct2 = productRepository.save(testProduct2);
    }

    @Test
    void searchProductsByName_FindsMatchingProducts() {
        List<ProductSummary> results = productTools.searchProductsByName(
                new ProductTools.ProductNameSearch("Laptop"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("Laptop Pro");
        assertThat(results.get(0).price()).isEqualTo(new BigDecimal("50000"));
    }

    @Test
    void searchProductsByCategory_FindsProductsInCategory() {
        List<ProductSummary> results = productTools.searchProductsByCategory(
                new ProductTools.ProductCategorySearch("Electronics"));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(ProductSummary::name)
                .containsExactlyInAnyOrder("Laptop Pro", "Smartphone X");
    }

    @Test
    void findProductsWithinBudget_FindsAffordableProducts() {
        List<ProductSummary> results = productTools.findProductsWithinBudget(
                new ProductTools.ProductBudgetSearch(new BigDecimal("30000")));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("Smartphone X");
    }

    @Test
    void getProductDetails_ReturnsProductDetails() {
        ProductSummary result = productTools.getProductDetails(
                new ProductTools.ProductIdRequest(testProduct1.getId()));

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("Laptop Pro");
        assertThat(result.category()).isEqualTo("Electronics");
        assertThat(result.price()).isEqualTo(new BigDecimal("50000"));
    }

    @Test
    void compareProducts_ReturnsMultipleProducts() {
        List<ProductSummary> results = productTools.compareProducts(
                new ProductTools.ProductComparisonRequest(
                        List.of(testProduct1.getId(), testProduct2.getId())));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(ProductSummary::name)
                .containsExactlyInAnyOrder("Laptop Pro", "Smartphone X");
    }

    @Test
    void addToCart_AddsProductToCart() {
        CartResponse response = productTools.addToCart(
                new ProductTools.AddToCartRequest("test-token", testProduct1.getId(), 2));

        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productId()).isEqualTo(testProduct1.getId());
        assertThat(response.items().get(0).quantity()).isEqualTo(2);
    }

    @Test
    void getCart_ReturnsCartContents() {
        productTools.addToCart(
                new ProductTools.AddToCartRequest("test-token", testProduct1.getId(), 1));

        CartResponse response = productTools.getCart(
                new ProductTools.GetCartRequest("test-token"));

        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(1);
        assertThat(response.itemCount()).isEqualTo(1);
    }

    @Test
    void updateCartItemQuantity_UpdatesQuantity() {
        CartResponse initialCart = productTools.addToCart(
                new ProductTools.AddToCartRequest("test-token", testProduct1.getId(), 1));

        CartResponse updatedCart = productTools.updateCartItemQuantity(
                new ProductTools.UpdateQuantityRequest("test-token", initialCart.items().get(0).id(), 3));

        assertThat(updatedCart.items().get(0).quantity()).isEqualTo(3);
    }

    @Test
    void removeCartItem_RemovesItemFromCart() {
        CartResponse initialCart = productTools.addToCart(
                new ProductTools.AddToCartRequest("test-token", testProduct1.getId(), 1));

        CartResponse updatedCart = productTools.removeCartItem(
                new ProductTools.RemoveItemRequest("test-token", initialCart.items().get(0).id()));

        assertThat(updatedCart.items()).isEmpty();
    }

    @Test
    void clearCart_ClearsAllItems() {
        productTools.addToCart(
                new ProductTools.AddToCartRequest("test-token", testProduct1.getId(), 1));
        productTools.addToCart(
                new ProductTools.AddToCartRequest("test-token", testProduct2.getId(), 1));

        CartResponse clearedCart = productTools.clearCart(
                new ProductTools.ClearCartRequest("test-token"));

        assertThat(clearedCart.items()).isEmpty();
    }
}
