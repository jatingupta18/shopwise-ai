package com.jatin.ai_shopping_agent.service;

import com.jatin.ai_shopping_agent.entity.Product;
import com.jatin.ai_shopping_agent.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.math.BigDecimal;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id).orElse(null);
    }

    public Product saveProduct(Product product) {
        return productRepository.save(product);
    }
    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategory(category);
    }
    public List<Product> searchProducts(String name) {
        return productRepository.findByNameContainingIgnoreCase(name);
    }

    public List<Product> getProductsWithinBudget(BigDecimal maximumPrice) {
        return productRepository.findByPriceLessThanEqualOrderByPriceAsc(maximumPrice);
    }
}
