package com.nebula.store.config;

import com.nebula.store.domain.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DataSeederTest {

    private final List<Product> catalog = DataSeeder.catalog();

    @Test
    @DisplayName("demo catalog is complete and consistent")
    void demoCatalogIsConsistent() {
        assertThat(catalog).hasSize(12);

        Set<String> slugs = catalog.stream().map(Product::getSlug).collect(Collectors.toSet());
        assertThat(slugs).hasSize(catalog.size());

        assertThat(catalog).allSatisfy(product -> {
            assertThat(product.getSlug()).matches("[a-z0-9-]+");
            assertThat(product.getName()).isNotBlank();
            assertThat(product.getDescription()).hasSizeGreaterThan(20);
            assertThat(product.getCategory()).isNotBlank();
            assertThat(product.getPrice()).isPositive();
            assertThat(product.getStock()).isPositive();
            assertThat(product.getRating()).isBetween(0.0, 5.0);
            assertThat(product.getIcon()).isNotBlank();
            assertThat(product.isActive()).isTrue();
        });
    }

    @Test
    @DisplayName("demo catalog covers every storefront category")
    void coversEveryCategory() {
        Set<String> categories = catalog.stream()
                .map(Product::getCategory)
                .collect(Collectors.toSet());

        assertThat(categories).containsExactlyInAnyOrder(
                "Аудио", "Клавиатуры", "Мониторы", "Носимые", "Аксессуары");
    }
}
