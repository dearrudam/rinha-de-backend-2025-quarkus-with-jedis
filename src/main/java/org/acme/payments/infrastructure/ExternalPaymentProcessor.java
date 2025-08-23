package org.acme.payments.infrastructure;

import org.acme.payments.domain.PaymentRequest;
import org.acme.payments.domain.ProcessedPayment;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class ExternalPaymentProcessor {

    private URI defaultURL;
    private URI fallbackURL;
    private HttpClient httpClient;
    private Map<String, AtomicLong> defaultErrorCount;
    private Map<String, AtomicLong> fallbackErrorCount;
    private Long retriesBeforeFallback;

    @Deprecated
    /**
     * This constructor is deprecated and should not be used.
     * It's for CDI compatibility only.
     */
    public ExternalPaymentProcessor() {
    }

    @Inject
    public ExternalPaymentProcessor(@ConfigProperty(name = "default.payment.url")
                                            String defaultURL,
                                    @ConfigProperty(name = "fallback.payment.url")
                                            String fallbackURL,
                                    @ConfigProperty(name = "retries.before.fallback")
                                            Optional<Long> retriesBeforeFallback) {
        this.defaultURL = URI.create(Path.of(defaultURL,"payments").toString());
        this.fallbackURL = URI.create(Path.of(fallbackURL,"payments").toString());
        this.retriesBeforeFallback = retriesBeforeFallback.orElse(16L);
        this.httpClient = builHttpClient();
    }

    @PostConstruct
    public void postConstruct() {
        this.defaultErrorCount = new ConcurrentHashMap<>();
        this.fallbackErrorCount = new ConcurrentHashMap<>();
    }

    public Optional<ProcessedPayment> process(PaymentRequest paymentRequest) {
        ProcessedPayment payment = ProcessedPayment.defaultPayment(paymentRequest);
        try {
            var request = createRequest(defaultURL, payment);
            var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 200) {
                defaultErrorCount.remove(payment.correlationId());
                return Optional.of(payment);
            } else {
                if (response.statusCode() == 500) {
                    long errors = defaultErrorCount
                            .computeIfAbsent(payment.correlationId(), k -> new AtomicLong(0))
                            .incrementAndGet();
                    if (errors >= retriesBeforeFallback) {
                        payment = processFallback(paymentRequest);
                        defaultErrorCount.remove(payment.correlationId());
                        return Optional.of(payment);
                    }
                }
            }
            return Optional.empty();
        } catch (InterruptedException | IOException e) {
            return Optional.empty();
        } finally {
            sleepIfNeed(payment);
        }
    }

    private void sleepIfNeed(ProcessedPayment payment) {
        long errorNumber = defaultErrorCount
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


    private ProcessedPayment processFallback(PaymentRequest paymentRequest) throws IOException, InterruptedException {
        ProcessedPayment payment = ProcessedPayment.fallbackPayment(paymentRequest);
        System.out.printf("Processing fallback payment for correlationId %s%n", payment.correlationId());
        // Fallback to the secondary URL
        var request = createRequest(fallbackURL, payment);
        var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() == 200) {
            return payment;
        } else {
            long errors = fallbackErrorCount
                    .computeIfAbsent(payment.correlationId(), k -> new AtomicLong(0))
                    .incrementAndGet();
            throw new RuntimeException("payment processing on the fallback processor failed with status code: %s - correlationId: %s - attempts: %s"
                    .formatted(response.statusCode(), payment.correlationId(), errors));
        }
    }

    private HttpRequest createRequest(URI defaultURL, ProcessedPayment payment) {
        return HttpRequest.newBuilder(defaultURL)
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

    private static HttpClient builHttpClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .executor(Runnable::run)
                .build();
    }
}