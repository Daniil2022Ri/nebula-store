package com.nebula.store.repository;

import com.nebula.store.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("select distinct p.category from Product p where p.active = true order by p.category")
    List<String> findActiveCategories();

    /** Returns {@code [category, productCount]} rows for every category that has active products. */
    @Query("""
            select p.category, count(p)
            from Product p
            where p.active = true
            group by p.category
            order by p.category
            """)
    List<Object[]> countActiveByCategory();

    @Query("select count(p) from Product p where p.active = true and p.category = :category")
    long countActiveInCategory(@Param("category") String category);

    @Query("select count(p) from Product p where p.active = true")
    long countActive();
}
