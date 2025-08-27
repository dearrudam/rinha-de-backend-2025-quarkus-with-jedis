package org.acme.payments.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RegisterForReflection
public record ProcessedPayment(String correlationId, String processedBy, BigDecimal amount, Instant requestedAt) {

    public static ProcessedPayment of(String processedBy, PaymentRequest paymentRequest) {
        return new ProcessedPayment(
                paymentRequest.correlationId(),
                processedBy,
                paymentRequest.amount(),
                Instant.now().truncatedTo(ChronoUnit.SECONDS)
        );
    }

}
