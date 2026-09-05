package com.jatin.ai_shopping_agent.repository;

import com.jatin.ai_shopping_agent.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.math.BigDecimal;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCategory(String category);
    List<Product> findByNameContainingIgnoreCase(String name);
    List<Product> findByPriceLessThanEqualOrderByPriceAsc(BigDecimal maximumPrice);
}
