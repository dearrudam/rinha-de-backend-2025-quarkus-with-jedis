package org.acme.payments.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class ExternalPaymentProcessor {

    private HttpClient httpClient;
    private ExternalPaymentLoadBalancer loadBalancer;
    private Map<String, AtomicLong> errorCount = new ConcurrentHashMap<>();

    /**
     * Required by CDI, Don't use it directly.
     */
    @Deprecated
    public ExternalPaymentProcessor() {
    }

    @Inject
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
                try {
                    return Optional.of(payment);
                } finally {
                    errorCount.remove(payment.correlationId());
                }
            }
            errorCount.computeIfAbsent(payment.correlationId(), k -> new AtomicLong(0)).incrementAndGet();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        } finally {
            sleepIfNeed(payment);
        }
    }

    private void sleepIfNeed(ProcessedPayment payment) {
        long errorNumber = errorCount
                .computeIfAbsent(payment.correlationId(), k -> new AtomicLong(0))
                .get();
        long delay = errorNumber * 100;
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                // Restore the interrupted status
            }
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