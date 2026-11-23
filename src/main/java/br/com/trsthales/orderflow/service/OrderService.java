package br.com.trsthales.orderflow.service;

import br.com.trsthales.orderflow.api.dto.CreateOrderRequest;
import br.com.trsthales.orderflow.api.dto.OrderResponse;
import br.com.trsthales.orderflow.domain.Order;
import br.com.trsthales.orderflow.domain.OrderRepository;
import br.com.trsthales.orderflow.messaging.OrderCreatedEvent;
import br.com.trsthales.orderflow.messaging.OrderEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final IdempotencyService idempotencyService;
    private final OrderEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository,
                        IdempotencyService idempotencyService,
                        OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.idempotencyService = idempotencyService;
        this.eventPublisher = eventPublisher;
    }

    public CreatedOrder createOrder(String idempotencyKey, CreateOrderRequest request) {
        Optional<UUID> existingOrderId = idempotencyService.tryAcquire(idempotencyKey);
        if (existingOrderId.isPresent()) {
            return new CreatedOrder(findById(existingOrderId.get()), true);
        }

        try {
            Order order = new Order(request.customerId());
            request.items().forEach(item ->
                    order.addItem(item.productId(), item.quantity(), item.unitPrice())
            );

            Order saved = orderRepository.save(order);
            idempotencyService.markCompleted(idempotencyKey, saved.getId());
            eventPublisher.publish(new OrderCreatedEvent(
                    saved.getId(),
                    saved.getCustomerId(),
                    saved.getTotalAmount(),
                    saved.getCreatedAt()
            ));
            log.info("Order created with id: {} for customer: {}", saved.getId(), saved.getCustomerId());

            return new CreatedOrder(OrderResponse.from(saved), false);
        } catch (RuntimeException ex) {
            idempotencyService.release(idempotencyKey);
            throw ex;
        }
    }

    @Cacheable(cacheNames = "orders", key = "#id")
    @Transactional(readOnly = true)
    public OrderResponse findById(UUID id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return OrderResponse.from(order);
    }

    public record CreatedOrder(OrderResponse order, boolean replayed) {
    }
}