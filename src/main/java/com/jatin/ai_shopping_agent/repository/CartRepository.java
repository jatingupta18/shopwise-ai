package com.jatin.ai_shopping_agent.repository;

import com.jatin.ai_shopping_agent.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByGuestToken(String guestToken);
}
