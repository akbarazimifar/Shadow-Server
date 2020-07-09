package su.sres.shadowserver.tests.controllers;

import com.google.common.collect.ImmutableSet;
import org.glassfish.jersey.test.grizzly.GrizzlyWebTestContainerFactory;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.util.Optional;

import io.dropwizard.auth.PolymorphicAuthValueFactoryProvider;
import io.dropwizard.testing.junit.ResourceTestRule;

import su.sres.shadowserver.auth.AmbiguousIdentifier;
import su.sres.shadowserver.auth.DisabledPermittedAccount;
import su.sres.shadowserver.configuration.CdnConfiguration;
import su.sres.shadowserver.controllers.ProfileController;
import su.sres.shadowserver.controllers.RateLimitExceededException;
import su.sres.shadowserver.entities.Profile;
import su.sres.shadowserver.limits.RateLimiter;
import su.sres.shadowserver.limits.RateLimiters;
import su.sres.shadowserver.storage.Account;
import su.sres.shadowserver.storage.AccountsManager;
import su.sres.shadowserver.storage.UsernamesManager;
import su.sres.shadowserver.tests.util.AuthHelper;
import su.sres.shadowserver.util.SystemMapper;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.*;

public class ProfileControllerTest {

	private static AccountsManager  accountsManager     = mock(AccountsManager.class );
	  private static UsernamesManager usernamesManager    = mock(UsernamesManager.class);
	  private static RateLimiters     rateLimiters        = mock(RateLimiters.class    );
	  private static RateLimiter      rateLimiter         = mock(RateLimiter.class     );
	  private static RateLimiter      usernameRateLimiter = mock(RateLimiter.class     );
	  private static CdnConfiguration configuration       = mock(CdnConfiguration.class);

  static {
    when(configuration.getUri()).thenReturn("uri");
    when(configuration.getAccessKey()).thenReturn("accessKey");
    when(configuration.getAccessSecret()).thenReturn("accessSecret");
    when(configuration.getRegion()).thenReturn("us-east-1");
    when(configuration.getBucket()).thenReturn("profile-bucket");
  }

  @ClassRule
  public static final ResourceTestRule resources;
  // static initializer for resources
  static {
      try {
        resources = ResourceTestRule.builder()
                .addProvider(AuthHelper.getAuthFilter())
                .addProvider(new PolymorphicAuthValueFactoryProvider.Binder<>(ImmutableSet.of(Account.class, DisabledPermittedAccount.class)))
                .setMapper(SystemMapper.getMapper())
                .setTestContainerFactory(new GrizzlyWebTestContainerFactory())
                .addResource(new ProfileController(rateLimiters,
                        accountsManager,
                        usernamesManager,
                        configuration)).build();
      }
      catch (final Exception e)
      {
        throw new RuntimeException("Failed to create ResourceTestRule instance in static block.",e);
      }
  }

