package org.acme.payments.domain;

import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
public class ExternalPaymentProcessor {

    private final HttpClient httpClient;
    private final ExternalPaymentLoadBalancer loadBalancer;

    public ExternalPaymentProcessor(
            HttpClient httpClient,
            ExternalPaymentLoadBalancer loadBalancer) {
        this.httpClient = httpClient;
        this.loadBalancer = loadBalancer;
    }

    public Optional<ProcessedPayment> process(PaymentRequest paymentRequest) {
        ProcessedPayment payment = null;
        try {
            HealthCheckData data = Objects.requireNonNull(loadBalancer.resolve(), "Cannot resolve the URL target");
            payment = data.buildProcessedPayment(paymentRequest);
            var request = createRequest(data.url().resolve("/payments"), payment);
            var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 200) {
                    return Optional.of(payment);
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private HttpRequest createRequest(URI defaultURL, ProcessedPayment payment) {
        return HttpRequest.newBuilder(defaultURL.resolve("/payments"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {
                            "correlationId": "%s",
                            "amount": %s,
                            "requestedAt": "%s"
                        }
                        """.formatted(payment.correlationId(), payment.amount(), payment.requestedAt())))
                .build();
    }
}