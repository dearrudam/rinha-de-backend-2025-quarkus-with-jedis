package org.acme.payments.producers.jedis;

import jakarta.json.bind.Jsonb;
import org.acme.payments.domain.HealthCheckData;
import org.acme.payments.domain.HealthCheckRepository;
import redis.clients.jedis.commands.StringCommands;

import java.util.Optional;

public record JedisHealthCheckRepository (Jsonb jsonb, StringCommands jedisCommands) implements HealthCheckRepository {

    public static final String ACTUAL_PAYMENT_SERVICE = "actual-payment-service";

    @Override
    public void update(HealthCheckData healthCheckData) {
        if (healthCheckData != null)
            jedisCommands.set(ACTUAL_PAYMENT_SERVICE, jsonb.toJson(healthCheckData));
    }

    @Override
    public HealthCheckData getActual() {
        return Optional.ofNullable(jedisCommands.get(ACTUAL_PAYMENT_SERVICE))
                .map(json -> jsonb.fromJson(json, HealthCheckData.class))
                .orElse(null);
    }
}
