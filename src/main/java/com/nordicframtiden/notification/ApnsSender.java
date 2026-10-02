package com.nordicframtiden.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Component
public class ApnsSender {
  private static final Logger log = LoggerFactory.getLogger(ApnsSender.class);
  private final ApnsProperties properties;
  private final ApnsDeviceTokenRepository tokens;
  private final ObjectMapper objectMapper;
  private final HttpClient client = HttpClient.newBuilder()
      .version(HttpClient.Version.HTTP_2).connectTimeout(Duration.ofSeconds(10)).build();
  private volatile String providerToken;
  private volatile Instant providerTokenCreatedAt = Instant.EPOCH;
  private volatile PrivateKey signingKey;

  public ApnsSender(ApnsProperties properties, ApnsDeviceTokenRepository tokens, ObjectMapper objectMapper) {
    this.properties = properties;
    this.tokens = tokens;
    this.objectMapper = objectMapper;
  }

  public void send(ApnsDeviceToken device, String title, String body, Map<String, Object> data) {
    if (!properties.isEnabled()) return;
    try {
      var aps = Map.of("alert", Map.of("title", title, "body", body), "sound", "default");
      var payload = new java.util.HashMap<String, Object>(data);
      payload.put("aps", aps);
      String host = "production".equals(device.getEnvironment())
          ? "https://api.push.apple.com" : "https://api.sandbox.push.apple.com";
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(host + "/3/device/" + device.getToken()))
          .timeout(Duration.ofSeconds(15))
          .header("authorization", "bearer " + providerToken())
          .header("apns-topic", properties.getBundleId())
          .header("apns-push-type", "alert")
          .header("apns-priority", "10")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
          .build();
      client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
        if (response.statusCode() == 410 || response.statusCode() == 400 && response.body().contains("BadDeviceToken")) {
          tokens.deleteById(device.getId());
        } else if (response.statusCode() >= 300) {
          log.warn("APNs rejected notification with status {} (provider response redacted)", response.statusCode());
        }
      }).exceptionally(error -> {
        log.warn("APNs notification could not be delivered: {}", error.getClass().getSimpleName());
        return null;
      });
    } catch (Exception error) {
      log.warn("APNs notification could not be queued: {}", error.getClass().getSimpleName());
    }
  }

  private synchronized String providerToken() throws Exception {
    Instant now = Instant.now();
    if (providerToken != null && providerTokenCreatedAt.isAfter(now.minus(Duration.ofMinutes(50)))) {
      return providerToken;
    }
    providerToken = Jwts.builder()
        .setHeaderParam("kid", properties.getKeyId())
        .setIssuer(properties.getTeamId())
        .setIssuedAt(Date.from(now))
        .signWith(privateKey(), SignatureAlgorithm.ES256)
        .compact();
    providerTokenCreatedAt = now;
    return providerToken;
  }

  private PrivateKey privateKey() throws Exception {
    if (signingKey != null) return signingKey;
    String pem = properties.getPrivateKey().replace("\\n", "\n")
        .replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replaceAll("\\s", "");
    signingKey = KeyFactory.getInstance("EC")
        .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
    return signingKey;
  }
}
