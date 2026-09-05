package com.jatin.ai_shopping_agent.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNumber,
        String customerName,
        String customerEmail,
        String customerPhone,
        String customerAddress,
        String customerCity,
        String customerState,
        String customerPinCode,
        BigDecimal totalAmount,
        int itemCount,
        String status,
        Instant createdAt,
        List<OrderItemResponse> items
) {
}