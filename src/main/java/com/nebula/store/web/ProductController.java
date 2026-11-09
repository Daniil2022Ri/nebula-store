package com.nebula.store.web;

import com.nebula.store.service.ProductService;
import com.nebula.store.web.dto.ProductDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public catalog endpoints.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * @param category exact category name, e.g. {@code Аудио}
     * @param query    free-text search
     * @param sort     {@code popular} (default), {@code price_asc}, {@code price_desc},
     *                 {@code rating} or {@code name}
     */
    @GetMapping
    public List<ProductDto> list(@RequestParam(required = false) String category,
                                 @RequestParam(name = "q", required = false) String query,
                                 @RequestParam(defaultValue = "popular") String sort) {
        return productService.find(category, query, sort);
    }

    @GetMapping("/{id}")
    public ProductDto getById(@PathVariable long id) {
        return productService.get(id);
    }
}
