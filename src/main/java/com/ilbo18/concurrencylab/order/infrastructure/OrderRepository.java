package com.ilbo18.concurrencylab.order.infrastructure;

import com.ilbo18.concurrencylab.order.domain.OrderEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    @Override
    @EntityGraph(attributePaths = "orderItems")
    Optional<OrderEntity> findById(Long id);
}
