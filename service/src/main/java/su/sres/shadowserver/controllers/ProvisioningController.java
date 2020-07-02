package su.sres.shadowserver.controllers;

import com.codahale.metrics.annotation.Timed;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;

import io.dropwizard.auth.Auth;
import su.sres.shadowserver.entities.ProvisioningMessage;
import su.sres.shadowserver.limits.RateLimiters;
import su.sres.shadowserver.push.PushSender;
import su.sres.shadowserver.push.WebsocketSender;
import su.sres.shadowserver.storage.Account;
import su.sres.shadowserver.util.Base64;
import su.sres.shadowserver.websocket.InvalidWebsocketAddressException;
import su.sres.shadowserver.websocket.ProvisioningAddress;

@Path("/v1/provisioning")
public class ProvisioningController {

  private final RateLimiters    rateLimiters;
  private final WebsocketSender websocketSender;

  public ProvisioningController(RateLimiters rateLimiters, PushSender pushSender) {
    this.rateLimiters    = rateLimiters;
    this.websocketSender = pushSender.getWebSocketSender();
  }

  @Timed
  @Path("/{destination}")
  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public void sendProvisioningMessage(@Auth                     Account source,
                                      @PathParam("destination") String destinationName,
                                      @Valid                    ProvisioningMessage message)
      throws RateLimitExceededException, InvalidWebsocketAddressException, IOException
  {
    rateLimiters.getMessagesLimiter().validate(source.getUserLogin());

    if (!websocketSender.sendProvisioningMessage(new ProvisioningAddress(destinationName, 0),
                                                 Base64.decode(message.getBody())))
    {
      throw new WebApplicationException(Response.Status.NOT_FOUND);
    }
  }
}
