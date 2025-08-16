package org.acme.payments.domain;

public interface PaymentsProcessor {
    void queue(PaymentRequest paymentRequest);
}
