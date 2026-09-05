package com.jatin.ai_shopping_agent.controller;

import com.jatin.ai_shopping_agent.entity.Product;
import com.jatin.ai_shopping_agent.repository.ProductRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CartControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void emptyCartIssuesGuestCookie() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(CartController.CART_COOKIE))
                .andExpect(cookie().httpOnly(CartController.CART_COOKIE, true))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    void guestCookiePersistsCartAcrossRequests() throws Exception {
        Product product = productRepository.save(new Product("Cookie Laptop", "Guest cart test", new BigDecimal("12000"), "Electronics"));

        MvcResult added = mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"quantity\":2}".formatted(product.getId())))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(CartController.CART_COOKIE))
                .andExpect(jsonPath("$.itemCount").value(2))
                .andReturn();

        Cookie guestCookie = added.getResponse().getCookie(CartController.CART_COOKIE);
        assertThat(guestCookie).isNotNull();

        mockMvc.perform(get("/api/cart").cookie(guestCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(product.getId()))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.subtotal").value(24000));

        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void missingProductReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":9999999,\"quantity\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found"));
    }

    @Test
    void invalidQuantityReturnsBadRequest() throws Exception {
        Product product = productRepository.save(new Product("Invalid Qty Laptop", "Qty validation", new BigDecimal("10000"), "Electronics"));

        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"quantity\":0}".formatted(product.getId())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"quantity\":100}".formatted(product.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRemoveAndClearWorkThroughHttp() throws Exception {
        Product product = productRepository.save(new Product("Http Laptop", "Controller cart test", new BigDecimal("5000"), "Electronics"));

        MvcResult added = mockMvc.perform(post("/api/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d}".formatted(product.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(1))
                .andReturn();

        Cookie guestCookie = added.getResponse().getCookie(CartController.CART_COOKIE);
        Long itemId = objectId(added, "$.items[0].id");

        mockMvc.perform(patch("/api/cart/items/" + itemId)
                        .cookie(guestCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(3))
                .andExpect(jsonPath("$.subtotal").value(15000));

        mockMvc.perform(delete("/api/cart/items/" + itemId).cookie(guestCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        mockMvc.perform(post("/api/cart/items")
                        .cookie(guestCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":%d,\"quantity\":1}".formatted(product.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/cart").cookie(guestCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.itemCount").value(0));
    }

    private Long objectId(MvcResult result, String jsonPath) throws Exception {
        String body = result.getResponse().getContentAsString();
        com.jayway.jsonpath.JsonPath parsed = com.jayway.jsonpath.JsonPath.compile(jsonPath);
        Number id = parsed.read(body);
        return id.longValue();
    }
}
