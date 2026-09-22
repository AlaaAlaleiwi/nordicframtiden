package com.nordicframtiden.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.firebase")
public class FirebaseNotificationProperties {
  private boolean enabled;
  private String projectId = "";
  private String webApiKey = "";
  private String authDomain = "";
  private String storageBucket = "";
  private String messagingSenderId = "";
  private String appId = "";
  private String vapidPublicKey = "";

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public String getProjectId() { return projectId; }
  public void setProjectId(String projectId) { this.projectId = projectId; }
  public String getWebApiKey() { return webApiKey; }
  public void setWebApiKey(String webApiKey) { this.webApiKey = webApiKey; }
  public String getAuthDomain() { return authDomain; }
  public void setAuthDomain(String authDomain) { this.authDomain = authDomain; }
  public String getStorageBucket() { return storageBucket; }
  public void setStorageBucket(String storageBucket) { this.storageBucket = storageBucket; }
  public String getMessagingSenderId() { return messagingSenderId; }
  public void setMessagingSenderId(String messagingSenderId) { this.messagingSenderId = messagingSenderId; }
  public String getAppId() { return appId; }
  public void setAppId(String appId) { this.appId = appId; }
  public String getVapidPublicKey() { return vapidPublicKey; }
  public void setVapidPublicKey(String vapidPublicKey) { this.vapidPublicKey = vapidPublicKey; }
}
