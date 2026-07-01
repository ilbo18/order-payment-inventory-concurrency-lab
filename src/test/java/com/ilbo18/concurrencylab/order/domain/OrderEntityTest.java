package com.ilbo18.concurrencylab.order.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderEntityTest {

    @Test
    void 주문_생성_시_CREATED_상태가_된다() {
        OrderEntity order = new OrderEntity(List.of(new OrderItem(1L, 1, BigDecimal.valueOf(10000))));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void 주문_항목_금액은_단가와_수량의_곱으로_계산된다() {
        OrderItem orderItem = new OrderItem(1L, 3, BigDecimal.valueOf(10000));

        assertThat(orderItem.getLineAmount()).isEqualByComparingTo(BigDecimal.valueOf(30000));
    }

    @Test
    void 주문_항목_수량이_0_이하이면_예외가_발생한다() {
        assertThatThrownBy(() -> new OrderItem(1L, 0, BigDecimal.valueOf(10000)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 주문_항목_가격이_음수이면_예외가_발생한다() {
        assertThatThrownBy(() -> new OrderItem(1L, 1, BigDecimal.valueOf(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 주문_총액은_주문_항목_합계와_일치한다() {
        OrderEntity order = new OrderEntity(List.of(
                new OrderItem(1L, 2, BigDecimal.valueOf(10000)),
                new OrderItem(2L, 1, BigDecimal.valueOf(5000))
        ));

        assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(25000));
    }
}
