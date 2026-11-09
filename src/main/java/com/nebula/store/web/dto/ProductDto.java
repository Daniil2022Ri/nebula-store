package com.nebula.store.web.dto;

import com.nebula.store.domain.Product;

import java.math.BigDecimal;

/**
 * Public representation of a catalog item.
 */
public record ProductDto(
        Long id,
        String slug,
        String name,
        String description,
        String category,
        BigDecimal price,
        int stock,
        double rating,
        String icon,
        boolean inStock
) {

    public static ProductDto of(Product product) {
        return new ProductDto(
                product.getId(),
                product.getSlug(),
                product.getName(),
                product.getDescription(),
                product.getCategory(),
                product.getPrice(),
                product.getStock(),
                product.getRating(),
                product.getIcon(),
                product.getStock() > 0
        );
    }
}
