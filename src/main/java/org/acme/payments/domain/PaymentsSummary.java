package org.acme.payments.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.json.bind.annotation.JsonbProperty;

import java.util.Map;
import java.util.Optional;

@RegisterForReflection
public record PaymentsSummary(
        @JsonbProperty("default") PaymentSummary defaultPaymentSummary,
        @JsonbProperty("fallback") PaymentSummary fallbackPaymentSummary) {

    public static final PaymentsSummary ZERO = new PaymentsSummary(PaymentSummary.ZERO, PaymentSummary.ZERO);

    public static PaymentsSummary of(Map<String, PaymentSummary> summary) {
        return of(summary.get("default"), summary.get("fallback"));
    }

    public static PaymentsSummary of(PaymentSummary defaultPaymentSummary, PaymentSummary fallbackPaymentSummary) {
        return new PaymentsSummary(defaultPaymentSummary, fallbackPaymentSummary);
    }

    public PaymentsSummary {
        defaultPaymentSummary = Optional.ofNullable(defaultPaymentSummary).orElse(PaymentSummary.ZERO);
        fallbackPaymentSummary = Optional.ofNullable(fallbackPaymentSummary).orElse(PaymentSummary.ZERO);
    }

    public PaymentsSummary add(PaymentsSummary paymentsSummary) {
        return new PaymentsSummary(
                this.defaultPaymentSummary.add(paymentsSummary.defaultPaymentSummary),
                this.fallbackPaymentSummary.add(paymentsSummary.fallbackPaymentSummary)
        );
    }

}
