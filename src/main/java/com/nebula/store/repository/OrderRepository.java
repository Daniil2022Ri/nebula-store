package com.nebula.store.repository;

import com.nebula.store.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByCode(String code);

    boolean existsByCode(String code);

    /** Newest first, used by the admin listing in the next stage. */
    @Query("select o from Order o order by o.createdAt desc")
    List<Order> findAllNewestFirst();
}
