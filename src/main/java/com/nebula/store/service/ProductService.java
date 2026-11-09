package com.nebula.store.service;

import com.nebula.store.domain.Product;
import com.nebula.store.error.NotFoundException;
import com.nebula.store.repository.ProductRepository;
import com.nebula.store.web.dto.CategoryDto;
import com.nebula.store.web.dto.ProductDto;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-side of the catalog: filtering, searching and sorting of active products.
 */
@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository products;

    public ProductService(ProductRepository products) {
        this.products = products;
    }

    /**
     * @param category optional category filter, case-insensitive
     * @param query    optional free-text search over name, description and category
     * @param sort     one of {@code popular}, {@code price_asc}, {@code price_desc},
     *                 {@code rating}, {@code name}
     */
    public List<ProductDto> find(String category, String query, String sort) {
        return products.findAll(activeSpec(category, query), sortFor(sort))
                .stream()
                .map(ProductDto::of)
                .toList();
    }

    public ProductDto get(long id) {
        return ProductDto.of(requireActive(id));
    }

    /** Loads a product that is visible in the catalog or fails with a 404. */
    public Product requireActive(long id) {
        return products.findById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> new NotFoundException("Товар с id=" + id + " не найден"));
    }

    public List<CategoryDto> categories() {
        return products.countActiveByCategory().stream()
                .map(row -> new CategoryDto((String) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    public long countActive() {
        return products.countActive();
    }

    private Specification<Product> activeSpec(String category, String query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));

            if (StringUtils.hasText(category)) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase()));
            }
            if (StringUtils.hasText(query)) {
                String pattern = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern),
                        cb.like(cb.lower(root.get("category")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static Sort sortFor(String sort) {
        return switch (sort == null ? "" : sort.trim().toLowerCase()) {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "price");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "price");
            case "rating" -> Sort.by(Sort.Direction.DESC, "rating").and(Sort.by("name"));
            case "name" -> Sort.by("name");
            default -> Sort.by(Sort.Direction.DESC, "rating").and(Sort.by("name"));
        };
    }
}
