package com.jatin.ai_shopping_agent.repository;

import com.jatin.ai_shopping_agent.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByGuestTokenOrderByCreatedAtDesc(String guestToken);
}