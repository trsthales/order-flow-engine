package br.com.trsthales.orderflow.config;

import br.com.trsthales.orderflow.api.dto.OrderResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheConfiguration ordersCacheConfiguration(
            @Value("${app.cache.orders-ttl}") Duration ordersTtl) {

        var serializer = new JacksonJsonRedisSerializer<>(OrderResponse.class);

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ordersTtl)
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));
    }
}