  @Before
  public void setup() throws Exception {

    when(rateLimiters.getProfileLimiter()).thenReturn(rateLimiter);
    when(rateLimiters.getUsernameLookupLimiter()).thenReturn(usernameRateLimiter);

    Account profileAccount = mock(Account.class);

    when(profileAccount.getIdentityKey()).thenReturn("bar");
    when(profileAccount.getProfileName()).thenReturn("baz");
    when(profileAccount.getAvatar()).thenReturn("profiles/bang");
    when(profileAccount.getAvatarDigest()).thenReturn("buh");
    when(profileAccount.getUuid()).thenReturn(AuthHelper.VALID_UUID_TWO);
    when(profileAccount.isEnabled()).thenReturn(true);
    when(profileAccount.isUuidAddressingSupported()).thenReturn(false);

    Account capabilitiesAccount = mock(Account.class);

    when(capabilitiesAccount.getIdentityKey()).thenReturn("barz");
    when(capabilitiesAccount.getProfileName()).thenReturn("bazz");
    when(capabilitiesAccount.getAvatar()).thenReturn("profiles/bangz");
    when(capabilitiesAccount.getAvatarDigest()).thenReturn("buz");
    when(capabilitiesAccount.isEnabled()).thenReturn(true);
    when(capabilitiesAccount.isUuidAddressingSupported()).thenReturn(true);

    when(accountsManager.get(AuthHelper.VALID_NUMBER_TWO)).thenReturn(Optional.of(profileAccount));
    when(accountsManager.get(AuthHelper.VALID_UUID_TWO)).thenReturn(Optional.of(profileAccount));
    when(usernamesManager.get(AuthHelper.VALID_UUID_TWO)).thenReturn(Optional.of("n00bkiller"));
    when(usernamesManager.get("n00bkiller")).thenReturn(Optional.of(AuthHelper.VALID_UUID_TWO));
    when(accountsManager.get(argThat((ArgumentMatcher<AmbiguousIdentifier>) identifier -> identifier != null && identifier.hasUserLogin() && identifier.getUserLogin().equals(AuthHelper.VALID_NUMBER_TWO)))).thenReturn(Optional.of(profileAccount));
    when(accountsManager.get(argThat((ArgumentMatcher<AmbiguousIdentifier>) identifier -> identifier != null && identifier.hasUuid() && identifier.getUuid().equals(AuthHelper.VALID_UUID_TWO)))).thenReturn(Optional.of(profileAccount));
    
    when(accountsManager.get(AuthHelper.VALID_NUMBER)).thenReturn(Optional.of(capabilitiesAccount));
    when(accountsManager.get(argThat((ArgumentMatcher<AmbiguousIdentifier>) identifier -> identifier != null && identifier.hasUserLogin() && identifier.getUserLogin().equals(AuthHelper.VALID_NUMBER)))).thenReturn(Optional.of(capabilitiesAccount));
    
    Mockito.clearInvocations(accountsManager);
  }
  
