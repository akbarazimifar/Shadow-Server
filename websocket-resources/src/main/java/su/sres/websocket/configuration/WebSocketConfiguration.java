package su.sres.websocket.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;

import su.sres.websocket.logging.WebsocketRequestLoggerFactory;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import io.dropwizard.request.logging.LogbackAccessRequestLogFactory;
import io.dropwizard.request.logging.RequestLogFactory;

public class WebSocketConfiguration {

  @Valid
  @NotNull
  @JsonProperty
  private WebsocketRequestLoggerFactory requestLog = new WebsocketRequestLoggerFactory();

  public WebsocketRequestLoggerFactory getRequestLog() {
    return requestLog;
  }
}
