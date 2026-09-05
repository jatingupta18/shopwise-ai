package com.jatin.ai_shopping_agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "Message is required")
        @Size(max = 2_000, message = "Message must be at most 2000 characters")
        String message) {
}
