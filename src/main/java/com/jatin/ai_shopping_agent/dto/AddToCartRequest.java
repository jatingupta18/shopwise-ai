package com.jatin.ai_shopping_agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddToCartRequest(
        @NotNull(message = "Product id is required")
        Long productId,
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 99, message = "Quantity must be at most 99")
        Integer quantity) {

    public int resolvedQuantity() {
        return quantity == null ? 1 : quantity;
    }
}
