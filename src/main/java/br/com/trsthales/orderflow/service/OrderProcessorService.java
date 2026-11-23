package br.com.trsthales.orderflow.service;

import br.com.trsthales.orderflow.domain.Order;
import br.com.trsthales.orderflow.domain.OrderRepository;
import br.com.trsthales.orderflow.domain.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class OrderProcessorService {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessorService.class);
    private static final BigDecimal AUTO_APPROVAL_LIMIT = new BigDecimal("10000.00");

    private final OrderRepository orderRepository;

    public OrderProcessorService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @CacheEvict(cacheNames = "orders", key = "#orderId")
    @Transactional
    public void process(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("Order {} already in status {}, ignoring", orderId, order.getStatus());
            return;
        }

        order.markAsProcessing();

        if (order.getTotalAmount().compareTo(AUTO_APPROVAL_LIMIT) > 0) {
            order.reject();
            log.info("Order {} rejected (amount {} exceeds limit)", orderId, order.getTotalAmount());
        } else {
            order.approve();
            log.info("Order {} approved", orderId);
        }
    }

    @CacheEvict(cacheNames = "orders", key = "#orderId")
    @Transactional
    public void reject(UUID orderId) {
        orderRepository.findById(orderId).ifPresent(order -> {
            if (order.getStatus() != OrderStatus.APPROVED && order.getStatus() != OrderStatus.REJECTED) {
                order.reject();
                log.warn("Order {} rejected after processing failure", orderId);
            }
        });
    }
}