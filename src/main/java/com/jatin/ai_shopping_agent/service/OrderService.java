package com.jatin.ai_shopping_agent.service;

import com.jatin.ai_shopping_agent.dto.CartItemResponse;
import com.jatin.ai_shopping_agent.dto.CartResponse;
import com.jatin.ai_shopping_agent.dto.CheckoutRequest;
import com.jatin.ai_shopping_agent.dto.OrderItemResponse;
import com.jatin.ai_shopping_agent.dto.OrderResponse;
import com.jatin.ai_shopping_agent.entity.Cart;
import com.jatin.ai_shopping_agent.entity.CartItem;
import com.jatin.ai_shopping_agent.entity.Order;
import com.jatin.ai_shopping_agent.entity.OrderItem;
import com.jatin.ai_shopping_agent.entity.Product;
import com.jatin.ai_shopping_agent.exception.EmptyCartException;
import com.jatin.ai_shopping_agent.repository.OrderItemRepository;
import com.jatin.ai_shopping_agent.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartService cartService;
    private final ProductService productService;

    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                         CartService cartService, ProductService productService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartService = cartService;
        this.productService = productService;
    }

    @Transactional
    public OrderResponse createOrder(String guestToken, CheckoutRequest checkoutRequest) {
        // Get current cart
        CartResponse cart = cartService.getCart(guestToken);

        // Validate cart is not empty
        if (cart.items() == null || cart.items().isEmpty()) {
            throw new EmptyCartException("Cannot create order: cart is empty");
        }

        // Create order
        Order order = new Order();
        order.setCustomerName(checkoutRequest.fullName());
        order.setCustomerEmail(checkoutRequest.email());
        order.setCustomerPhone(checkoutRequest.phone());
        order.setCustomerAddress(checkoutRequest.address());
        order.setCustomerCity(checkoutRequest.city());
        order.setCustomerState(checkoutRequest.state());
        order.setCustomerPinCode(checkoutRequest.pinCode());
        order.setGuestToken(guestToken);
        order.setStatus(Order.OrderStatus.PENDING);

        // Calculate totals and create order items
        BigDecimal totalAmount = BigDecimal.ZERO;
        int totalItemCount = 0;

        for (CartItemResponse cartItem : cart.items()) {
            Product product = productService.getProductById(cartItem.productId());
            if (product == null) {
                continue;
            }

            BigDecimal unitPrice = cartItem.unitPrice() != null ? cartItem.unitPrice() : BigDecimal.ZERO;
            BigDecimal lineTotal = cartItem.lineTotal() != null ? cartItem.lineTotal() : BigDecimal.ZERO;

            OrderItem orderItem = new OrderItem(order, product, cartItem.quantity(), unitPrice, lineTotal);
            order.addItem(orderItem);

            totalAmount = totalAmount.add(lineTotal);
            totalItemCount += cartItem.quantity();
        }

        order.setTotalAmount(totalAmount);
        order.setItemCount(totalItemCount);

        // Save order
        Order savedOrder = orderRepository.save(order);

        // Clear the cart after successful order creation
        cartService.clearCart(guestToken);

        return toResponse(savedOrder);
    }

    @Transactional
    public OrderResponse getOrderByOrderNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        return toResponse(order);
    }

    @Transactional
    public List<OrderResponse> getOrdersByGuestToken(String guestToken) {
        List<Order> orders = orderRepository.findByGuestTokenOrderByCreatedAtDesc(guestToken);
        List<OrderResponse> responses = new ArrayList<>();
        for (Order order : orders) {
            responses.add(toResponse(order));
        }
        return responses;
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = new ArrayList<>();

        for (OrderItem item : order.getItems()) {
            items.add(new OrderItemResponse(
                    item.getId(),
                    item.getProductName(),
                    item.getProductCategory(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getLineTotal()
            ));
        }

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getCustomerPhone(),
                order.getCustomerAddress(),
                order.getCustomerCity(),
                order.getCustomerState(),
                order.getCustomerPinCode(),
                order.getTotalAmount(),
                order.getItemCount(),
                order.getStatus().name(),
                order.getCreatedAt(),
                items
        );
    }
}