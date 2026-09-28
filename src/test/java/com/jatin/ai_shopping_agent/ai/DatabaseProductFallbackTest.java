package com.jatin.ai_shopping_agent.ai;

import com.jatin.ai_shopping_agent.dto.ChatResponse;
import com.jatin.ai_shopping_agent.dto.ProductSummary;
import com.jatin.ai_shopping_agent.entity.Product;
import com.jatin.ai_shopping_agent.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseProductFallbackTest {

    @Mock
    private ProductService productService;

    private DatabaseProductFallback fallback;

    @BeforeEach
    void setUp() {
        fallback = new DatabaseProductFallback(productService);
    }

    @Test
    void dellInspironLaptopComparedWithOtherAvailableReturnsOnlyLaptops() {
        List<Product> catalog = createTestCatalog();
        when(productService.getAllProducts()).thenReturn(catalog);

        ChatResponse response = fallback.handle("Compare Dell Inspiron Laptop with other available products").orElseThrow();

        assertThat(response.products()).hasSizeGreaterThan(1);
        List<String> productNames = response.products().stream().map(ProductSummary::name).toList();
        
        assertThat(productNames).contains("Dell Inspiron Laptop");
        assertThat(productNames).anyMatch(name -> name.contains("Laptop") || name.contains("laptop"));
        
        assertThat(productNames).noneMatch(name -> 
            name.contains("JBL") || name.contains("Sony") || name.contains("Bose") ||
            name.contains("Galaxy") || name.contains("Pixel") || name.contains("OnePlus")
        );
    }

    @Test
    void hpPavilionLaptopDoesNotCompareWithJBLSonyBose() {
        List<Product> catalog = createTestCatalog();
        when(productService.getAllProducts()).thenReturn(catalog);

        ChatResponse response = fallback.handle("Compare HP Pavilion Laptop with other available products").orElseThrow();

        List<String> productNames = response.products().stream().map(ProductSummary::name).toList();
        
        assertThat(productNames).contains("HP Pavilion Laptop");
        assertThat(productNames).noneMatch(name -> 
            name.contains("JBL") || name.contains("Sony") || name.contains("Bose")
        );
    }

    @Test
    void phoneComparisonDoesNotIncludeLaptops() {
        List<Product> catalog = createTestCatalog();
        when(productService.getAllProducts()).thenReturn(catalog);

        ChatResponse response = fallback.handle("Compare Samsung Galaxy S23 with other available products").orElseThrow();

        List<String> productNames = response.products().stream().map(ProductSummary::name).toList();
        
        assertThat(productNames).contains("Samsung Galaxy S23");
        assertThat(productNames).noneMatch(name -> name.toLowerCase().contains("laptop"));
    }

    @Test
    void headphonesDoNotCompareWithSpeakers() {
        List<Product> catalog = createTestCatalog();
        when(productService.getAllProducts()).thenReturn(catalog);

        ChatResponse response = fallback.handle("Compare Sony WH-1000XM4 with other available products").orElseThrow();

        List<String> productNames = response.products().stream().map(ProductSummary::name).toList();
        
        assertThat(productNames).contains("Sony WH-1000XM4");
        assertThat(productNames).noneMatch(name -> name.toLowerCase().contains("speaker") || name.contains("JBL"));
    }

    @Test
    void speakersDoNotCompareWithHeadphones() {
        List<Product> catalog = createTestCatalog();
        when(productService.getAllProducts()).thenReturn(catalog);

        ChatResponse response = fallback.handle("Compare JBL Flip 6 with other available products").orElseThrow();

        List<String> productNames = response.products().stream().map(ProductSummary::name).toList();
        
        assertThat(productNames).contains("JBL Flip 6");
        assertThat(productNames).noneMatch(name -> 
            name.toLowerCase().contains("headphone") || name.contains("Sony") || name.contains("Bose")
        );
    }

    @Test
    void specificCategoryComparisonRemainsIntact() {
        List<Product> catalog = createSpecificCategoryCatalog();
        when(productService.getAllProducts()).thenReturn(catalog);

        ChatResponse response = fallback.handle("Compare Gaming Laptop with other available products").orElseThrow();

        List<String> productNames = response.products().stream().map(ProductSummary::name).toList();
        
        assertThat(productNames).contains("Gaming Laptop", "Office Laptop");
        assertThat(productNames).noneMatch(name -> name.contains("Gaming Mouse"));
    }

    @Test
    void unknownProductTypesAreNotGroupedTogether() {
        List<Product> catalog = createUnknownTypeCatalog();
        when(productService.getAllProducts()).thenReturn(catalog);

        ChatResponse response = fallback.handle("Compare Widget Pro with other available products").orElseThrow();

        List<String> productNames = response.products().stream().map(ProductSummary::name).toList();
        
        assertThat(productNames).contains("Widget Pro");
        assertThat(productNames).hasSize(1);
    }

    @Test
    void directNamedComparisonStillWorks() {
        List<Product> catalog = createTestCatalog();
        when(productService.getAllProducts()).thenReturn(catalog);

        ChatResponse response = fallback.handle("Compare Dell Inspiron Laptop with HP Pavilion Laptop").orElseThrow();

        List<String> productNames = response.products().stream().map(ProductSummary::name).toList();
        
        assertThat(productNames).contains("Dell Inspiron Laptop", "HP Pavilion Laptop");
    }

    private List<Product> createTestCatalog() {
        List<Product> products = new ArrayList<>();
        products.add(new Product("Dell Inspiron Laptop", "15.6-inch FHD, Intel i5, 8GB RAM, 512GB SSD", new BigDecimal("55000"), "Electronics"));
        products.add(new Product("HP Pavilion Laptop", "14-inch HD, AMD Ryzen 5, 16GB RAM, 1TB SSD", new BigDecimal("52000"), "Electronics"));
        products.add(new Product("Lenovo IdeaPad", "15.6-inch FHD, Intel i3, 8GB RAM, 256GB SSD", new BigDecimal("45000"), "Electronics"));
        products.add(new Product("Asus VivoBook", "14-inch FHD, Intel i5, 8GB RAM, 512GB SSD", new BigDecimal("58000"), "Electronics"));
        products.add(new Product("Acer Aspire", "15.6-inch HD, AMD Ryzen 3, 4GB RAM, 1TB HDD", new BigDecimal("42000"), "Electronics"));
        products.add(new Product("MacBook Air M1", "13.3-inch Retina, Apple M1, 8GB RAM, 256GB SSD", new BigDecimal("85000"), "Electronics"));
        products.add(new Product("Samsung Galaxy S23", "6.1-inch AMOLED, 8GB RAM, 128GB Storage", new BigDecimal("65000"), "Electronics"));
        products.add(new Product("OnePlus 11", "6.7-inch AMOLED, 12GB RAM, 256GB Storage", new BigDecimal("56000"), "Electronics"));
        products.add(new Product("Google Pixel 7", "6.3-inch OLED, 8GB RAM, 128GB Storage", new BigDecimal("49000"), "Electronics"));
        products.add(new Product("Sony WH-1000XM4", "Noise-canceling headphones, 30hr battery", new BigDecimal("25000"), "Electronics"));
        products.add(new Product("Bose QuietComfort 45", "Noise-canceling headphones, 24hr battery", new BigDecimal("28000"), "Electronics"));
        products.add(new Product("JBL Flip 6", "Portable Bluetooth speaker, waterproof", new BigDecimal("9000"), "Electronics"));
        
        long id = 1;
        for (Product product : products) {
            product.setId(id++);
        }
        
        return products;
    }

    private List<Product> createSpecificCategoryCatalog() {
        List<Product> products = new ArrayList<>();
        products.add(new Product("Gaming Laptop", "High-performance gaming laptop", new BigDecimal("75000"), "Laptops"));
        products.add(new Product("Office Laptop", "Business productivity laptop", new BigDecimal("45000"), "Laptops"));
        products.add(new Product("Gaming Mouse", "RGB gaming mouse", new BigDecimal("2000"), "Accessories"));
        
        long id = 1;
        for (Product product : products) {
            product.setId(id++);
        }
        
        return products;
    }

    private List<Product> createUnknownTypeCatalog() {
        List<Product> products = new ArrayList<>();
        products.add(new Product("Widget Pro", "Advanced widget", new BigDecimal("10000"), "Electronics"));
        products.add(new Product("Gadget Plus", "Smart gadget", new BigDecimal("15000"), "Electronics"));
        
        long id = 1;
        for (Product product : products) {
            product.setId(id++);
        }
        
        return products;
    }
}
