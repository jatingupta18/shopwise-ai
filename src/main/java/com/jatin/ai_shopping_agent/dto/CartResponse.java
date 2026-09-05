package com.jatin.ai_shopping_agent.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(Long id, List<CartItemResponse> items, int itemCount, BigDecimal subtotal) {
}
