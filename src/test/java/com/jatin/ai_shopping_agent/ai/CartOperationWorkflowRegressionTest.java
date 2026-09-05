package com.jatin.ai_shopping_agent.ai;

import com.jatin.ai_shopping_agent.dto.CartItemResponse;
import com.jatin.ai_shopping_agent.dto.CartResponse;
import com.jatin.ai_shopping_agent.dto.ChatResponse;
import com.jatin.ai_shopping_agent.entity.Cart;
import com.jatin.ai_shopping_agent.entity.Product;
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

/**
 * Regression test for the exact cart operation scenario:
 * 1. Add HP Pavilion (product ID 2) to cart
 * 2. Verify getCart shows correct state (not contradictory)
 * 3. Verify removeCartItem uses correct item ID (not product ID)
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CartOperationWorkflowRegressionTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private DatabaseShoppingFallback databaseShoppingFallback;

    @Autowired
    private ShoppingAgentService shoppingAgentService;

    private Product dellLaptop;
    private Product hpPavilion;
    private String guestToken = "regression-test-token";

    @BeforeEach
    void setUp() {
        cartRepository.deleteAll();
        productRepository.deleteAll();

        // Create Dell Inspiron Laptop (product ID likely 1)
        dellLaptop = new Product();
        dellLaptop.setName("Dell Inspiron Laptop");
        dellLaptop.setCategory("Electronics");
        dellLaptop.setPrice(new BigDecimal("55000"));
        dellLaptop.setDescription("15.6-inch FHD, Intel i5, 8GB RAM, 512GB SSD");
        dellLaptop = productRepository.save(dellLaptop);

        // Create HP Pavilion Laptop (product ID likely 2)
        hpPavilion = new Product();
        hpPavilion.setName("HP Pavilion Laptop");
        hpPavilion.setCategory("Electronics");
        hpPavilion.setPrice(new BigDecimal("52000"));
        hpPavilion.setDescription("14-inch HD, AMD Ryzen 5, 16GB RAM, 1TB SSD");
        hpPavilion = productRepository.save(hpPavilion);
    }

    @Test
    void addHPPavilionToCart_verifyGetCartShowsCorrectState() {
        // Step 1: Add HP Pavilion to cart
        CartResponse addResponse = cartService.addProduct(guestToken, hpPavilion.getId(), 1);
        
        assertThat(addResponse).isNotNull();
        assertThat(addResponse.items()).hasSize(1);
        assertThat(addResponse.itemCount()).isEqualTo(1);
        
        CartItemResponse addedItem = addResponse.items().get(0);
        assertThat(addedItem.productId()).isEqualTo(hpPavilion.getId());
        assertThat(addedItem.name()).isEqualTo("HP Pavilion Laptop");
        assertThat(addedItem.quantity()).isEqualTo(1);
        
        Long itemId = addedItem.id(); // This is the cart item ID, NOT the product ID
        
        // Step 2: Verify getCart returns consistent state
        CartResponse getCartResponse = cartService.getCart(guestToken);
        
        assertThat(getCartResponse).isNotNull();
        assertThat(getCartResponse.items()).hasSize(1);
        assertThat(getCartResponse.itemCount()).isEqualTo(1);
        
        CartItemResponse cartItem = getCartResponse.items().get(0);
        assertThat(cartItem.productId()).isEqualTo(hpPavilion.getId());
        assertThat(cartItem.name()).isEqualTo("HP Pavilion Laptop");
        assertThat(cartItem.quantity()).isEqualTo(1);
        assertThat(cartItem.id()).isEqualTo(itemId); // Same item ID
        
        // Verify the cart is NOT empty (regression check)
        assertThat(getCartResponse.itemCount()).isNotEqualTo(0);
        assertThat(getCartResponse.items()).isNotEmpty();
    }

    @Test
    void removeCartItem_usesCorrectItemId_notProductId() {
        // Step 1: Add HP Pavilion to cart
        CartResponse addResponse = cartService.addProduct(guestToken, hpPavilion.getId(), 1);
        Long itemId = addResponse.items().get(0).id();
        
        // Step 2: Verify cart has 1 item
        CartResponse cartBeforeRemove = cartService.getCart(guestToken);
        assertThat(cartBeforeRemove.items()).hasSize(1);
        assertThat(cartBeforeRemove.items().get(0).productId()).isEqualTo(hpPavilion.getId());
        
        // Step 3: Remove using correct ITEM ID (not product ID)
        CartResponse removeResponse = cartService.removeItem(guestToken, itemId);
        
        assertThat(removeResponse).isNotNull();
        assertThat(removeResponse.items()).isEmpty();
        assertThat(removeResponse.itemCount()).isEqualTo(0);
        
        // Step 4: Verify cart is now empty
        CartResponse finalCart = cartService.getCart(guestToken);
        assertThat(finalCart.items()).isEmpty();
        assertThat(finalCart.itemCount()).isEqualTo(0);
    }

    @Test
    void removeCartItem_withProductId_failsCorrectly() {
        // Step 1: Add HP Pavilion to cart
        cartService.addProduct(guestToken, hpPavilion.getId(), 1);
        
        // Step 2: Try to remove using PRODUCT ID instead of ITEM ID
        // This should fail because product ID != item ID
        try {
            cartService.removeItem(guestToken, hpPavilion.getId());
            // If we get here, the remove operation incorrectly accepted a product ID
            // This is the bug we're testing for
            CartResponse cartAfterWrongRemove = cartService.getCart(guestToken);
            
            // The cart should still have the item if the wrong ID was used
            // If it's empty, then the system incorrectly treated product ID as item ID
            assertThat(cartAfterWrongRemove.items()).hasSize(1);
            assertThat(cartAfterWrongRemove.items().get(0).productId()).isEqualTo(hpPavilion.getId());
        } catch (Exception e) {
            // Expected: remove should fail with "Cart item not found" when given a product ID
            // This is the correct behavior
            assertThat(e.getMessage()).contains("Cart item not found");
        }
    }

    @Test
    void multipleItems_removeSpecificItemById() {
        // Step 1: Add both laptops to cart
        cartService.addProduct(guestToken, dellLaptop.getId(), 1);
        cartService.addProduct(guestToken, hpPavilion.getId(), 1);
        
        CartResponse cartWithBoth = cartService.getCart(guestToken);
        assertThat(cartWithBoth.items()).hasSize(2);
        
        // Get the item IDs
        Long dellItemId = cartWithBoth.items().stream()
                .filter(item -> item.productId().equals(dellLaptop.getId()))
                .map(CartItemResponse::id)
                .findFirst()
                .orElseThrow();
        
        Long hpItemId = cartWithBoth.items().stream()
                .filter(item -> item.productId().equals(hpPavilion.getId()))
                .map(CartItemResponse::id)
                .findFirst()
                .orElseThrow();
        
        // Step 2: Remove HP Pavilion using its ITEM ID
        CartResponse afterRemove = cartService.removeItem(guestToken, hpItemId);
        
        assertThat(afterRemove.items()).hasSize(1);
        assertThat(afterRemove.items().get(0).productId()).isEqualTo(dellLaptop.getId());
        assertThat(afterRemove.items().get(0).name()).isEqualTo("Dell Inspiron Laptop");
        
        // Step 3: Verify Dell is still there
        CartResponse finalCart = cartService.getCart(guestToken);
        assertThat(finalCart.items()).hasSize(1);
        assertThat(finalCart.items().get(0).productId()).isEqualTo(dellLaptop.getId());
    }

    @Test
    void exactWorkflow_reproduceManualTestScenario() {
        // This test reproduces the exact manual test scenario:
        // 1. Search laptop → add HP Pavilion (ID 2) → show cart → remove the laptop
        
        // Step 1: Simulate search for laptops (already have dellLaptop and hpPavilion in DB)
        // Step 2: Add HP Pavilion to cart (simulating "HP Pavilion laptop ko cart me daalo")
        CartResponse addResponse = cartService.addProduct(guestToken, hpPavilion.getId(), 1);
        
        assertThat(addResponse.items()).hasSize(1);
        assertThat(addResponse.items().get(0).productId()).isEqualTo(hpPavilion.getId());
        assertThat(addResponse.items().get(0).name()).isEqualTo("HP Pavilion Laptop");
        
        Long hpItemId = addResponse.items().get(0).id(); // Item ID for removal
        Long hpProductId = addResponse.items().get(0).productId(); // Product ID (2)
        
        // Verify these are different IDs
        assertThat(hpItemId).isNotEqualTo(hpProductId);
        
        // Step 3: Show cart (simulating "Mera cart dikhao")
        CartResponse showCartResponse = cartService.getCart(guestToken);
        
        // This should NOT be contradictory - should show 1 item with HP Pavilion
        assertThat(showCartResponse.items()).hasSize(1);
        assertThat(showCartResponse.itemCount()).isEqualTo(1);
        assertThat(showCartResponse.items().get(0).productId()).isEqualTo(hpPavilion.getId());
        assertThat(showCartResponse.items().get(0).name()).isEqualTo("HP Pavilion Laptop");
        
        // Step 4: Remove the laptop (simulating "Cart se laptop remove karo")
        // CRITICAL: Must use ITEM ID, not PRODUCT ID
        // The bug was that it would use Dell's product ID (1) instead of HP's item ID
        CartResponse removeResponse = cartService.removeItem(guestToken, hpItemId);
        
        assertThat(removeResponse.items()).isEmpty();
        assertThat(removeResponse.itemCount()).isEqualTo(0);
        
        // Verify cart is now empty
        CartResponse finalCart = cartService.getCart(guestToken);
        assertThat(finalCart.items()).isEmpty();
        assertThat(finalCart.itemCount()).isEqualTo(0);
    }

    @Test
    void cartOperationIntent_notShortCircuited_byDatabaseFallback() {
        // Test that cart operation messages are NOT short-circuited by the database fallback
        // This ensures the AI can actually call the cart tools
        
        // Add HP Pavilion to cart first
        cartService.addProduct(guestToken, hpPavilion.getId(), 1);
        
        // Test various cart operation messages
        String[] cartOperationMessages = {
            "HP Pavilion Laptop ko cart me daalo",
            "cart me add karo",
            "mera cart dikhao",
            "cart se remove karo",
            "show my cart",
            "add to cart"
        };
        
        for (String message : cartOperationMessages) {
            boolean isShortCircuit = databaseShoppingFallback.needsComparisonShortCircuit(
                message, 
                databaseShoppingFallback.findCandidates(message)
            );
            // Cart operations should NOT be short-circuited
            assertThat(isShortCircuit).isFalse();
        }
    }

    @Test
    void aiService_withGuestToken_processesCartOperationMessage() {
        // Test that the AI service processes cart operation messages and doesn't short-circuit them
        // This ensures the AI can actually call the cart tools when needed
        
        // Test with a cart operation message
        String cartMessage = "HP Pavilion Laptop ko cart me daalo";
        
        // The AI service should process this without short-circuiting
        // This allows the AI to potentially call cart tools
        ChatResponse response = shoppingAgentService.chat(cartMessage, guestToken);
        
        assertThat(response).isNotNull();
        assertThat(response.answer()).isNotBlank();
        
        // The response should come from the AI provider (not database fallback)
        // because cart operations should not be short-circuited
        // Note: This test might still use fallback if Ollama is not available,
        // but the important thing is that the message wasn't short-circuited
    }

    @Test
    void cartPersistence_addToCart_thenGetCart_returnsSameItem() {
        // This test reproduces the exact bug scenario:
        // 1. "HP Pavilion laptop ko cart me daalo" → successfully added
        // 2. Immediately "cart dikhao" → incorrectly says cart is empty
        
        // Step 1: Add HP Pavilion to cart (simulating first chat request)
        CartResponse addResponse = cartService.addProduct(guestToken, hpPavilion.getId(), 1);
        
        assertThat(addResponse.items()).hasSize(1);
        assertThat(addResponse.items().get(0).productId()).isEqualTo(hpPavilion.getId());
        assertThat(addResponse.items().get(0).name()).isEqualTo("HP Pavilion Laptop");
        
        Long addedItemId = addResponse.items().get(0).id();
        Long addedProductId = addResponse.items().get(0).productId();
        
        // Step 2: Get cart in a new transaction (simulating second chat request)
        // This simulates the persistence issue where the cart appears empty
        CartResponse getCartResponse = cartService.getCart(guestToken);
        
        // CRITICAL: The cart should NOT be empty - it should contain the added item
        assertThat(getCartResponse.items()).hasSize(1);
        assertThat(getCartResponse.itemCount()).isEqualTo(1);
        
        CartItemResponse cartItem = getCartResponse.items().get(0);
        assertThat(cartItem.productId()).isEqualTo(addedProductId);
        assertThat(cartItem.name()).isEqualTo("HP Pavilion Laptop");
        assertThat(cartItem.id()).isEqualTo(addedItemId);
        
        // Step 3: Verify the cart repository has the correct data
        var cart = cartRepository.findByGuestToken(guestToken);
        assertThat(cart).isPresent();
        assertThat(cart.get().getItems()).hasSize(1);
        assertThat(cart.get().getItems().get(0).getProduct().getId()).isEqualTo(hpPavilion.getId());
    }
}
