package br.com.trsthales.orderflow.api;

import br.com.trsthales.orderflow.domain.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    void shouldCreateOrderAndReplayResponseForSameIdempotencyKey() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        String payload = """
                {
                  "customerId": "%s",
                  "items": [
                    {
                      "productId": "%s",
                      "quantity": 2,
                      "unitPrice": 45.00
                    }
                  ]
                }
                """.formatted(customerId, productId);

        MvcResult firstCall = mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.totalAmount").value(90.00))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        MvcResult secondCall = mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(secondCall.getResponse().getContentAsString())
                .isEqualTo(firstCall.getResponse().getContentAsString());
        assertThat(orderRepository.count()).isEqualTo(1);

        String location = firstCall.getResponse().getHeader("Location");
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void shouldReturnConflictWhenIdempotencyKeyIsInProgress() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("idempotency:orders:" + idempotencyKey, "IN_PROGRESS");

        String payload = """
                {
                  "customerId": "%s",
                  "items": [
                    { "productId": "%s", "quantity": 1, "unitPrice": 15.00 }
                  ]
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnBadRequestWhenPayloadIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.customerId").exists())
                .andExpect(jsonPath("$.errors.items").exists());
    }
}