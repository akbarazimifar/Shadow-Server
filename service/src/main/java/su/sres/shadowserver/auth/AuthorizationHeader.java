/*
 * Original software: Copyright 2013-2020 Signal Messenger, LLC
 * Modified software: Copyright 2019-2022 Anton Alipov, sole trader
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package su.sres.shadowserver.auth;


import java.io.IOException;
import java.util.Base64;

import su.sres.shadowserver.util.Util;

public class AuthorizationHeader {

	private final AmbiguousIdentifier identifier;
	  private final long                deviceId;
	  private final String              password;

	  private AuthorizationHeader(AmbiguousIdentifier identifier, long deviceId, String password) {
	    this.identifier = identifier;
	    this.deviceId   = deviceId;
	    this.password   = password;
  }

  public static AuthorizationHeader fromUserAndPassword(String user, String password) throws InvalidAuthorizationHeaderException {
    try {
      String[] numberAndId = user.split("\\.");
      return new AuthorizationHeader(new AmbiguousIdentifier(numberAndId[0]),
                                     numberAndId.length > 1 ? Long.parseLong(numberAndId[1]) : 1,
                                     password);
    } catch (NumberFormatException nfe) {
      throw new InvalidAuthorizationHeaderException(nfe);
    }
  }

  public static AuthorizationHeader fromFullHeader(String header) throws InvalidAuthorizationHeaderException {
    try {
      if (header == null) {
        throw new InvalidAuthorizationHeaderException("Null header");
      }

      String[] headerParts = header.split(" ");

      if (headerParts == null || headerParts.length < 2) {
        throw new InvalidAuthorizationHeaderException("Invalid authorization header: " + header);
      }

      if (!"Basic".equals(headerParts[0])) {
        throw new InvalidAuthorizationHeaderException("Unsupported authorization method: " + headerParts[0]);
      }

      String concatenatedValues = new String(Base64.getDecoder().decode(headerParts[1]));

      if (Util.isEmpty(concatenatedValues)) {
        throw new InvalidAuthorizationHeaderException("Bad decoded value: " + concatenatedValues);
      }

      String[] credentialParts = concatenatedValues.split(":");

      if (credentialParts == null || credentialParts.length < 2) {
        throw new InvalidAuthorizationHeaderException("Badly formated credentials: " + concatenatedValues);
      }

      return fromUserAndPassword(credentialParts[0], credentialParts[1]);
    } catch (IllegalArgumentException e) {
      throw new InvalidAuthorizationHeaderException(e);
    }
  }

  public AmbiguousIdentifier getIdentifier() {
	    return identifier;
  }

  public long getDeviceId() {
	  return deviceId;
  }

  public String getPassword() {
    return password;
  }
}
