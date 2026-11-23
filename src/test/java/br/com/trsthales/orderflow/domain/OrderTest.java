package br.com.trsthales.orderflow.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    void shouldRecalculateTotalWhenAddingItems() {
        Order order = new Order(UUID.randomUUID());

        order.addItem(UUID.randomUUID(), 3, new BigDecimal("25.00"));
        order.addItem(UUID.randomUUID(), 2, new BigDecimal("10.50"));

        assertThat(order.getTotalAmount()).isEqualByComparingTo("96.00");
        assertThat(order.getItems()).hasSize(2);
    }

    @Test
    void shouldNotAllowAddingItemsAfterProcessingStarts() {
        Order order = new Order(UUID.randomUUID());
        order.addItem(UUID.randomUUID(), 1, new BigDecimal("50.00"));
        order.markAsProcessing();

        assertThatThrownBy(() -> order.addItem(UUID.randomUUID(), 1, new BigDecimal("10.00")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldEnforceValidStateTransitions() {
        Order order = new Order(UUID.randomUUID());

        assertThatThrownBy(order::approve)
                .isInstanceOf(IllegalStateException.class);

        order.markAsProcessing();
        order.approve();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.APPROVED);
        assertThatThrownBy(order::reject)
                .isInstanceOf(IllegalStateException.class);
    }
}
