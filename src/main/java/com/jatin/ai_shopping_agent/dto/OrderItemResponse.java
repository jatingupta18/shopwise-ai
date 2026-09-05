package com.jatin.ai_shopping_agent.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        String productName,
        String productCategory,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {
}