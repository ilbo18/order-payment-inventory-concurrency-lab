package com.ilbo18.concurrencylab.inventory.domain;

import com.ilbo18.concurrencylab.common.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryTest {

    @Test
    void 재고가_충분하면_정상_차감된다() {
        Inventory inventory = new Inventory(1L, 10);

        inventory.decrease(3);

        assertThat(inventory.getQuantity()).isEqualTo(7);
    }

    @Test
    void 재고가_부족하면_예외가_발생한다() {
        Inventory inventory = new Inventory(1L, 10);

        assertThatThrownBy(() -> inventory.decrease(11))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    void 차감_수량이_0_이하이면_예외가_발생한다() {
        Inventory inventory = new Inventory(1L, 10);

        assertThatThrownBy(() -> inventory.decrease(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 증가_수량이_0_이하이면_예외가_발생한다() {
        Inventory inventory = new Inventory(1L, 10);

        assertThatThrownBy(() -> inventory.increase(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
