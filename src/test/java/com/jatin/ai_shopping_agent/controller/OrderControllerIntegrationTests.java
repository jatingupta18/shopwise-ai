package com.jatin.ai_shopping_agent.controller;

import com.jatin.ai_shopping_agent.dto.CheckoutRequest;
import com.jatin.ai_shopping_agent.entity.Cart;
import com.jatin.ai_shopping_agent.entity.CartItem;
import com.jatin.ai_shopping_agent.entity.Order;
import com.jatin.ai_shopping_agent.entity.Product;
import com.jatin.ai_shopping_agent.repository.CartItemRepository;
import com.jatin.ai_shopping_agent.repository.CartRepository;
import com.jatin.ai_shopping_agent.repository.OrderRepository;
import com.jatin.ai_shopping_agent.repository.ProductRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    private Cart testCart;
    private Product testProduct;
    private CheckoutRequest checkoutRequest;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();

        testProduct = new Product();
        testProduct.setName("Test Product");
        testProduct.setCategory("Electronics");
        testProduct.setPrice(new BigDecimal("999.99"));
        testProduct.setDescription("Test product description");
        testProduct = productRepository.save(testProduct);

        testCart = new Cart();
        testCart.setGuestToken("test-token");
        testCart = cartRepository.save(testCart);

        CartItem cartItem = new CartItem();
        cartItem.setCart(testCart);
        cartItem.setProduct(testProduct);
        cartItem.setQuantity(2);
        cartItem = cartItemRepository.save(cartItem);

        testCart.setItems(new java.util.ArrayList<>());
        testCart.getItems().add(cartItem);
        testCart = cartRepository.save(testCart);

        checkoutRequest = new CheckoutRequest(
            "John Doe",
            "john.doe@example.com",
            "9876543210",
            "123 Test Street",
            "Test City",
            "Test State",
            "123456"
        );
    }

    @Test
    void checkout_SuccessfulOrder() throws Exception {
        String requestBody = String.format(
            "{\"fullName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"address\":\"%s\",\"city\":\"%s\",\"state\":\"%s\",\"pinCode\":\"%s\"}",
            checkoutRequest.fullName(),
            checkoutRequest.email(),
            checkoutRequest.phone(),
            checkoutRequest.address(),
            checkoutRequest.city(),
            checkoutRequest.state(),
            checkoutRequest.pinCode()
        );

        mockMvc.perform(post("/api/orders/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .cookie(new Cookie("shopwise_cart", "test-token")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderNumber").exists())
            .andExpect(jsonPath("$.customerName").value("John Doe"))
            .andExpect(jsonPath("$.customerEmail").value("john.doe@example.com"))
            .andExpect(jsonPath("$.customerPhone").value("9876543210"))
            .andExpect(jsonPath("$.customerAddress").value("123 Test Street"))
            .andExpect(jsonPath("$.customerCity").value("Test City"))
            .andExpect(jsonPath("$.customerState").value("Test State"))
            .andExpect(jsonPath("$.customerPinCode").value("123456"))
            .andExpect(jsonPath("$.totalAmount").value(1999.98))
            .andExpect(jsonPath("$.itemCount").value(2))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void checkout_EmptyCartReturnsBadRequest() throws Exception {
        Cart emptyCart = new Cart();
        emptyCart.setGuestToken("empty-token");
        emptyCart = cartRepository.save(emptyCart);

        String requestBody = String.format(
            "{\"fullName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"address\":\"%s\",\"city\":\"%s\",\"state\":\"%s\",\"pinCode\":\"%s\"}",
            checkoutRequest.fullName(),
            checkoutRequest.email(),
            checkoutRequest.phone(),
            checkoutRequest.address(),
            checkoutRequest.city(),
            checkoutRequest.state(),
            checkoutRequest.pinCode()
        );

        mockMvc.perform(post("/api/orders/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .cookie(new Cookie("shopwise_cart", "empty-token")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Cannot create order: cart is empty"));
    }

    @Test
    void checkout_InvalidRequestReturnsBadRequest() throws Exception {
        String invalidRequest = "{\"fullName\":\"\",\"email\":\"invalid-email\",\"phone\":\"123\",\"address\":\"\",\"city\":\"\",\"state\":\"\",\"pinCode\":\"123\"}";

        mockMvc.perform(post("/api/orders/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest)
                .cookie(new Cookie("shopwise_cart", "test-token")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void checkout_MissingCookieReturnsNotFound() throws Exception {
        String requestBody = String.format(
            "{\"fullName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"address\":\"%s\",\"city\":\"%s\",\"state\":\"%s\",\"pinCode\":\"%s\"}",
            checkoutRequest.fullName(),
            checkoutRequest.email(),
            checkoutRequest.phone(),
            checkoutRequest.address(),
            checkoutRequest.city(),
            checkoutRequest.state(),
            checkoutRequest.pinCode()
        );

        mockMvc.perform(post("/api/orders/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isNotFound());
    }

    @Test
    void checkout_CartClearedAfterOrder() throws Exception {
        String requestBody = String.format(
            "{\"fullName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"address\":\"%s\",\"city\":\"%s\",\"state\":\"%s\",\"pinCode\":\"%s\"}",
            checkoutRequest.fullName(),
            checkoutRequest.email(),
            checkoutRequest.phone(),
            checkoutRequest.address(),
            checkoutRequest.city(),
            checkoutRequest.state(),
            checkoutRequest.pinCode()
        );

        mockMvc.perform(post("/api/orders/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .cookie(new Cookie("shopwise_cart", "test-token")))
            .andExpect(status().isOk());

        Cart updatedCart = cartRepository.findByGuestToken("test-token").orElse(null);
        assertNotNull(updatedCart);
        assertTrue(updatedCart.getItems().isEmpty());
    }

    @Test
    void checkout_OrderPersistedCorrectly() throws Exception {
        String requestBody = String.format(
            "{\"fullName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"address\":\"%s\",\"city\":\"%s\",\"state\":\"%s\",\"pinCode\":\"%s\"}",
            checkoutRequest.fullName(),
            checkoutRequest.email(),
            checkoutRequest.phone(),
            checkoutRequest.address(),
            checkoutRequest.city(),
            checkoutRequest.state(),
            checkoutRequest.pinCode()
        );

        mockMvc.perform(post("/api/orders/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .cookie(new Cookie("shopwise_cart", "test-token")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderNumber").exists())
            .andExpect(jsonPath("$.customerName").value("John Doe"))
            .andExpect(jsonPath("$.totalAmount").value(1999.98));
    }

    @Test
    void checkout_OrderItemsSnapshottedCorrectly() throws Exception {
        String requestBody = String.format(
            "{\"fullName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"address\":\"%s\",\"city\":\"%s\",\"state\":\"%s\",\"pinCode\":\"%s\"}",
            checkoutRequest.fullName(),
            checkoutRequest.email(),
            checkoutRequest.phone(),
            checkoutRequest.address(),
            checkoutRequest.city(),
            checkoutRequest.state(),
            checkoutRequest.pinCode()
        );

        mockMvc.perform(post("/api/orders/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .cookie(new Cookie("shopwise_cart", "test-token")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].productName").value("Test Product"))
            .andExpect(jsonPath("$.items[0].productCategory").value("Electronics"))
            .andExpect(jsonPath("$.items[0].quantity").value(2))
            .andExpect(jsonPath("$.items[0].unitPrice").value(999.99))
            .andExpect(jsonPath("$.items[0].lineTotal").value(1999.98));
    }

    @Test
    void getOrdersByGuestToken_ReturnsOrders() throws Exception {
        mockMvc.perform(post("/api/orders/checkout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format(
                    "{\"fullName\":\"%s\",\"email\":\"%s\",\"phone\":\"%s\",\"address\":\"%s\",\"city\":\"%s\",\"state\":\"%s\",\"pinCode\":\"%s\"}",
                    checkoutRequest.fullName(), checkoutRequest.email(), checkoutRequest.phone(),
                    checkoutRequest.address(), checkoutRequest.city(), checkoutRequest.state(), checkoutRequest.pinCode()
                ))
                .cookie(new Cookie("shopwise_cart", "test-token")))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders")
                .cookie(new Cookie("shopwise_cart", "test-token")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].orderNumber").exists())
            .andExpect(jsonPath("$[0].customerName").value("John Doe"));
    }

    @Test
    void getOrdersByGuestToken_MissingCookieReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/orders"))
            .andExpect(status().isNotFound());
    }
}
