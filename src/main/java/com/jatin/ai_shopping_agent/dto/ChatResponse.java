package com.jatin.ai_shopping_agent.dto;

import java.util.List;

public record ChatResponse(String answer, List<ProductSummary> products, String provider, boolean fallbackUsed) {
}
