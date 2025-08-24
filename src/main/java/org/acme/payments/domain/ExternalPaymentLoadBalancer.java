package org.acme.payments.domain;

public interface ExternalPaymentLoadBalancer {
    HealthCheckData resolve();
}