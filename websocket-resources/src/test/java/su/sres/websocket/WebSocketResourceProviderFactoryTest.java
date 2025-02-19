/*
 * Copyright 2013-2020 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package su.sres.websocket;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.UpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.Test;
import su.sres.websocket.auth.AuthenticationException;
import su.sres.websocket.auth.WebSocketAuthenticator;
import su.sres.websocket.setup.WebSocketEnvironment;

import javax.security.auth.Subject;
import java.io.IOException;
import java.security.Principal;
import java.util.Optional;

import io.dropwizard.jersey.DropwizardResourceConfig;

public class WebSocketResourceProviderFactoryTest {

  @Test
  public void testUnauthorized() throws AuthenticationException, IOException {
    ResourceConfig jerseyEnvironment = new DropwizardResourceConfig();
    WebSocketEnvironment environment = mock(WebSocketEnvironment.class);
    WebSocketAuthenticator authenticator = mock(WebSocketAuthenticator.class);
    ServletUpgradeRequest request = mock(ServletUpgradeRequest.class);
    ServletUpgradeResponse response = mock(ServletUpgradeResponse.class);

    when(environment.getAuthenticator()).thenReturn(authenticator);
    when(authenticator.authenticate(eq(request))).thenReturn(new WebSocketAuthenticator.AuthenticationResult<>(Optional.empty(), true));
    when(environment.jersey()).thenReturn(jerseyEnvironment);

    WebSocketResourceProviderFactory factory = new WebSocketResourceProviderFactory(environment, Account.class);
    Object connection = factory.createWebSocket(request, response);

    assertNull(connection);
    verify(response).sendForbidden(eq("Unauthorized"));
    verify(authenticator).authenticate(eq(request));
  }

  @Test
  public void testValidAuthorization() throws AuthenticationException {
    ResourceConfig jerseyEnvironment = new DropwizardResourceConfig();
    WebSocketEnvironment environment = mock(WebSocketEnvironment.class);
    WebSocketAuthenticator authenticator = mock(WebSocketAuthenticator.class);
    ServletUpgradeRequest request = mock(ServletUpgradeRequest.class);
    ServletUpgradeResponse response = mock(ServletUpgradeResponse.class);
    Session session = mock(Session.class);
    Account account = new Account();

    when(environment.getAuthenticator()).thenReturn(authenticator);
    when(authenticator.authenticate(eq(request))).thenReturn(new WebSocketAuthenticator.AuthenticationResult<>(Optional.of(account), true));
    when(environment.jersey()).thenReturn(jerseyEnvironment);
    when(session.getUpgradeRequest()).thenReturn(mock(UpgradeRequest.class));

    WebSocketResourceProviderFactory factory = new WebSocketResourceProviderFactory(environment, Account.class);
    Object connection = factory.createWebSocket(request, response);

    assertNotNull(connection);
    verifyNoMoreInteractions(response);
    verify(authenticator).authenticate(eq(request));

    ((WebSocketResourceProvider) connection).onWebSocketConnect(session);

    assertNotNull(((WebSocketResourceProvider) connection).getContext().getAuthenticated());
    assertEquals(((WebSocketResourceProvider) connection).getContext().getAuthenticated(), account);
  }

  @Test
  public void testErrorAuthorization() throws AuthenticationException, IOException {
    ResourceConfig jerseyEnvironment = new DropwizardResourceConfig();
    WebSocketEnvironment environment = mock(WebSocketEnvironment.class);
    WebSocketAuthenticator authenticator = mock(WebSocketAuthenticator.class);
    ServletUpgradeRequest request = mock(ServletUpgradeRequest.class);
    ServletUpgradeResponse response = mock(ServletUpgradeResponse.class);

    when(environment.getAuthenticator()).thenReturn(authenticator);
    when(authenticator.authenticate(eq(request))).thenThrow(new AuthenticationException("database failure"));
    when(environment.jersey()).thenReturn(jerseyEnvironment);

    WebSocketResourceProviderFactory factory = new WebSocketResourceProviderFactory(environment, Account.class);
    Object connection = factory.createWebSocket(request, response);

    assertNull(connection);
    verify(response).sendError(eq(500), eq("Failure"));
    verify(authenticator).authenticate(eq(request));
  }

  @Test
  public void testConfigure() {
    ResourceConfig jerseyEnvironment = new DropwizardResourceConfig();
    WebSocketEnvironment environment = mock(WebSocketEnvironment.class);
    WebSocketServletFactory servletFactory = mock(WebSocketServletFactory.class);
    when(environment.jersey()).thenReturn(jerseyEnvironment);

    WebSocketResourceProviderFactory factory = new WebSocketResourceProviderFactory(environment, Account.class);
    factory.configure(servletFactory);

    verify(servletFactory).setCreator(eq(factory));
  }

  private static class Account implements Principal {
    @Override
    public String getName() {
      return null;
    }

    @Override
    public boolean implies(Subject subject) {
      return false;
    }
  }

}