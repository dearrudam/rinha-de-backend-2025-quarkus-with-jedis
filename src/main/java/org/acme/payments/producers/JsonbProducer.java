package org.acme.payments.producers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

@ApplicationScoped
public class JsonbProducer {

    @Produces
    public Jsonb jsonb() {
        return JsonbBuilder.create();
    }

}
