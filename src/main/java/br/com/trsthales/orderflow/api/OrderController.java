package br.com.trsthales.orderflow.api;

import br.com.trsthales.orderflow.api.dto.CreateOrderRequest;
import br.com.trsthales.orderflow.api.dto.OrderResponse;
import br.com.trsthales.orderflow.service.OrderService;
import br.com.trsthales.orderflow.service.OrderService.CreatedOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @RequestBody @Valid CreateOrderRequest request) {

        CreatedOrder result = orderService.createOrder(idempotencyKey, request);
        if (result.replayed()) {
            return ResponseEntity.ok(result.order());
        }

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(result.order().id())
                .toUri();

        return ResponseEntity.created(location).body(result.order());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.findById(id));
    }
}