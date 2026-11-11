package com.nebula.store.repository;

import com.nebula.store.domain.Order;
import com.nebula.store.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByCode(String code);

    boolean existsByCode(String code);

    Page<Order> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

    Page<Order> findByStatusOrderByCreatedAtDescIdDesc(OrderStatus status, Pageable pageable);

    long countByStatus(OrderStatus status);

    /** Newest first, used by the admin listing. */
    @Query("select o from Order o order by o.createdAt desc")
    List<Order> findAllNewestFirst();

    /** Revenue over orders that were not cancelled; {@code null} when there are none. */
    @Query("select sum(o.total) from Order o where o.status <> :excluded")
    BigDecimal sumTotalExcluding(@Param("excluded") OrderStatus excluded);

    /** Units sold over orders that were not cancelled; {@code null} when there are none. */
    @Query("select sum(i.quantity) from OrderItem i where i.order.status <> :excluded")
    Long sumUnitsExcluding(@Param("excluded") OrderStatus excluded);
}
