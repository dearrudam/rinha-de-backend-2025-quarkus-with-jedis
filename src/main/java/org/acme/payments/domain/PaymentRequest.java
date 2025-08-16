package org.acme.payments.domain;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.math.BigDecimal;

@RegisterForReflection
public record PaymentRequest(String correlationId, BigDecimal amount) { }
