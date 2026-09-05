package com.jatin.ai_shopping_agent.service;

import com.jatin.ai_shopping_agent.dto.CheckoutRequest;
import com.jatin.ai_shopping_agent.dto.OrderResponse;
import com.jatin.ai_shopping_agent.entity.Cart;
import com.jatin.ai_shopping_agent.entity.CartItem;
import com.jatin.ai_shopping_agent.entity.Order;
import com.jatin.ai_shopping_agent.entity.OrderItem;
import com.jatin.ai_shopping_agent.entity.Product;
import com.jatin.ai_shopping_agent.exception.EmptyCartException;
import com.jatin.ai_shopping_agent.repository.CartItemRepository;
import com.jatin.ai_shopping_agent.repository.CartRepository;
import com.jatin.ai_shopping_agent.repository.OrderItemRepository;
import com.jatin.ai_shopping_agent.repository.OrderRepository;
import com.jatin.ai_shopping_agent.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderServiceIntegrationTests {

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductRepository productRepository;

    private Cart testCart;
    private Product testProduct;
    private CheckoutRequest checkoutRequest;

    @BeforeEach
    void setUp() {
        orderItemRepository.deleteAll();
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
    void createOrder_SuccessfulOrderCreation() {
        OrderResponse response = orderService.createOrder("test-token", checkoutRequest);

        assertNotNull(response);
        assertNotNull(response.orderNumber());
        assertEquals("John Doe", response.customerName());
        assertEquals("john.doe@example.com", response.customerEmail());
        assertEquals("9876543210", response.customerPhone());
        assertEquals("123 Test Street", response.customerAddress());
        assertEquals("Test City", response.customerCity());
        assertEquals("Test State", response.customerState());
        assertEquals("123456", response.customerPinCode());
        assertEquals(new BigDecimal("1999.98"), response.totalAmount());
        assertEquals(2, response.itemCount());
        assertEquals("PENDING", response.status());
        assertNotNull(response.items());
        assertEquals(1, response.items().size());
    }

    @Test
    void createOrder_EmptyCartThrowsException() {
        Cart emptyCart = new Cart();
        emptyCart.setGuestToken("empty-token");
        emptyCart = cartRepository.save(emptyCart);

        EmptyCartException exception = assertThrows(EmptyCartException.class, () -> {
            orderService.createOrder("empty-token", checkoutRequest);
        });

        assertEquals("Cannot create order: cart is empty", exception.getMessage());
    }

    @Test
    void createOrder_CartClearedAfterOrderCreation() {
        orderService.createOrder("test-token", checkoutRequest);

        Cart updatedCart = cartRepository.findByGuestToken("test-token").orElse(null);
        assertNotNull(updatedCart);
        assertTrue(updatedCart.getItems().isEmpty());
    }

    @Test
    void createOrder_OrderItemsSnapshottedCorrectly() {
        OrderResponse response = orderService.createOrder("test-token", checkoutRequest);

        assertNotNull(response.items());
        assertEquals(1, response.items().size());

        var itemResponse = response.items().get(0);
        assertNotNull(itemResponse.id());
        assertEquals("Test Product", itemResponse.productName());
        assertEquals("Electronics", itemResponse.productCategory());
        assertEquals(2, itemResponse.quantity());
        assertEquals(new BigDecimal("999.99"), itemResponse.unitPrice());
        assertEquals(new BigDecimal("1999.98"), itemResponse.lineTotal());
    }

    @Test
    void createOrder_OrderPersistedInDatabase() {
        OrderResponse response = orderService.createOrder("test-token", checkoutRequest);

        Order order = orderRepository.findByOrderNumber(response.orderNumber()).orElse(null);
        assertNotNull(order);
        assertEquals(response.orderNumber(), order.getOrderNumber());
        assertEquals("John Doe", order.getCustomerName());
        assertEquals("john.doe@example.com", order.getCustomerEmail());
        assertEquals("9876543210", order.getCustomerPhone());
        assertEquals("123 Test Street", order.getCustomerAddress());
        assertEquals("Test City", order.getCustomerCity());
        assertEquals("Test State", order.getCustomerState());
        assertEquals("123456", order.getCustomerPinCode());
        assertEquals(new BigDecimal("1999.98"), order.getTotalAmount());
        assertEquals(2, order.getItemCount());
        assertEquals(Order.OrderStatus.PENDING, order.getStatus());
        assertEquals("test-token", order.getGuestToken());
        assertNotNull(order.getCreatedAt());
        assertNotNull(order.getUpdatedAt());
    }

    @Test
    void createOrder_MultipleItemsInCart() {
        Product product2 = new Product();
        product2.setName("Test Product 2");
        product2.setCategory("Books");
        product2.setPrice(new BigDecimal("499.99"));
        product2.setDescription("Second test product");
        product2 = productRepository.save(product2);

        CartItem cartItem2 = new CartItem();
        cartItem2.setCart(testCart);
        cartItem2.setProduct(product2);
        cartItem2.setQuantity(1);
        cartItem2 = cartItemRepository.save(cartItem2);

        testCart.getItems().add(cartItem2);
        testCart = cartRepository.save(testCart);

        OrderResponse response = orderService.createOrder("test-token", checkoutRequest);

        assertNotNull(response);
        assertEquals(3, response.itemCount());
        assertEquals(new BigDecimal("2499.97"), response.totalAmount());
        assertEquals(2, response.items().size());
    }

    @Test
    void getOrdersByGuestToken_ReturnsOrders() {
        orderService.createOrder("test-token", checkoutRequest);

        var orders = orderService.getOrdersByGuestToken("test-token");

        assertNotNull(orders);
        assertEquals(1, orders.size());
        assertTrue(orders.get(0).orderNumber().startsWith("ORD-"));
    }

    @Test
    void getOrdersByGuestToken_EmptyReturnsEmptyList() {
        var orders = orderService.getOrdersByGuestToken("nonexistent-token");

        assertNotNull(orders);
        assertTrue(orders.isEmpty());
    }

    @Test
    void getOrdersByGuestToken_ReturnsOrdersForToken() {
        // Create an order
        OrderResponse response = orderService.createOrder("test-token", checkoutRequest);

        // Query for orders with the guest token
        var orders = orderService.getOrdersByGuestToken("test-token");

        assertNotNull(orders);
        assertEquals(1, orders.size());
        assertEquals(response.orderNumber(), orders.get(0).orderNumber());
        assertTrue(orders.get(0).orderNumber().startsWith("ORD-"));
    }

}
