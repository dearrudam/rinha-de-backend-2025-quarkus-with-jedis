package org.acme.payments.domain;

import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class ExternalPaymentHealthCheckChecker implements ExternalPaymentLoadBalancer {

    private final static Logger logger = LoggerFactory.getLogger(ExternalPaymentHealthCheckChecker.class);

    private final URI defaultURL;
    private final URI defaultHealthCheckURL;
    private final URI fallbackURL;
    private final URI fallbackHealthCheckURL;
    private final Optional<Duration> healthCheckInterval;
    private volatile boolean active;
    private final String instanceName;
    private final ExecutorService executorService;
    private final HealthCheckRepository healthCheckRepository;
    private final LeaderResolver leaderResolver;
    private final HttpClient httpClient;
    private final Jsonb jsonb;
    private final AtomicReference<HealthCheckData> activeData = new AtomicReference<>();

    @Inject
    public ExternalPaymentHealthCheckChecker(@ConfigProperty(name = "default.payment.url")
                                      URI defaultURL,
                                             @ConfigProperty(name = "fallback.payment.url")
                                      URI fallbackURL,
                                             @ConfigProperty(name = "payment.healthcheck.interval")
                                      Optional<Duration> healthCheckInterval,
                                             @ConfigProperty(name = "instance.name", defaultValue = "instance-" + "#{java.util.UUID.randomUUID()}")
                                      String instanceName,
                                             @VirtualThreads
                                      ExecutorService executorService,
                                             HealthCheckRepository healthCheckRepository,
                                             LeaderResolver leaderResolver,
                                             HttpClient httpClient) {
        this.defaultURL = defaultURL;
        this.defaultHealthCheckURL = defaultURL.resolve("/payments/service-health");
        this.healthCheckInterval = healthCheckInterval;
        this.fallbackURL = fallbackURL;
        this.fallbackHealthCheckURL = fallbackURL.resolve("/payments/service-health");
        this.instanceName = instanceName;
        this.executorService = executorService;
        this.healthCheckRepository = healthCheckRepository;
        this.leaderResolver = leaderResolver;
        this.httpClient = httpClient;
        this.jsonb = JsonbBuilder.create();
    }

    @PreDestroy
    public void stopCheck() {
        this.active = false;
    }

    public void startCheck() {
        if (!this.active) {
            this.active = true;
            executorService.execute(this::checkPaymentProcessor);
        }
    }

    private void checkPaymentProcessor() {
        Duration duration = healthCheckInterval.orElse(Duration.ofSeconds(4));
        while (active) {
            try {
                if (leaderResolver.amILeader(instanceName, duration)) {

                    var defaultHealthCheckData = CompletableFuture.supplyAsync(() -> this.checkHealth(defaultURL, defaultHealthCheckURL), executorService);
                    var fallbackHealthCheckData = CompletableFuture.supplyAsync(() -> this.checkHealth(fallbackURL, fallbackHealthCheckURL), executorService);

                    CompletableFuture.allOf(defaultHealthCheckData, fallbackHealthCheckData).join();

                    accept(HealthCheckData.compare(
                            defaultHealthCheckData.get(),
                            fallbackHealthCheckData.get(),
                            this::getDefaultTieBreaker));
                } else {
                    accept(healthCheckRepository.getActual());
                }
            } catch (Exception e) {
                logger.warn("Failed to check default payment processor", e);
            } finally {
                try {
                    Thread.sleep(duration);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private HealthCheckData checkHealth(URI uri, URI healthCheckURI) {
        try {
            var response = httpClient.send(HttpRequest.newBuilder(healthCheckURI)
                    .header("Content-Type", "application/json")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                HealthCheckData data = jsonb.fromJson(response.body(), HealthCheckData.class);
                return data.withURL(translateURL(uri), uri);
            }
            return null;
        } catch (Exception e) {
            logger.warn("Failed to check the health of {} processor", uri, e);
            return null;
        }
    }

    @Override
    public HealthCheckData resolve() {
        startCheck(); // make sure the check is started
        return Optional
                .ofNullable(activeData)
                .map(AtomicReference::get)
                .orElse(HealthCheckData.of(translateURL(defaultURL), defaultURL, false, 0));
    }

    private String translateURL(URI url) {
        return switch (url) {
            case URI u when u.equals(defaultURL) -> "default";
            case URI u when u.equals(fallbackURL) -> "fallback";
            default -> "none";
        };
    }

    public void accept(final HealthCheckData data) {
        this.activeData
                .updateAndGet(old -> {
                    try {
                        return data;
                    } finally {
                        healthCheckRepository.update(data);
                        logger.info("Actual: {}", data);
                    }
                });
    }

    private HealthCheckData getDefaultTieBreaker(HealthCheckData data, HealthCheckData data1) {
        return data.url().equals(defaultURL) ? data : data1;
    }

}
