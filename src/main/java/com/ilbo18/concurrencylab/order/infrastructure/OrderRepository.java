package com.ilbo18.concurrencylab.order.infrastructure;

import com.ilbo18.concurrencylab.order.domain.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
}