  @Test
  public void testProfileGetByUuid() throws RateLimitExceededException {
    Profile profile= resources.getJerseyTest()
                              .target("/v1/profile/" + AuthHelper.VALID_UUID_TWO)
                              .request()
                              .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
                              .get(Profile.class);

    assertThat(profile.getIdentityKey()).isEqualTo("bar");
    assertThat(profile.getName()).isEqualTo("baz");
    assertThat(profile.getAvatar()).isEqualTo("profiles/bang");
    assertThat(profile.getUsername()).isEqualTo("n00bkiller");

    verify(accountsManager, times(1)).get(argThat((ArgumentMatcher<AmbiguousIdentifier>) identifier -> identifier != null && identifier.hasUuid() && identifier.getUuid().equals(AuthHelper.VALID_UUID_TWO)));
    verify(usernamesManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO));
    verify(rateLimiter, times(2)).validate(eq(AuthHelper.VALID_NUMBER));
    reset(rateLimiter);
  }

  @Test
  public void testProfileGetByNumber() throws RateLimitExceededException {
    Profile profile= resources.getJerseyTest()
                              .target("/v1/profile/" + AuthHelper.VALID_NUMBER_TWO)
                              .request()
                              .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
                              .get(Profile.class);

    assertThat(profile.getIdentityKey()).isEqualTo("bar");
    assertThat(profile.getName()).isEqualTo("baz");
    assertThat(profile.getAvatar()).isEqualTo("profiles/bang");
    assertThat(profile.getCapabilities().isUuid()).isFalse();
    assertThat(profile.getUsername()).isNull();
    assertThat(profile.getUuid()).isNull();;

    verify(accountsManager, times(1)).get(argThat((ArgumentMatcher<AmbiguousIdentifier>) identifier -> identifier != null && identifier.hasUserLogin() && identifier.getUserLogin().equals(AuthHelper.VALID_NUMBER_TWO)));
    verifyNoMoreInteractions(usernamesManager);
    verify(rateLimiter, times(1)).validate(eq(AuthHelper.VALID_NUMBER));
    reset(rateLimiter);
  }

  @Test
  public void testProfileGetByUsername() throws RateLimitExceededException {
    Profile profile= resources.getJerseyTest()
                              .target("/v1/profile/username/n00bkiller")
                              .request()
                              .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
                              .get(Profile.class);

    assertThat(profile.getIdentityKey()).isEqualTo("bar");
    assertThat(profile.getName()).isEqualTo("baz");
    assertThat(profile.getAvatar()).isEqualTo("profiles/bang");
    assertThat(profile.getUsername()).isEqualTo("n00bkiller");
    assertThat(profile.getUuid()).isEqualTo(AuthHelper.VALID_UUID_TWO);

    verify(accountsManager, times(1)).get(eq(AuthHelper.VALID_UUID_TWO));
    verify(usernamesManager, times(1)).get(eq("n00bkiller"));
    verify(usernameRateLimiter, times(1)).validate(eq(AuthHelper.VALID_UUID.toString()));
  }
  
  @Test
  public void testProfileGetUnauthorized() throws Exception {
    Response response = resources.getJerseyTest()
                                 .target("/v1/profile/" + AuthHelper.VALID_NUMBER_TWO)
                                 .request()
                                 .get();

    assertThat(response.getStatus()).isEqualTo(401);
  }
  
  @Test
  public void testProfileGetByUsernameUnauthorized() throws Exception {
    Response response = resources.getJerseyTest()
                                 .target("/v1/profile/username/n00bkiller")
                                 .request()
                                 .get();

    assertThat(response.getStatus()).isEqualTo(401);
  }


  @Test
  public void testProfileGetByUsernameNotFound() throws RateLimitExceededException {
    Response response = resources.getJerseyTest()
                              .target("/v1/profile/username/n00bkillerzzzzz")
                              .request()
                              .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
                              .get();

    assertThat(response.getStatus()).isEqualTo(404);

    verify(usernamesManager, times(1)).get(eq("n00bkillerzzzzz"));
    verify(usernameRateLimiter, times(1)).validate(eq(AuthHelper.VALID_UUID.toString()));
    reset(usernameRateLimiter);
  }
  
  @Test
  public void testProfileGetDisabled() throws Exception {
    Response response = resources.getJerseyTest()
                                 .target("/v1/profile/" + AuthHelper.VALID_NUMBER_TWO)
                                 .request()
                                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.DISABLED_NUMBER, AuthHelper.DISABLED_PASSWORD))
                                 .get();

    assertThat(response.getStatus()).isEqualTo(401);
  }
  
  @Test
  public void testProfileCapabilities() throws Exception {
    Profile profile= resources.getJerseyTest()
                              .target("/v1/profile/" + AuthHelper.VALID_NUMBER)
                              .request()
                              .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
                              .get(Profile.class);

    assertThat(profile.getCapabilities().isUuid()).isTrue();
  }
  
  @Test
  public void testSetProfileName() {
    Response response = resources.getJerseyTest()
                                 .target("/v1/profile/name/123456789012345678901234567890123456789012345678901234567890123456789012")
                                 .request()
                                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
                                 .put(Entity.text(""));

    assertThat(response.getStatus()).isEqualTo(204);

    verify(accountsManager, times(1)).update(any(Account.class));
  }

  @Test
  public void testSetProfileNameExtended() {
    Response response = resources.getJerseyTest()
                                 .target("/v1/profile/name/123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678")
                                 .request()
                                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
                                 .put(Entity.text(""));

    assertThat(response.getStatus()).isEqualTo(204);

    verify(accountsManager, times(1)).update(any(Account.class));
  }

  @Test
  public void testSetProfileNameWrongSize() {
    Response response = resources.getJerseyTest()
                                 .target("/v1/profile/name/1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890")
                                 .request()
                                 .header("Authorization", AuthHelper.getAuthHeader(AuthHelper.VALID_NUMBER, AuthHelper.VALID_PASSWORD))
                                 .put(Entity.text(""));

    assertThat(response.getStatus()).isEqualTo(400);
    verifyNoMoreInteractions(accountsManager);
  }
}