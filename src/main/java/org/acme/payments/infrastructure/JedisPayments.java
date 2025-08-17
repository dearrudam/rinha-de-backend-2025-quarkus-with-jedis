package org.acme.payments.infrastructure;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.acme.payments.domain.PaymentRequest;
import org.acme.payments.domain.PaymentSummary;
import org.acme.payments.domain.PaymentsProcessor;
import org.acme.payments.domain.PaymentsRepository;
import org.acme.payments.domain.PaymentsSummary;
import org.acme.payments.domain.ProcessedPayment;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.exceptions.JedisException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@ApplicationScoped
public class JedisPayments implements PaymentsProcessor, PaymentsRepository {

    public static final String PAYMENTS = "payments";
    public static final String PAYMENTS_QUEUE = "payments-queued";

    @Inject
    @ConfigProperty(name = "queue.in.memory", defaultValue = "false")
    private boolean queueInMemory;

    @Inject
    @ConfigProperty(name = "jedis.url", defaultValue = "redis://localhost:6377")
    private String jedisUrl;

    @Inject
    @ConfigProperty(name = "instance.name", defaultValue = "singleInstance")
    private String instanceName;

    @Inject
    @ConfigProperty(name = "workers.size", defaultValue = "1")
    private int workersSize;

    @Inject
    private ExternalPaymentProcessor externalPaymentProcessor;

    private ExecutorService executeService;

    private UnifiedJedis purgeJedis;

    private UnifiedJedis summaryJedis;

    private Jsonb jsonb;

    private LinkedBlockingQueue<PaymentRequest> queue = new LinkedBlockingQueue<>();

