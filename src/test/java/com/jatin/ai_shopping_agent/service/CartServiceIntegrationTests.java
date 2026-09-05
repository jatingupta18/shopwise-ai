package com.jatin.ai_shopping_agent.service;

import com.jatin.ai_shopping_agent.dto.CartResponse;
import com.jatin.ai_shopping_agent.entity.Product;
import com.jatin.ai_shopping_agent.exception.InvalidCartQuantityException;
import com.jatin.ai_shopping_agent.exception.ResourceNotFoundException;
import com.jatin.ai_shopping_agent.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CartServiceIntegrationTests {

    @Autowired
    private CartService cartService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void emptyCartHasNoItemsAndZeroSubtotal() {
        CartResponse cart = cartService.getCart(newGuestToken());

        assertThat(cart.items()).isEmpty();
        assertThat(cart.itemCount()).isZero();
        assertThat(cart.subtotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(cart.id()).isNotNull();
    }

    @Test
    void addProductCreatesCartLineFromCatalogPrice() {
        Product laptop = saveLaptop("Office Laptop", "45000");

        CartResponse cart = cartService.addProduct(newGuestToken(), laptop.getId(), 1);

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().getFirst().productId()).isEqualTo(laptop.getId());
        assertThat(cart.items().getFirst().name()).isEqualTo("Office Laptop");
        assertThat(cart.items().getFirst().quantity()).isEqualTo(1);
        assertThat(cart.items().getFirst().unitPrice()).isEqualByComparingTo("45000");
        assertThat(cart.items().getFirst().lineTotal()).isEqualByComparingTo("45000");
        assertThat(cart.itemCount()).isEqualTo(1);
        assertThat(cart.subtotal()).isEqualByComparingTo("45000");
    }

    @Test
    void duplicateProductMergesQuantity() {
        Product laptop = saveLaptop("Budget Laptop", "30000");
        String guestToken = newGuestToken();

        cartService.addProduct(guestToken, laptop.getId(), 1);
        CartResponse cart = cartService.addProduct(guestToken, laptop.getId(), 2);

        assertThat(cart.items()).hasSize(1);
        assertThat(cart.items().getFirst().quantity()).isEqualTo(3);
        assertThat(cart.itemCount()).isEqualTo(3);
        assertThat(cart.subtotal()).isEqualByComparingTo("90000");
    }

    @Test
    void updateQuantityChangesLineAndSubtotal() {
        Product laptop = saveLaptop("Work Laptop", "20000");
        String guestToken = newGuestToken();
        CartResponse added = cartService.addProduct(guestToken, laptop.getId(), 1);

        CartResponse cart = cartService.updateQuantity(guestToken, added.items().getFirst().id(), 4);

        assertThat(cart.items().getFirst().quantity()).isEqualTo(4);
        assertThat(cart.itemCount()).isEqualTo(4);
        assertThat(cart.subtotal()).isEqualByComparingTo("80000");
    }

    @Test
    void removeItemDeletesTheLine() {
        Product first = saveLaptop("First Laptop", "10000");
        Product second = saveLaptop("Second Laptop", "25000");
        String guestToken = newGuestToken();
        CartResponse added = cartService.addProduct(guestToken, first.getId(), 1);
        cartService.addProduct(guestToken, second.getId(), 1);

        CartResponse cart = cartService.removeItem(guestToken, added.items().getFirst().id());

        assertThat(cart.items()).extracting(item -> item.name()).containsExactly("Second Laptop");
        assertThat(cart.subtotal()).isEqualByComparingTo("25000");
    }

    @Test
    void clearCartRemovesAllItems() {
        Product laptop = saveLaptop("Clear Laptop", "15000");
        String guestToken = newGuestToken();
        cartService.addProduct(guestToken, laptop.getId(), 2);

        CartResponse cart = cartService.clearCart(guestToken);

        assertThat(cart.items()).isEmpty();
        assertThat(cart.itemCount()).isZero();
        assertThat(cart.subtotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void missingProductIsRejected() {
        assertThatThrownBy(() -> cartService.addProduct(newGuestToken(), 9_999_999L, 1))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found");
    }

    @Test
    void invalidQuantityIsRejected() {
        Product laptop = saveLaptop("Qty Laptop", "10000");
        String guestToken = newGuestToken();

        assertThatThrownBy(() -> cartService.addProduct(guestToken, laptop.getId(), 0))
                .isInstanceOf(InvalidCartQuantityException.class);
        assertThatThrownBy(() -> cartService.addProduct(guestToken, laptop.getId(), 100))
                .isInstanceOf(InvalidCartQuantityException.class);
    }

    @Test
    void subtotalUsesCurrentCatalogPriceTimesQuantity() {
        Product laptop = saveLaptop("Priced Laptop", "58000");
        String guestToken = newGuestToken();

        CartResponse cart = cartService.addProduct(guestToken, laptop.getId(), 2);

        assertThat(cart.items().getFirst().unitPrice()).isEqualByComparingTo("58000");
        assertThat(cart.items().getFirst().lineTotal()).isEqualByComparingTo("116000");
        assertThat(cart.subtotal()).isEqualByComparingTo("116000");
    }

    private Product saveLaptop(String name, String price) {
        return productRepository.save(new Product(name, "Portable work computer", new BigDecimal(price), "Electronics"));
    }

    private String newGuestToken() {
        return UUID.randomUUID().toString();
    }
}
