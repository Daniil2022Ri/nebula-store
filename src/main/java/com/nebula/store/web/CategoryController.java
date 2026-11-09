package com.nebula.store.web;

import com.nebula.store.service.ProductService;
import com.nebula.store.web.dto.CategoryDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Category facets used to build the storefront navigation.
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final ProductService productService;

    public CategoryController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<CategoryDto> list() {
        return productService.categories();
    }
}
