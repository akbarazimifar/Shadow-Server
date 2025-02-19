/*
 * Copyright 2021 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package su.sres.shadowserver.limits;

import io.micrometer.core.instrument.Metrics;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import su.sres.shadowserver.push.APNSender;
import su.sres.shadowserver.push.ApnMessage;
import su.sres.shadowserver.push.ApnMessage.Type;
import su.sres.shadowserver.push.GCMSender;
import su.sres.shadowserver.push.GcmMessage;
import su.sres.shadowserver.push.NotPushRegisteredException;
import su.sres.shadowserver.storage.Account;
import su.sres.shadowserver.storage.Device;
import su.sres.shadowserver.storage.PushChallengeScyllaDb;
import su.sres.shadowserver.util.ua.ClientPlatform;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Optional;

import static com.codahale.metrics.MetricRegistry.name;

// TODO: iOS
public class PushChallengeManager {
  // private final APNSender apnSender;
  private final GCMSender gcmSender;

  private final PushChallengeScyllaDb pushChallengeScyllaDb;

  private final SecureRandom random = new SecureRandom();

  private static final int CHALLENGE_TOKEN_LENGTH = 16;
  private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);

  private static final String CHALLENGE_REQUESTED_COUNTER_NAME = name(PushChallengeManager.class, "requested");
  private static final String CHALLENGE_ANSWERED_COUNTER_NAME = name(PushChallengeManager.class, "answered");

  private static final String PLATFORM_TAG_NAME = "platform";
  private static final String SENT_TAG_NAME = "sent";
  private static final String SUCCESS_TAG_NAME = "success";

  public PushChallengeManager(
      // final APNSender apnSender,
      final GCMSender gcmSender,
      final PushChallengeScyllaDb pushChallengeScyllaDb) {

    // this.apnSender = apnSender;
    this.gcmSender = gcmSender;
    this.pushChallengeScyllaDb = pushChallengeScyllaDb;
  }

  public void sendChallenge(final Account account) throws NotPushRegisteredException {
    final Device masterDevice = account.getMasterDevice().orElseThrow(NotPushRegisteredException::new);

    if (StringUtils.isAllBlank(masterDevice.getGcmId(), masterDevice.getApnId())) {
      throw new NotPushRegisteredException();
    }

    final byte[] token = new byte[CHALLENGE_TOKEN_LENGTH];
    random.nextBytes(token);

    final boolean sent;
    final String platform;

    if (pushChallengeScyllaDb.add(account.getUuid(), token, CHALLENGE_TTL)) {
      final String tokenHex = Hex.encodeHexString(token);
      sent = true;

      if (StringUtils.isNotBlank(masterDevice.getGcmId())) {
        gcmSender.sendMessage(new GcmMessage(masterDevice.getGcmId(), account.getUserLogin(), 0, GcmMessage.Type.RATE_LIMIT_CHALLENGE, Optional.of(tokenHex)));
        platform = ClientPlatform.ANDROID.name().toLowerCase();
      } else if (StringUtils.isNotBlank(masterDevice.getApnId())) {
        // TODO: iOS
        // apnSender.sendMessage(new ApnMessage(masterDevice.getApnId(), account.getUserLogin(), 0, false, Type.RATE_LIMIT_CHALLENGE, Optional.of(tokenHex)));
        platform = ClientPlatform.IOS.name().toLowerCase();
      } else {
        throw new AssertionError();
      }
    } else {
      sent = false;
      platform = "unrecognized";
    }

    Metrics.counter(CHALLENGE_REQUESTED_COUNTER_NAME,
        PLATFORM_TAG_NAME, platform,
        SENT_TAG_NAME, String.valueOf(sent)).increment();
  }

  public boolean answerChallenge(final Account account, final String challengeTokenHex) {
    boolean success = false;

    try {
      success = pushChallengeScyllaDb.remove(account.getUuid(), Hex.decodeHex(challengeTokenHex));
    } catch (final DecoderException ignored) {
    }

    final String platform = account.getMasterDevice().map(masterDevice -> {
      if (StringUtils.isNotBlank(masterDevice.getGcmId())) {
        return ClientPlatform.IOS.name().toLowerCase();
      } else if (StringUtils.isNotBlank(masterDevice.getApnId())) {
        return ClientPlatform.ANDROID.name().toLowerCase();
      } else {
        return "unknown";
      }
    }).orElse("unknown");


    Metrics.counter(CHALLENGE_ANSWERED_COUNTER_NAME,
        PLATFORM_TAG_NAME, platform,
        SUCCESS_TAG_NAME, String.valueOf(success)).increment();

    return success;
  }
}
