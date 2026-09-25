package com.jatin.ai_shopping_agent.ai;

import com.jatin.ai_shopping_agent.dto.ChatRequest;
import com.jatin.ai_shopping_agent.dto.ChatResponse;
import com.jatin.ai_shopping_agent.entity.Cart;
import com.jatin.ai_shopping_agent.entity.CartItem;
import com.jatin.ai_shopping_agent.entity.Product;
import com.jatin.ai_shopping_agent.repository.CartItemRepository;
import com.jatin.ai_shopping_agent.repository.CartRepository;
import com.jatin.ai_shopping_agent.repository.ProductRepository;
import com.jatin.ai_shopping_agent.service.CartService;
import org.junit.jupiter.api.BeforeEach;
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
class AiCartOperationsIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private ShoppingAgentService shoppingAgentService;

    private Product testProduct;
    private Product dellInspiron;
    private Product hpPavilion;
    private String guestToken = "test-guest-token";

    @BeforeEach
    void setUp() {
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();

        testProduct = new Product();
        testProduct.setName("Test Laptop");
        testProduct.setCategory("Electronics");
        testProduct.setPrice(new BigDecimal("45000"));
        testProduct.setDescription("Test laptop for AI cart operations");
        testProduct = productRepository.save(testProduct);

        dellInspiron = productRepository.save(new Product(
                "Dell Inspiron Laptop", "15.6-inch FHD, Intel i5", new BigDecimal("55000"), "Electronics"));
        hpPavilion = productRepository.save(new Product(
                "HP Pavilion Laptop", "14-inch HD, AMD Ryzen 5", new BigDecimal("52000"), "Electronics"));

        // Create a cart with the guest token
        Cart cart = new Cart();
        cart.setGuestToken(guestToken);
        cart = cartRepository.save(cart);
    }

    @Test
    void manualCartOperations_WorksWithGuestToken() {
        // Verify that cart operations work manually with the guest token
        var cartResponse = cartService.addProduct(guestToken, testProduct.getId(), 1);

        assertThat(cartResponse).isNotNull();
        assertThat(cartResponse.items()).hasSize(1);
        assertThat(cartResponse.items().get(0).productId()).isEqualTo(testProduct.getId());
        assertThat(cartResponse.itemCount()).isEqualTo(1);
    }

    @Test
    void shoppingAgentService_WithGuestToken_ProcessesRequest() {
        // Verify that the shopping agent service can process requests with guest tokens
        ChatResponse response = shoppingAgentService.chat("Show me laptops", guestToken);

        assertThat(response).isNotNull();
        assertThat(response.answer()).isNotBlank();
        assertThat(response.products()).isNotNull();
    }

    @Test
    void shoppingAgentService_CartOperationWorkflow() {
        // This test verifies the cart operation workflow that the AI should follow
        // First, get the current cart
        var initialCart = cartService.getCart(guestToken);
        assertThat(initialCart.itemCount()).isEqualTo(0);

        // Add a product using the cart service (simulating what AI should do)
        var cartAfterAdd = cartService.addProduct(guestToken, testProduct.getId(), 1);
        assertThat(cartAfterAdd.itemCount()).isEqualTo(1);

        // Verify the product was added
        var finalCart = cartService.getCart(guestToken);
        assertThat(finalCart.itemCount()).isEqualTo(1);
        assertThat(finalCart.items().get(0).productId()).isEqualTo(testProduct.getId());
    }

    @Test
    void fallback_AddToCart_UsesTheRealCatalogProduct() {
        ChatResponse response = shoppingAgentService.chat("add Dell Inspiron laptop to cart", guestToken);

        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.answer()).contains("Added Dell Inspiron Laptop");
        assertThat(cartService.getCart(guestToken).items())
                .extracting(item -> item.productId())
                .containsExactly(dellInspiron.getId());
    }

    @Test
    void fallback_HinglishAddToCart_UsesTheRealCatalogProduct() {
        ChatResponse response = shoppingAgentService.chat("Dell Inspiron laptop ko cart me add karo", guestToken);

        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.answer()).contains("Added Dell Inspiron Laptop");
        assertThat(cartService.getCart(guestToken).items()).hasSize(1);
    }

    @Test
    void fallback_GetCartImmediatelyAfterAdd_ReturnsPersistedCart() {
        shoppingAgentService.chat("cart me HP Pavilion add karo", guestToken);

        ChatResponse response = shoppingAgentService.chat("show my cart", guestToken);

        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.answer()).contains("HP Pavilion Laptop x1");
        assertThat(response.products()).extracting(product -> product.id()).containsExactly(hpPavilion.getId());
    }

    @Test
    void fallback_HinglishGetCart_PersistsAcrossSequentialRequests() {
        shoppingAgentService.chat("Dell Inspiron laptop ko cart me add karo", guestToken);

        ChatResponse response = shoppingAgentService.chat("mera cart dikhao", guestToken);

        assertThat(response.answer()).contains("Dell Inspiron Laptop x1");
        assertThat(cartService.getCart(guestToken).items()).hasSize(1);
    }

    @Test
    void fallback_RemoveFromCart_UsesPersistedCartItemId() {
        shoppingAgentService.chat("add Dell Inspiron laptop to cart", guestToken);

        ChatResponse response = shoppingAgentService.chat("remove Dell Inspiron from cart", guestToken);

        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.answer()).contains("Removed Dell Inspiron Laptop");
        assertThat(cartService.getCart(guestToken).items()).isEmpty();
    }

    @Test
    void fallback_ProductDetails_UsesThePersistedCatalogProduct() {
        ChatResponse response = shoppingAgentService.chat("HP Pavilion laptop details", guestToken);

        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.answer()).contains("HP Pavilion Laptop", "Category: Electronics", "₹52000", "AMD Ryzen 5");
        assertThat(response.products()).extracting(product -> product.id()).containsExactly(hpPavilion.getId());
    }

    @Test
    void fallback_HinglishProductDetails_UsesThePersistedCatalogProduct() {
        ChatResponse response = shoppingAgentService.chat("Dell Inspiron ke baare me batao", guestToken);

        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.answer()).contains("Dell Inspiron Laptop", "₹55000", "Intel i5");
        assertThat(response.products()).extracting(product -> product.id()).containsExactly(dellInspiron.getId());
    }

    @Test
    void fallback_UnknownOrAmbiguousProductDetails_DoNotGuess() {
        productRepository.save(new Product(
                "Dell Inspiron 14", "14-inch FHD, Intel i3", new BigDecimal("48000"), "Electronics"));

        ChatResponse unknown = shoppingAgentService.chat("Unknown device details", guestToken);
        ChatResponse ambiguous = shoppingAgentService.chat("Dell Inspiron details", guestToken);

        assertThat(unknown.answer()).contains("could not identify one exact catalog product");
        assertThat(unknown.products()).isEmpty();
        assertThat(ambiguous.answer()).contains("could not identify one exact catalog product");
        assertThat(ambiguous.products()).isEmpty();
    }

    @Test
    void fallback_ComparisonOfTwoNamedProducts_UsesPersistedValues() {
        ChatResponse response = shoppingAgentService.chat("compare HP Pavilion and Dell Inspiron Laptop", guestToken);

        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.answer()).contains("Database catalog comparison", "HP Pavilion Laptop", "Dell Inspiron Laptop", "₹52000", "₹55000");
        assertThat(response.products()).extracting(product -> product.id())
                .containsExactlyInAnyOrder(hpPavilion.getId(), dellInspiron.getId());
    }

    @Test
    void fallback_ComparisonWithOtherAvailableProducts_UsesRelevantCategory() {
        ChatResponse response = shoppingAgentService.chat("Compare Dell Inspiron Laptop with other available products", guestToken);

        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.answer()).contains("Database catalog comparison", "Dell Inspiron Laptop", "HP Pavilion Laptop");
        assertThat(response.products()).extracting(product -> product.category()).containsOnly("Electronics");
        assertThat(response.products()).extracting(product -> product.id()).contains(dellInspiron.getId());
    }

    @Test
    void fallback_AmbiguousComparison_AsksForBothProductNames() {
        ChatResponse response = shoppingAgentService.chat("compare these laptops", guestToken);

        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.answer()).contains("could not identify two exact catalog products");
    }
}
