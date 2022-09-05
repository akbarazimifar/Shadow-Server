/*
 * Original software: Copyright 2013-2020 Signal Messenger, LLC
 * Modified software: Copyright 2019-2022 Anton Alipov, sole trader
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package su.sres.shadowserver.util;

import com.amazonaws.HttpMethod;

import su.sres.shadowserver.s3.UrlSigner;

import org.junit.Test;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class UrlSignerTest {

  @Test
  public void testTransferAcceleration() {
	  UrlSigner signer = new UrlSigner("foo", "bar", "attachments-test");
    URL url = signer.getPreSignedUrl(1234, HttpMethod.GET);

    assertThat(url).hasHost("attachments-test.s3-accelerate.amazonaws.com");
  }

  @Test
  public void testTransferUnaccelerated() {
	  UrlSigner signer = new UrlSigner("foo", "bar", "attachments-test");
    URL url = signer.getPreSignedUrl(1234, HttpMethod.GET);

    assertThat(url).hasHost("s3.amazonaws.com");
  }

}
