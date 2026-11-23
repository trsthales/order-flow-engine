package br.com.trsthales.orderflow.messaging;

import br.com.trsthales.orderflow.service.OrderProcessorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);

    private final OrderProcessorService processorService;

    public OrderEventListener(OrderProcessorService processorService) {
        this.processorService = processorService;
    }

    @KafkaListener(topics = "${app.kafka.topics.orders-created}")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Processing event for order {}", event.orderId());
        processorService.process(event.orderId());
    }

    @KafkaListener(topics = "${app.kafka.topics.orders-created}-dlt", groupId = "order-flow-dlt")
    public void onDeadLetter(OrderCreatedEvent event) {
        log.warn("Order {} reached DLT, marking as rejected", event.orderId());
        processorService.reject(event.orderId());
    }
}