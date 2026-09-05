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
}
