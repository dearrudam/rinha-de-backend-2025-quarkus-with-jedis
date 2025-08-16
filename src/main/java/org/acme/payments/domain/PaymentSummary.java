package org.acme.payments.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.function.Supplier;

@RegisterForReflection
public record PaymentSummary(Long totalRequests,
                             BigDecimal totalAmount) {
    public static PaymentSummary ZERO = new PaymentSummary(0L, BigDecimal.ZERO);

    public PaymentSummary {
        totalRequests = Objects.requireNonNull(totalRequests, "Total requests must not be null");
        if (totalRequests < 0) {
            throw new IllegalArgumentException("Total requests must be non-negative");
        }
        totalAmount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
    }

    public static PaymentSummary of(Long totalRequests, BigDecimal totalAmount) {
        return new PaymentSummary(totalRequests, totalAmount);
    }

    public static PaymentSummary of(Number totalRequests, Number totalAmount) {
        return new PaymentSummary(totalRequests.longValue(), BigDecimal.valueOf(totalAmount.doubleValue()));
    }

    public static PaymentSummary of(Supplier<? extends Number> totalRequestsSupplier,
                                    Supplier<? extends Number> totalAmountSupplier) {
        return of(totalRequestsSupplier.get(), totalAmountSupplier.get());
    }

    public PaymentSummary add(ProcessedPayment payment) {
        if (payment == null) {
            return this;
        }
        return new PaymentSummary(totalRequests + 1, totalAmount.add(payment.amount()));
    }

    public PaymentSummary add(PaymentSummary paymentSummary) {
        if (paymentSummary == null) {
            return this;
        }
        return PaymentSummary.of(
                this.totalRequests + paymentSummary.totalRequests(),
                this.totalAmount.add(paymentSummary.totalAmount())
        );
    }

}