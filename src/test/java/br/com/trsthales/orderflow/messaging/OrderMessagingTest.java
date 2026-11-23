package br.com.trsthales.orderflow.messaging;

import br.com.trsthales.orderflow.api.dto.CreateOrderItemRequest;
import br.com.trsthales.orderflow.api.dto.CreateOrderRequest;
import br.com.trsthales.orderflow.domain.Order;
import br.com.trsthales.orderflow.domain.OrderRepository;
import br.com.trsthales.orderflow.domain.OrderStatus;
import br.com.trsthales.orderflow.service.OrderProcessorService;
import br.com.trsthales.orderflow.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ActiveProfiles("test")
@SpringBootTest
class OrderMessagingTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderEventPublisher eventPublisher;

    @MockitoSpyBean
    private OrderProcessorService processorService;

    @Test
    void shouldProcessAndApproveOrder() {
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new CreateOrderItemRequest(UUID.randomUUID(), 2, new BigDecimal("150.00")))
        );

        UUID orderId = orderService.createOrder(UUID.randomUUID().toString(), request).order().id();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Order order = orderRepository.findById(orderId).orElseThrow();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.APPROVED);
        });
    }

    @Test
    void shouldSendToDltAndRejectOrderAfterRetries() {
        Order order = new Order(UUID.randomUUID());
        order.addItem(UUID.randomUUID(), 1, new BigDecimal("200.00"));
        Order saved = orderRepository.save(order);
        UUID orderId = saved.getId();

        doThrow(new RuntimeException("Database connection timeout"))
                .when(processorService).process(orderId);

        eventPublisher.publish(new OrderCreatedEvent(
                orderId, saved.getCustomerId(), saved.getTotalAmount(), saved.getCreatedAt()
        ));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Order found = orderRepository.findById(orderId).orElseThrow();
            assertThat(found.getStatus()).isEqualTo(OrderStatus.REJECTED);
        });

        verify(processorService, times(3)).process(orderId);
        verify(processorService, times(1)).reject(orderId);
    }
}
