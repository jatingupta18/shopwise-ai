package com.jatin.ai_shopping_agent.dto;

import com.jatin.ai_shopping_agent.entity.Product;

import java.math.BigDecimal;
import java.util.Objects;

/** A compact, database-derived product representation safe to return from chat. */
public record ProductSummary(Long id, String name, String description, BigDecimal price, String category) {

    public static ProductSummary from(Product product) {
        return new ProductSummary(product.getId(), product.getName(), product.getDescription(),
                product.getPrice(), product.getCategory());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductSummary that = (ProductSummary) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
