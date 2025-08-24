package org.acme.payments.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
public class PaymentsService {

    @Inject
    PaymentsProcessor paymentsProcessor;

    @Inject
    PaymentsRepository paymentsRepository;


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