    @PostConstruct
    public void postConstruct() {
        this.jsonb = JsonbBuilder.create();
        this.purgeJedis = createUnifiedJedis();
        this.summaryJedis = createUnifiedJedis();
        this.executeService = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PreDestroy
    public void destroy() {
        if (executeService != null) {
            executeService.shutdown();
            int maxWaitSeconds = 5;
            if (!executeService.isTerminated()) {
                try {
                    Thread.sleep(Duration.ofSeconds(maxWaitSeconds).toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore interrupted status
                }
                if (!executeService.isTerminated()) {
                    executeService.shutdownNow();
                }
            }
        }
    }

    @Startup
    public void onApplicationStart() {

        // Initialize the queue in Redis
        int initiatedWorker = 0;
        do {
            executeService.execute(getPaymentTask());
            initiatedWorker++;
        } while (initiatedWorker < this.workersSize);
        System.out.printf("Started %d workers for queue payment processing%n", initiatedWorker);
        // If the queue is in memory, we will use the in-memory queue
        System.out.printf("Using %s for queue%n", queueInMemory ? "in-memory queue" : "Redis queue");

        // Start a separate thread to handle queuing payment requests to Redis
        // This thread will take payment requests from the queue and push them to Redis
        if (!queueInMemory)
            this.executeService.execute(() -> {
                UnifiedJedis unifiedJedis = createUnifiedJedis();
                while (true) {
                    try {
                        PaymentRequest paymentRequest = queue.take();
                        queueInRedis(unifiedJedis, paymentRequest);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // Restore interrupted status
                        break; // Exit the loop if interrupted
                    }
                }
            });

    }

    private Runnable getPaymentTask() {
        if (queueInMemory) {
            // If the queue is in memory, we will use the in-memory queue
            return this.getPaymentTaskFromMemory();
        }
        return this.getPaymentTaskFromJedis();
    }

    Runnable getPaymentTaskFromJedis() {
        var unifiedJedis = createUnifiedJedis();
        // If the queue is in memory, we will use the in-memory queue
        return () -> this.listenForPayments(
                () -> this.retrievePaymentRequest(unifiedJedis),
                processedPayment -> {
                    // On successful processing, store the processed payment in Redis
                    unifiedJedis.lpush(PAYMENTS, jsonb.toJson(processedPayment));
                },
                paymentRequest -> {
                    // On processing failure, re-queue the payment request
                    this.queueInRedis(unifiedJedis, paymentRequest);
                }
        );
    }

    Runnable getPaymentTaskFromMemory() {
        var unifiedJedis = createUnifiedJedis();
        // If the queue is in memory, we will use the in-memory queue
        return () -> this.listenForPayments(
                () -> this.retrievePaymentFromMemory(),
                processedPayment -> {
                    // On successful processing, store the processed payment in Redis
                    unifiedJedis.lpush(PAYMENTS, jsonb.toJson(processedPayment));
                },
                paymentRequest -> {
                    // On processing failure, re-queue the payment request
                    this.queue.offer(paymentRequest); // Re-queue in memory
                }
        );
    }

    private Optional<PaymentRequest> retrievePaymentFromMemory() {
        try {
            PaymentRequest paymentRequest = queue.take();
            return Optional.ofNullable(paymentRequest);
        } catch (InterruptedException e) {
            return Optional.empty();
        }
    }

    private UnifiedJedis createUnifiedJedis() {
        return new UnifiedJedis(jedisUrl);
    }

    public void listenForPayments(Supplier<Optional<PaymentRequest>> paymentRequestSupplier,
                                  Consumer<ProcessedPayment> processedPaymentConsumer,
                                  Consumer<PaymentRequest> onProcessingFailed) {
        while (!executeService.isShutdown()) {
            try {
                Optional<PaymentRequest> receivedPaymentRequest = paymentRequestSupplier.get();
                // Process the payment request if it was received
                receivedPaymentRequest.ifPresent(paymentRequest ->
                        externalPaymentProcessor.process(paymentRequest)
                                .ifPresentOrElse(
                                        processedPaymentConsumer,
                                        () -> onProcessingFailed.accept(paymentRequest)
                                ));
            } catch (RuntimeException e) {
                if (e instanceof JedisException) {
                    // printing any Jedis exception stack trace
                    e.printStackTrace();
                }
            }
        }
    }


    private Optional<PaymentRequest> retrievePaymentRequest(UnifiedJedis unifiedJedis) {
        // BLPOP returns a list of two elements: the queue name and the message
        var result = unifiedJedis.blpop(0, PAYMENTS_QUEUE); // 0 timeout means to block indefinitely
        if (result != null && result.size() == 2) {
            String message = result.get(1);
            return Optional.ofNullable(jsonb.fromJson(message, PaymentRequest.class));
        }
        return Optional.empty();
    }

    @Override
    public void queue(PaymentRequest paymentRequest) {
        this.queue.offer(paymentRequest);
    }

    private void queueInRedis(UnifiedJedis unifiedJedis, PaymentRequest paymentRequest) {
        unifiedJedis.lpush(PAYMENTS_QUEUE, jsonb.toJson(paymentRequest));
    }

    @Override
    public void purge() {
        this.purgeJedis.del(PAYMENTS);
    }

    @Override
    public PaymentsSummary summary(Instant from, Instant to) {
        Map<String, PaymentSummary> summary = summaryJedis.lrange(PAYMENTS, 0, -1)
                .stream()
                .map(json -> jsonb.fromJson(json, ProcessedPayment.class))
                .filter(createdOn(from, to))
                .collect(Collectors.groupingBy(
                        payment -> payment.processedBy(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                payments ->
                                        new PaymentSummary(
                                                Long.valueOf(payments.size()),
                                                payments.stream().map(ProcessedPayment::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                                        )
                        )
                ));
        return PaymentsSummary.of(summary);
    }

    public static Predicate<ProcessedPayment> createdOn(Instant from, Instant to) {
        return payment -> {
            if (from == null && to == null) {
                return true;
            }
            Instant requestedAt = payment.requestedAt();
            return (from == null || (from.isBefore(requestedAt) || from.equals(requestedAt)))
                    &&
                    (to == null || (to.isAfter(requestedAt) || to.equals(requestedAt)));
        };
    }
}
