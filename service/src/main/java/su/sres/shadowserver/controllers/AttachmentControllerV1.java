/*
 * Original software: Copyright 2013-2020 Signal Messenger, LLC
 * Modified software: Copyright 2019-2022 Anton Alipov, sole trader
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package su.sres.shadowserver.controllers;

import com.codahale.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import io.dropwizard.auth.Auth;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import io.minio.http.Method;
import su.sres.shadowserver.entities.AttachmentDescriptorV1;
import su.sres.shadowserver.entities.AttachmentUri;
import su.sres.shadowserver.limits.RateLimiters;
import su.sres.shadowserver.s3.UrlSigner;
import su.sres.shadowserver.storage.Account;

@Path("/v1/attachments")
public class AttachmentControllerV1 extends AttachmentControllerBase {

  @SuppressWarnings("unused")
  private final Logger logger = LoggerFactory.getLogger(AttachmentControllerV1.class);

  private final RateLimiters rateLimiters;  
  private final UrlSigner urlSigner;

  public AttachmentControllerV1(RateLimiters rateLimiters, String accessKey, String accessSecret, String bucket, String endpoint) {   

    this.rateLimiters = rateLimiters;    
    this.urlSigner = new UrlSigner(accessKey, accessSecret, bucket, endpoint);
  }

  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public AttachmentDescriptorV1 allocateAttachment(@Auth Account account)
      throws RateLimitExceededException, InvalidKeyException, ErrorResponseException, IllegalArgumentException, InsufficientDataException, InternalException, InvalidResponseException, NoSuchAlgorithmException, XmlParserException, ServerException, IOException {
    if (account.isRateLimited()) {
      rateLimiters.getAttachmentLimiter().validate(account.getUserLogin());
    }

    long attachmentId = generateAttachmentId();
    URL url = urlSigner.getPreSignedUrl(attachmentId, Method.PUT);

    return new AttachmentDescriptorV1(attachmentId, url.toExternalForm());

  }

  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/{attachmentId}")
  public AttachmentUri redirectToAttachment(@Auth Account account,
      @PathParam("attachmentId") long attachmentId)
      // excluded federation, reserved for future purposes
      // @QueryParam("relay") Optional<String> relay)
      throws IOException, InvalidKeyException, ErrorResponseException, IllegalArgumentException, InsufficientDataException, InternalException, InvalidResponseException, NoSuchAlgorithmException, XmlParserException, ServerException {
    /*
     * excluded federation, reserved for future purposes
     *
     * try { if (!relay.isPresent()) { return new
     * AttachmentUri(urlSigner.getPreSignedUrl(attachmentId, HttpMethod.GET,
     * Stream.of(UNACCELERATED_REGIONS).anyMatch(region ->
     * account.getNumber().startsWith(region)))); } else { return new
     * AttachmentUri(federatedClientManager.getClient(relay.get()).
     * getSignedAttachmentUri(attachmentId)); } } catch (NoSuchPeerException e) {
     * logger.info("No such peer: " + relay); throw new
     * WebApplicationException(Response.status(404).build()); }
     */
    return new AttachmentUri(urlSigner.getPreSignedUrl(attachmentId, Method.GET));
  }
}
