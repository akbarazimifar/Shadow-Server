/*
 * Copyright (C) 2013 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package su.sres.shadowserver;

import com.fasterxml.jackson.annotation.JsonProperty;

import su.sres.websocket.configuration.WebSocketConfiguration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import io.dropwizard.Configuration;
import io.dropwizard.client.JerseyClientConfiguration;

import su.sres.shadowserver.configuration.*;

/** @noinspection MismatchedQueryAndUpdateOfCollection, WeakerAccess */
public class WhisperServerConfiguration extends Configuration {

  @NotNull
  @Valid
  @JsonProperty
  private PushConfiguration push;

  @NotNull
  @Valid
  @JsonProperty
  private AttachmentsConfiguration attachments;
  
  @NotNull
  @Valid
  @JsonProperty
  private AttachmentsConfiguration debuglogs;

  @NotNull
  @Valid
  @JsonProperty
  private CdnConfiguration cdn;

  @NotNull
  @Valid
  @JsonProperty
  private RedisConfiguration cache;

  @NotNull
  @Valid
  @JsonProperty
  private RedisConfiguration directory;
  
  @NotNull
  @Valid
  @JsonProperty
  private AccountDatabaseCrawlerConfiguration accountDatabaseCrawler;

  @NotNull
  @Valid
  @JsonProperty
  private RedisConfiguration pushScheduler;

  @NotNull
  @Valid
  @JsonProperty
  private MessageCacheConfiguration messageCache;

  @Valid
  @NotNull
  @JsonProperty
  private DatabaseConfiguration messageStore;
  
  @Valid
  @NotNull
  @JsonProperty
  private DatabaseConfiguration abuseDatabase;

  @Valid
  @NotNull
  @JsonProperty
  private List<TestDeviceConfiguration> testDevices = new LinkedList<>();

  @Valid
  @NotNull
  @JsonProperty
  private List<MaxDeviceConfiguration> maxDevices = new LinkedList<>();

  /*
   * excluded federation configuration, let's preserve for future purposes 
   * 
  @Valid
  @JsonProperty
  private FederationConfiguration federation = new FederationConfiguration();
  
  */

  @Valid
  @NotNull
  @JsonProperty
  private DatabaseConfiguration keysDatabase;

  @Valid
  @NotNull
  @JsonProperty
  private DatabaseConfiguration accountsDatabase;

  @JsonProperty
  private DatabaseConfiguration read_database;

  @Valid
  @NotNull
  @JsonProperty
  private RateLimitsConfiguration limits = new RateLimitsConfiguration();

  @Valid
  @NotNull
  @JsonProperty
  private JerseyClientConfiguration httpClient = new JerseyClientConfiguration();

  @Valid
  @NotNull
  @JsonProperty
  private WebSocketConfiguration webSocket = new WebSocketConfiguration();

  @Valid
  @NotNull
  @JsonProperty
  private TurnConfiguration turn;

  @Valid
  @NotNull
  @JsonProperty
  private GcmConfiguration gcm;

  @Valid
  @NotNull
  @JsonProperty
  private ApnConfiguration apn;
  
  @Valid
  @NotNull
  @JsonProperty
  private UnidentifiedDeliveryConfiguration unidentifiedDelivery;
  
  @Valid
  @NotNull
  @JsonProperty
  private RecaptchaConfiguration recaptcha;
  
  @Valid
  @NotNull
  @JsonProperty
  private SecureStorageServiceConfiguration storageService;
  
  @Valid
  @NotNull
  @JsonProperty
  private SecureBackupServiceConfiguration backupService;
  
  @NotNull
  @JsonProperty
  private LocalParametersConfiguration localParametersConfiguration;
  
  @NotNull
  @JsonProperty
  private ServiceConfiguration serviceConfiguration;
  
  private Map<String, String> transparentDataIndex = new HashMap<>();
  
  public RecaptchaConfiguration getRecaptchaConfiguration() {
	    return recaptcha;
	  }

  public WebSocketConfiguration getWebSocketConfiguration() {
    return webSocket;
  }

  public PushConfiguration getPushConfiguration() {
    return push;
  }

  public JerseyClientConfiguration getJerseyClientConfiguration() {
    return httpClient;
  }

  public AttachmentsConfiguration getAttachmentsConfiguration() {
    return attachments;
  }
  
  public AttachmentsConfiguration getDebugLogsConfiguration() {
	    return debuglogs;
	  }

  public RedisConfiguration getCacheConfiguration() {
    return cache;
  }

  public RedisConfiguration getDirectoryConfiguration() {
    return directory;
  }
  
  public SecureStorageServiceConfiguration getSecureStorageServiceConfiguration() {
	    return storageService;
	  }
  
  public AccountDatabaseCrawlerConfiguration getAccountDatabaseCrawlerConfiguration() {
	    return accountDatabaseCrawler;
	  }

  public MessageCacheConfiguration getMessageCacheConfiguration() {
    return messageCache;
  }

  public RedisConfiguration getPushScheduler() {
    return pushScheduler;
  }

  public DatabaseConfiguration getMessageStoreConfiguration() {
    return messageStore;
  }
  
  public DatabaseConfiguration getAbuseDatabaseConfiguration() {
	    return abuseDatabase;
	  }

  public DatabaseConfiguration getKeysDatabase() {
	    return keysDatabase;
  }

  public DatabaseConfiguration getAccountsDatabaseConfiguration() {
	    return accountsDatabase;
  }

  public RateLimitsConfiguration getLimitsConfiguration() {
    return limits;
  }

  /*
   * excluded federation configuration, let's preserve for future purposes 
   *   
  public FederationConfiguration getFederationConfiguration() {
    return federation;
  }
  
  */

  public TurnConfiguration getTurnConfiguration() {
    return turn;
  }

  public GcmConfiguration getGcmConfiguration() {
    return gcm;
  }

  public ApnConfiguration getApnConfiguration() {
    return apn;
  }

  public CdnConfiguration getCdnConfiguration() {
	    return cdn;
  }
  
  public UnidentifiedDeliveryConfiguration getDeliveryCertificate() {
	    return unidentifiedDelivery;
	  }

  public Map<String, Integer> getTestDevices() {
    Map<String, Integer> results = new HashMap<>();

    for (TestDeviceConfiguration testDeviceConfiguration : testDevices) {
      results.put(testDeviceConfiguration.getNumber(),
                  testDeviceConfiguration.getCode());
    }

    return results;
  }

  public Map<String, Integer> getMaxDevices() {
    Map<String, Integer> results = new HashMap<>();

    for (MaxDeviceConfiguration maxDeviceConfiguration : maxDevices) {
      results.put(maxDeviceConfiguration.getNumber(),
                  maxDeviceConfiguration.getCount());
    }

    return results;
  }
  
  public Map<String, String> getTransparentDataIndex() {
	    return transparentDataIndex;
	  }
  
  public SecureBackupServiceConfiguration getSecureBackupServiceConfiguration() {
	    return backupService;
	  }
  
  public LocalParametersConfiguration getLocalParametersConfiguration() {
	    return localParametersConfiguration;
	  }
  
  public ServiceConfiguration getServiceConfiguration() {
	    return serviceConfiguration;
	  }
}
