package org.acme.payments.domain;

public interface HealthCheckRepository {

    void update(HealthCheckData healthCheckData);

    HealthCheckData getActual();

}
