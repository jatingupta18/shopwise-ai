package com.jatin.ai_shopping_agent.service;

import com.jatin.ai_shopping_agent.dto.CartItemResponse;
import com.jatin.ai_shopping_agent.dto.CartResponse;
import com.jatin.ai_shopping_agent.entity.Cart;
import com.jatin.ai_shopping_agent.entity.CartItem;
import com.jatin.ai_shopping_agent.entity.Product;
import com.jatin.ai_shopping_agent.exception.InvalidCartQuantityException;
import com.jatin.ai_shopping_agent.exception.ResourceNotFoundException;
import com.jatin.ai_shopping_agent.repository.CartItemRepository;
import com.jatin.ai_shopping_agent.repository.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CartService {

    public static final int MIN_QUANTITY = 1;
    public static final int MAX_QUANTITY = 99;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductService productService;

    public CartService(CartRepository cartRepository, CartItemRepository cartItemRepository,
                       ProductService productService) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productService = productService;
    }

    @Transactional
    public CartResponse getCart(String guestToken) {
        return toResponse(getOrCreateCart(guestToken));
    }

    @Transactional
    public CartResponse addProduct(String guestToken, Long productId, int quantity) {
        validateQuantity(quantity);
        Product product = requireProduct(productId);
        Cart cart = getOrCreateCart(guestToken);

        CartItem item = cartItemRepository.findByCartAndProduct(cart, product).orElse(null);
        if (item == null) {
            item = new CartItem(cart, product, quantity);
            cart.addItem(item);
        } else {
            item.setQuantity(cappedQuantity(item.getQuantity() + quantity));
        }
        cart.touch();
        return toResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse updateQuantity(String guestToken, Long itemId, int quantity) {
        validateQuantity(quantity);
        Cart cart = getOrCreateCart(guestToken);
        CartItem item = requireItem(cart, itemId);
        item.setQuantity(quantity);
        cart.touch();
        return toResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse removeItem(String guestToken, Long itemId) {
        Cart cart = getOrCreateCart(guestToken);
        CartItem item = requireItem(cart, itemId);
        cart.getItems().remove(item);
        cart.touch();
        return toResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse clearCart(String guestToken) {
        Cart cart = getOrCreateCart(guestToken);
        cart.getItems().clear();
        cart.touch();
        return toResponse(cartRepository.save(cart));
    }

    Cart getOrCreateCart(String guestToken) {
        return cartRepository.findByGuestToken(guestToken)
                .orElseGet(() -> cartRepository.save(new Cart(guestToken)));
    }

    private Product requireProduct(Long productId) {
        Product product = productService.getProductById(productId);
        if (product == null) {
            throw new ResourceNotFoundException("Product not found");
        }
        return product;
    }

    private CartItem requireItem(Cart cart, Long itemId) {
        return cartItemRepository.findByIdAndCart(itemId, cart)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));
    }

    private void validateQuantity(int quantity) {
        if (quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            throw new InvalidCartQuantityException("Quantity must be between %d and %d".formatted(MIN_QUANTITY, MAX_QUANTITY));
        }
    }

    private int cappedQuantity(int quantity) {
        return Math.min(MAX_QUANTITY, Math.max(MIN_QUANTITY, quantity));
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = new ArrayList<>();
        int itemCount = 0;
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CartItem item : cart.getItems()) {
            Product product = item.getProduct();
            if (product == null) {
                continue;
            }
            BigDecimal unitPrice = product.getPrice() == null ? BigDecimal.ZERO : product.getPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            items.add(new CartItemResponse(
                    item.getId(),
                    product.getId(),
                    product.getName(),
                    product.getDescription(),
                    product.getCategory(),
                    item.getQuantity(),
                    unitPrice,
                    lineTotal));
            itemCount += item.getQuantity();
            subtotal = subtotal.add(lineTotal);
        }

        return new CartResponse(cart.getId(), items, itemCount, subtotal);
    }
}
