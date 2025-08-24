package org.acme.payments.producers;

import io.quarkus.runtime.annotations.RegisterForReflection;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import org.acme.payments.domain.HealthCheckRepository;
import org.acme.payments.domain.LeaderResolver;
import org.acme.payments.domain.PaymentsProcessor;
import org.acme.payments.domain.PaymentsRepository;
import org.acme.payments.domain.ExternalPaymentProcessor;
import org.acme.payments.producers.jedis.JedisHealthCheckRepository;
import org.acme.payments.producers.jedis.JedisLeaderResolver;
import org.acme.payments.producers.jedis.JedisPaymentsProcessor;
import org.acme.payments.producers.jedis.JedisPaymentsRepository;
import org.apache.commons.pool2.impl.DefaultEvictionPolicy;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import redis.clients.jedis.UnifiedJedis;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Optional.ofNullable;

@ApplicationScoped
// é necessário registrar DefaultEvictionPolicy para reflexão durante build nativo
@RegisterForReflection(targets = DefaultEvictionPolicy.class)
public class JedisProducer {

    @Inject
    @ConfigProperty(name = "jedis.url", defaultValue = "redis://localhost:6377")
    String jedisUrl;

    @Inject
    @ConfigProperty(name = "workers.size", defaultValue = "5")
    int workersSize;

    @Inject
    @VirtualThreads
    ExecutorService executeService;

    @Produces
    public PaymentsRepository paymentsRepository(Jsonb jsonb, UnifiedJedis jedis) {
        return new JedisPaymentsRepository(jsonb, jedis);
    }


    @Produces
    public UnifiedJedis unifiedJedis() {
        return new UnifiedJedis(jedisUrl);
    }

    @Produces
    public LeaderResolver leaderResolver(UnifiedJedis jedis) {
        return new JedisLeaderResolver(jedis);
    }

    @Produces
    public HealthCheckRepository healthCheckRepository(Jsonb jsonb, UnifiedJedis jedis) {
        return new JedisHealthCheckRepository(jsonb, jedis);
    }

    private AtomicReference<JedisPaymentsProcessor> paymentsProcessorRef = new AtomicReference<>();

    @Produces
    public PaymentsProcessor paymentsProcessor(Jsonb jsonb,
                                               UnifiedJedis jedis,
                                               PaymentsRepository paymentsRepository,
                                               ExternalPaymentProcessor externalPaymentProcessor) {
        return paymentsProcessorRef.updateAndGet(existing ->
                existing == null ? new JedisPaymentsProcessor(
                        jsonb,
                        jedis,
                        paymentsRepository,
                        externalPaymentProcessor,
                        new LinkedBlockingQueue<>(),
                        workersSize,
                        executeService).start() : existing);
    }

    @PreDestroy
    public void onDestroy() {
        ofNullable(paymentsProcessorRef.get())
                .ifPresent(JedisPaymentsProcessor::shutdown);
    }
}
