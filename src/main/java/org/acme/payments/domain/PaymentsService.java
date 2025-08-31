package org.acme.payments.domain;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;

@ApplicationScoped
public class PaymentsService {

    private final PaymentsProcessor paymentsProcessor;
    private final PaymentsRepository paymentsRepository;

    public PaymentsService(PaymentsProcessor paymentsProcessor, PaymentsRepository paymentsRepository) {
        this.paymentsProcessor = paymentsProcessor;
        this.paymentsRepository = paymentsRepository;
    }

    public void accept(PaymentRequest paymentRequest) {
        paymentsProcessor.queue(paymentRequest);
    }

    public void purge() {
        paymentsRepository.purge();
    }

    public PaymentsSummary summary(Instant from, Instant to) {
        return paymentsRepository.summary(from, to);
    }
}
