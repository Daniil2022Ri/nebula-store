package com.nebula.store.web.dto;

/**
 * Catalog category together with the number of active products in it.
 */
public record CategoryDto(String name, long count) {
}
