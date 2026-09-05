package com.jatin.ai_shopping_agent.ai;

import com.jatin.ai_shopping_agent.dto.CartItemResponse;
import com.jatin.ai_shopping_agent.dto.CartResponse;
import com.jatin.ai_shopping_agent.dto.ProductSummary;
import com.jatin.ai_shopping_agent.entity.Product;
import com.jatin.ai_shopping_agent.service.CartService;
import com.jatin.ai_shopping_agent.service.ProductService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Tools exposed to the model. Every result comes directly from ProductService or CartService. */
@Component
public class ProductTools {

    private final ProductService productService;
    private final CartService cartService;

    public ProductTools(ProductService productService, CartService cartService) {
        this.productService = productService;
        this.cartService = cartService;
    }

    @Tool(description = "Search the store catalog for products whose name contains the supplied text.")
    public List<ProductSummary> searchProductsByName(ProductNameSearch request) {
        return productService.searchProducts(request.name()).stream().map(ProductSummary::from).toList();
    }

    @Tool(description = "Find all products in an exact product category from the store catalog.")
    public List<ProductSummary> searchProductsByCategory(ProductCategorySearch request) {
        return productService.getProductsByCategory(request.category()).stream().map(ProductSummary::from).toList();
    }

    @Tool(description = "Find products whose database price is at or below the requested maximum budget.")
    public List<ProductSummary> findProductsWithinBudget(ProductBudgetSearch request) {
        return productService.getProductsWithinBudget(request.maximumPrice()).stream().map(ProductSummary::from).toList();
    }

    @Tool(description = "Retrieve exact database details for one product ID. Use this before describing a product.")
    public ProductSummary getProductDetails(ProductIdRequest request) {
        Product product = productService.getProductById(request.productId());
        return product == null ? null : ProductSummary.from(product);
    }

    @Tool(description = "Retrieve exact database details for several product IDs so they can be compared. Do not compare products not returned by this tool.")
    public List<ProductSummary> compareProducts(ProductComparisonRequest request) {
        return request.productIds().stream()
                .map(productService::getProductById)
                .filter(Objects::nonNull)
                .map(ProductSummary::from)
                .toList();
    }

    @Tool(description = "Add a product to the user's shopping cart. You MUST use the guest token provided in the system prompt. Required parameters: guestToken (string), productId (long), quantity (int).")
    public CartResponse addToCart(AddToCartRequest request) {
        return cartService.addProduct(request.guestToken(), request.productId(), request.quantity());
    }

    @Tool(description = "Get the current contents of the user's shopping cart. You MUST use the guest token provided in the system prompt. Required parameter: guestToken (string). Returns cart items with their itemId (for removal) and productId (for reference).")
    public CartResponse getCart(GetCartRequest request) {
        return cartService.getCart(request.guestToken());
    }

    @Tool(description = "Update the quantity of an item in the user's shopping cart. You MUST use the guest token provided in the system prompt. Required parameters: guestToken (string), itemId (long), quantity (int).")
    public CartResponse updateCartItemQuantity(UpdateQuantityRequest request) {
        return cartService.updateQuantity(request.guestToken(), request.itemId(), request.quantity());
    }

    @Tool(description = "Remove an item from the user's shopping cart. You MUST use the guest token provided in the system prompt. REQUIRED: Use the itemId from the getCart response, NOT the productId. Required parameters: guestToken (string), itemId (long). The itemId is the cart item identifier, different from the product catalog ID.")
    public CartResponse removeCartItem(RemoveItemRequest request) {
        return cartService.removeItem(request.guestToken(), request.itemId());
    }

    @Tool(description = "Clear all items from the user's shopping cart. You MUST use the guest token provided in the system prompt. Required parameter: guestToken (string).")
    public CartResponse clearCart(ClearCartRequest request) {
        return cartService.clearCart(request.guestToken());
    }

    public record ProductNameSearch(String name) { }
    public record ProductCategorySearch(String category) { }
    public record ProductBudgetSearch(BigDecimal maximumPrice) { }
    public record ProductIdRequest(Long productId) { }
    public record ProductComparisonRequest(List<Long> productIds) { }
    public record AddToCartRequest(String guestToken, Long productId, int quantity) { }
    public record GetCartRequest(String guestToken) { }
    public record UpdateQuantityRequest(String guestToken, Long itemId, int quantity) { }
    public record RemoveItemRequest(String guestToken, Long itemId) { }
    public record ClearCartRequest(String guestToken) { }
}
