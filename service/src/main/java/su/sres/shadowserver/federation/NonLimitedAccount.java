package su.sres.shadowserver.federation;


import com.fasterxml.jackson.annotation.JsonIgnore;
//import com.google.common.base.Optional;
import java.util.Optional;

import su.sres.shadowserver.storage.Account;
import su.sres.shadowserver.storage.Device;

public class NonLimitedAccount extends Account {

  @JsonIgnore
  private final String number;

  @JsonIgnore
  private final String relay;

  @JsonIgnore
  private final long deviceId;

  public NonLimitedAccount(String number, long deviceId, String relay) {
    this.number   = number;
    this.deviceId = deviceId;
    this.relay    = relay;
  }

  @Override
  public String getNumber() {
    return number;
  }

  @Override
  public boolean isRateLimited() {
    return false;
  }

  @Override
  public Optional<String> getRelay() {
    return Optional.of(relay);
  }
/* TODO correct this constructor and uncomment in case of future use of federation 
 * 
  @Override
  public Optional<Device> getAuthenticatedDevice() {
    return Optional.of(new Device(deviceId, null, null, null, null, null, null, null, false, 0, null, System.currentTimeMillis(), System.currentTimeMillis(), false, false, "NA"));
  }
  */
}
