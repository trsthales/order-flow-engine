package br.com.trsthales.orderflow.domain;

import br.com.trsthales.orderflow.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class OrderRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldSaveAndLoadOrderWithItems() {
        Order order = new Order(UUID.randomUUID());
        order.addItem(UUID.randomUUID(), 2, new BigDecimal("35.50"));
        order.addItem(UUID.randomUUID(), 1, new BigDecimal("120.00"));

        Order saved = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        Order found = orderRepository.findByIdWithItems(saved.getId()).orElseThrow();

        assertThat(found.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(found.getTotalAmount()).isEqualByComparingTo("191.00");
        assertThat(found.getItems()).hasSize(2);
        assertThat(found.getCreatedAt()).isNotNull();
    }
}