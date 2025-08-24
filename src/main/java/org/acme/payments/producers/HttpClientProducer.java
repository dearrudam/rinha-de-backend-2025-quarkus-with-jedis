package org.acme.payments.producers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.net.http.HttpClient;

@ApplicationScoped
public class HttpClientProducer {

    @Produces
    public HttpClient getClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .executor(Runnable::run)
                .build();
    }
}
