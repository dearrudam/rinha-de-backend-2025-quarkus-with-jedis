package org.acme.payments.producers.jedis;

import jakarta.json.bind.Jsonb;
import org.acme.payments.domain.PaymentSummary;
import org.acme.payments.domain.PaymentsRepository;
import org.acme.payments.domain.PaymentsSummary;
import org.acme.payments.domain.ProcessedPayment;
import redis.clients.jedis.UnifiedJedis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public record JedisPaymentsRepository(Jsonb jsonb, UnifiedJedis jedis) implements PaymentsRepository {

    public static final String PAYMENTS = "payments";

    @Override
    public void purge() {
        this.jedis.del(PAYMENTS);
    }

    @Override
    public ProcessedPayment save(ProcessedPayment processedPayment) {
        jedis.zadd(PAYMENTS, processedPayment.requestedAt().toEpochMilli(), jsonb.toJson(processedPayment));
        return null;
    }

    @Override
    public PaymentsSummary summary(Instant from, Instant to) {
        var min = Optional.of(from).map(Instant::toEpochMilli).orElse(0l);
        var max = Optional.of(to).map(Instant::toEpochMilli).orElse(-1l);

        Map<String, PaymentSummary> summary = jedis.zrangeByScore(PAYMENTS, min, max)
                .stream()
                .map(json -> jsonb.fromJson(json, ProcessedPayment.class))
                .collect(Collectors.groupingBy(
                        payment -> payment.processedBy(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                payments ->
                                        new PaymentSummary(
                                                Long.valueOf(payments.size()),
                                                payments.stream().map(ProcessedPayment::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                                        )
                        )
                ));

        return PaymentsSummary.of(summary);
    }
}
