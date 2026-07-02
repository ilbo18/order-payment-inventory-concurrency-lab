package com.ilbo18.concurrencylab.inventory.infrastructure;

import com.ilbo18.concurrencylab.inventory.domain.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);

    // 같은 상품 재고를 동시에 차감하는 주문 요청을 직렬화하기 위해 재고 행에 쓰기 락을 건다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select inventory from Inventory inventory where inventory.productId = :productId")
    Optional<Inventory> findByProductIdForUpdate(@Param("productId") Long productId);

    // 낙관적 락은 조회 시점에는 락을 잡지 않고, 커밋 시점의 version 충돌로 동시 수정을 감지한다.
    @Query("select inventory from Inventory inventory where inventory.productId = :productId")
    Optional<Inventory> findByProductIdForOptimistic(@Param("productId") Long productId);
}
