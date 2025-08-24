package org.acme.payments.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.net.URI;
import java.util.function.BiFunction;

@RegisterForReflection
public record HealthCheckData(
        String name,
        URI url,
        boolean failing,
        int minResponseTime) {

    public static HealthCheckData of(String name, URI url, boolean failing, int minResponseTime) {
        return new HealthCheckData(name, url, failing, minResponseTime);
    }

    public ProcessedPayment buildProcessedPayment(PaymentRequest paymentRequest) {
        return ProcessedPayment.of(this.name, paymentRequest);
    }

    public static HealthCheckData compare(HealthCheckData a,
                                          HealthCheckData b,
                                          BiFunction<HealthCheckData, HealthCheckData, HealthCheckData> tieBreaker) {
        if (a == null && b == null) {
            ;
            return null;
        }
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        if (a.failing && !b.failing) {
            return b;
        }
        if (!a.failing && b.failing) {
            return a;
        }
        if (a.minResponseTime == b.minResponseTime) {
            return tieBreaker.apply(a, b);
        }
        return a.minResponseTime < b.minResponseTime ? a : b;
    }

    public HealthCheckData withURL(String name, URI defaultURL) {
        return new HealthCheckData(name, defaultURL, this.failing, this.minResponseTime);
    }
}
