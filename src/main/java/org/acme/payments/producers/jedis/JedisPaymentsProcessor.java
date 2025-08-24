package org.acme.payments.producers.jedis;

import jakarta.json.bind.Jsonb;
import org.acme.payments.domain.PaymentRequest;
import org.acme.payments.domain.PaymentsProcessor;
import org.acme.payments.domain.PaymentsRepository;
import org.acme.payments.domain.ProcessedPayment;
import org.acme.payments.domain.ExternalPaymentProcessor;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.exceptions.JedisException;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

public record JedisPaymentsProcessor(
        Jsonb jsonb,
        UnifiedJedis jedis,
        PaymentsRepository paymentsRepository,
        ExternalPaymentProcessor externalPaymentProcessor,
        LinkedBlockingQueue<PaymentRequest> queue,
        int workersSize,
        ExecutorService executeService

) implements PaymentsProcessor {

    public static final String PAYMENTS_QUEUE = "payments-queued";

    public JedisPaymentsProcessor start() {

        // Initialize the queue in Redis
        int initiatedWorker = 0;
        do {
            executeService.execute(getPaymentTask());
            initiatedWorker++;
        } while (initiatedWorker < this.workersSize);
        System.out.printf("Started %d workers for queue payment processing%n", initiatedWorker);

        // Start a separate thread to handle queuing payment requests to Redis
        // This thread will take payment requests from the queue and push them to Redis
        this.executeService.execute(() -> {
            while (true) {
                try {
                    PaymentRequest paymentRequest = queue.take();
                    queueInRedis(paymentRequest);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore interrupted status
                    break; // Exit the loop if interrupted
                }
            }
        });
        return this;
    }

    public void shutdown() {
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

    private Runnable getPaymentTask() {
        return () -> this.listenForPayments(
                this::retrievePaymentRequest,
                paymentsRepository::save,
                paymentRequest -> {
                    // On processing failure, re-queue the payment request
                    this.queueInRedis(paymentRequest);
                }
        );
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


    private Optional<PaymentRequest> retrievePaymentRequest() {
        // BLPOP returns a list of two elements: the queue name and the message
        var result = jedis.blpop(0, PAYMENTS_QUEUE); // 0 timeout means to block indefinitely
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

    private void queueInRedis(PaymentRequest paymentRequest) {
        jedis.lpush(PAYMENTS_QUEUE, jsonb.toJson(paymentRequest));
    }

}
