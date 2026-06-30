package com.ilbo18.concurrencylab.product.infrastructure;

import com.ilbo18.concurrencylab.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
