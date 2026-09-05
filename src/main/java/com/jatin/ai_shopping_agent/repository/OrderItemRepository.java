package com.jatin.ai_shopping_agent.repository;

import com.jatin.ai_shopping_agent.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}