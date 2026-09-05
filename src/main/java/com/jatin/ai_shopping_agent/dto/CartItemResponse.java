package com.jatin.ai_shopping_agent.dto;

import java.math.BigDecimal;

public record CartItemResponse(
        Long id,
        Long productId,
        String name,
        String description,
        String category,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal) {
}
