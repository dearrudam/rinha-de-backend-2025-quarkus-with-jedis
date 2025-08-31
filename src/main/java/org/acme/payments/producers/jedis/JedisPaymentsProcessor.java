package org.acme.payments.producers.jedis;

import jakarta.json.bind.Jsonb;
import org.acme.payments.domain.ExternalPaymentProcessor;
import org.acme.payments.domain.PaymentRequest;
import org.acme.payments.domain.PaymentsProcessor;
import org.acme.payments.domain.PaymentsRepository;
import org.acme.payments.domain.ProcessedPayment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.exceptions.JedisException;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class JedisPaymentsProcessor implements PaymentsProcessor {

    private final static Logger logger = LoggerFactory.getLogger(JedisPaymentsProcessor.class);

    public static final String PAYMENTS_QUEUE = "payments-queued";

    private volatile boolean running = false;
    private final Jsonb jsonb;
    private final UnifiedJedis jedis;
    private final PaymentsRepository paymentsRepository;
    private final ExternalPaymentProcessor externalPaymentProcessor;
    private final LinkedBlockingQueue<PaymentRequest> queue;
    private final int workersSize;
    private final ExecutorService executeService;

    public JedisPaymentsProcessor(Jsonb jsonb,
                                  UnifiedJedis jedis,
                                  PaymentsRepository paymentsRepository,
                                  ExternalPaymentProcessor externalPaymentProcessor,
                                  LinkedBlockingQueue<PaymentRequest> queue,
                                  int workersSize,
                                  ExecutorService executeService) {
        this.jsonb = jsonb;
        this.jedis = jedis;
        this.paymentsRepository = paymentsRepository;
        this.externalPaymentProcessor = externalPaymentProcessor;
        this.queue = queue;
        this.workersSize = workersSize;
        this.executeService = executeService;
    }

    public JedisPaymentsProcessor start() {
        if (running) {
            return this;
        }
        running = true;
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
            while (running) {
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
        this.running = false;
    }

    private Runnable getPaymentTask() {
        return () -> this.listenForPayments(
                this::retrievePaymentRequest,
                paymentsRepository::save,
                this::queueInRedis // On processing failure, re-queue the payment request
        );
    }

    public void listenForPayments(Supplier<Optional<PaymentRequest>> paymentRequestSupplier,
                                  Consumer<ProcessedPayment> processedPaymentConsumer,
                                  Consumer<PaymentRequest> onProcessingFailed) {
        while (running) {
            try {
                Optional<PaymentRequest> receivedPaymentRequest = paymentRequestSupplier.get();
                // Process the payment request if it was received
                receivedPaymentRequest.ifPresent(paymentRequest ->
                        externalPaymentProcessor.process(paymentRequest)
                                .ifPresentOrElse(
                                        processedPaymentConsumer,
                                        () -> onProcessingFailed.accept(paymentRequest)
                                ));
            } catch (RuntimeException ex) {
                if (ex instanceof JedisException jedisEx) {
                    // printing any Jedis exception stack trace
                    logger.warn("Jedis exception occurred: {}", jedisEx.getMessage(), jedisEx);
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
