package com.jatin.ai_shopping_agent.config;

import com.jatin.ai_shopping_agent.entity.Product;
import com.jatin.ai_shopping_agent.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@Profile("!test")
public class DataLoader implements CommandLineRunner {

    private final ProductRepository productRepository;

    public DataLoader(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (productRepository.count() == 0) {
            productRepository.save(new Product("Dell Inspiron Laptop", "15.6-inch FHD, Intel i5, 8GB RAM, 512GB SSD", new BigDecimal("55000"), "Electronics"));
            productRepository.save(new Product("HP Pavilion Laptop", "14-inch HD, AMD Ryzen 5, 16GB RAM, 1TB SSD", new BigDecimal("52000"), "Electronics"));
            productRepository.save(new Product("Lenovo IdeaPad", "15.6-inch FHD, Intel i3, 8GB RAM, 256GB SSD", new BigDecimal("45000"), "Electronics"));
            productRepository.save(new Product("Asus VivoBook", "14-inch FHD, Intel i5, 8GB RAM, 512GB SSD", new BigDecimal("58000"), "Electronics"));
            productRepository.save(new Product("Acer Aspire", "15.6-inch HD, AMD Ryzen 3, 4GB RAM, 1TB HDD", new BigDecimal("42000"), "Electronics"));
            productRepository.save(new Product("MacBook Air M1", "13.3-inch Retina, Apple M1, 8GB RAM, 256GB SSD", new BigDecimal("85000"), "Electronics"));
            productRepository.save(new Product("Samsung Galaxy S23", "6.1-inch AMOLED, 8GB RAM, 128GB Storage", new BigDecimal("65000"), "Electronics"));
            productRepository.save(new Product("OnePlus 11", "6.7-inch AMOLED, 12GB RAM, 256GB Storage", new BigDecimal("56000"), "Electronics"));
            productRepository.save(new Product("Google Pixel 7", "6.3-inch OLED, 8GB RAM, 128GB Storage", new BigDecimal("49000"), "Electronics"));
            productRepository.save(new Product("Sony WH-1000XM4", "Noise-canceling headphones, 30hr battery", new BigDecimal("25000"), "Electronics"));
            productRepository.save(new Product("Bose QuietComfort 45", "Noise-canceling headphones, 24hr battery", new BigDecimal("28000"), "Electronics"));
            productRepository.save(new Product("JBL Flip 6", "Portable Bluetooth speaker, waterproof", new BigDecimal("9000"), "Electronics"));
        }
    }
}
