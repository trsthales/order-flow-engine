package br.com.trsthales.orderflow.service;

import br.com.trsthales.orderflow.AbstractIntegrationTest;
import br.com.trsthales.orderflow.api.dto.OrderResponse;
import br.com.trsthales.orderflow.domain.Order;
import br.com.trsthales.orderflow.domain.OrderRepository;
import br.com.trsthales.orderflow.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OrderCacheTest extends AbstractIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderProcessorService orderProcessorService;

    @MockitoSpyBean
    private OrderRepository orderRepository;

    @Test
    void shouldReadFromCacheOnSecondCall() {
        UUID id = savePendingOrder("75.00");
        clearInvocations(orderRepository);

        OrderResponse first = orderService.findById(id);
        OrderResponse second = orderService.findById(id);

        assertThat(second).isEqualTo(first);
        verify(orderRepository, times(1)).findByIdWithItems(id);
    }

    @Test
    void shouldEvictCacheWhenOrderIsProcessed() {
        UUID id = savePendingOrder("75.00");
        assertThat(orderService.findById(id).status()).isEqualTo(OrderStatus.PENDING);

        orderProcessorService.process(id);

        assertThat(orderService.findById(id).status()).isEqualTo(OrderStatus.APPROVED);
    }

    private UUID savePendingOrder(String unitPrice) {
        Order order = new Order(UUID.randomUUID());
        order.addItem(UUID.randomUUID(), 1, new BigDecimal(unitPrice));
        return orderRepository.save(order).getId();
    }
}
