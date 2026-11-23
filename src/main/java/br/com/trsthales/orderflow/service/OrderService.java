package br.com.trsthales.orderflow.service;

import br.com.trsthales.orderflow.api.dto.CreateOrderRequest;
import br.com.trsthales.orderflow.api.dto.OrderResponse;
import br.com.trsthales.orderflow.domain.Order;
import br.com.trsthales.orderflow.domain.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final IdempotencyService idempotencyService;

    public OrderService(OrderRepository orderRepository, IdempotencyService idempotencyService) {
        this.orderRepository = orderRepository;
        this.idempotencyService = idempotencyService;
    }

    public OrderCreationResult createOrder(String idempotencyKey, CreateOrderRequest request) {
        Optional<UUID> existingOrderId = idempotencyService.tryAcquire(idempotencyKey);
        if (existingOrderId.isPresent()) {
            return new OrderCreationResult(findById(existingOrderId.get()), true);
        }

        try {
            Order order = new Order(request.customerId());
            request.items().forEach(item ->
                    order.addItem(item.productId(), item.quantity(), item.unitPrice())
            );

            Order saved = orderRepository.save(order);
            idempotencyService.markCompleted(idempotencyKey, saved.getId());
            log.info("Order created with id: {} for customer: {}", saved.getId(), saved.getCustomerId());

            return new OrderCreationResult(OrderResponse.from(saved), false);
        } catch (RuntimeException ex) {
            idempotencyService.release(idempotencyKey);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(UUID id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return OrderResponse.from(order);
    }

    public record OrderCreationResult(OrderResponse order, boolean replayed) {
    }
}