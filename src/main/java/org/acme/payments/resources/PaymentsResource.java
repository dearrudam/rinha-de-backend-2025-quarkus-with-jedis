package org.acme.payments.resources;

import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.acme.payments.domain.PaymentRequest;
import org.acme.payments.domain.PaymentsService;

import java.time.Instant;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PaymentsResource {

    @Inject
    private PaymentsService paymentsService;

    @POST
    @Path("/payments")
    public Response pay(PaymentRequest paymentRequest) {
        paymentsService.accept(paymentRequest);
        return Response.status(Response.Status.ACCEPTED).build();
    }

    @POST
    @Path("/purge-payments")
    public Response purge() {
        paymentsService.purge();
        return Response.status(Response.Status.NO_CONTENT).build();
    }


    @GET
    @Path("/payments-summary")
    public Response summary(@QueryParam("from") Instant from,
                            @QueryParam("to") Instant to) {
        return Response.ok(paymentsService.summary(from, to)).build();
    }

}
