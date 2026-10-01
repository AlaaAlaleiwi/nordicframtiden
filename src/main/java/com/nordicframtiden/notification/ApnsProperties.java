package com.nordicframtiden.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.apns")
public class ApnsProperties {
  private boolean enabled;
  private String teamId = "";
  private String keyId = "";
  private String privateKey = "";
  private String bundleId = "";

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public String getTeamId() { return teamId; }
  public void setTeamId(String teamId) { this.teamId = teamId; }
  public String getKeyId() { return keyId; }
  public void setKeyId(String keyId) { this.keyId = keyId; }
  public String getPrivateKey() { return privateKey; }
  public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
  public String getBundleId() { return bundleId; }
  public void setBundleId(String bundleId) { this.bundleId = bundleId; }
}
