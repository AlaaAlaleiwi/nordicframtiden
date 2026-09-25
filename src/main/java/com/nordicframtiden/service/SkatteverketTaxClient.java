package com.nordicframtiden.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SkatteverketTaxClient {
  private static final int MONTHLY_SALARY_TYPE = 2;

  private final boolean enabled;
  private final URI baseUri;
  private final String clientId;
  private final String clientSecret;
  private final Duration timeout;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  @Autowired
  public SkatteverketTaxClient(
      @Value("${app.skatteverket.tax.enabled:false}") boolean enabled,
      @Value("${app.skatteverket.tax.base-url}") URI baseUri,
      @Value("${app.skatteverket.tax.client-id:}") String clientId,
      @Value("${app.skatteverket.tax.client-secret:}") String clientSecret,
      @Value("${app.skatteverket.tax.timeout-seconds:10}") long timeoutSeconds,
      ObjectMapper objectMapper) {
    this(enabled, baseUri, clientId, clientSecret, Duration.ofSeconds(timeoutSeconds),
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build(), objectMapper);
  }

  SkatteverketTaxClient(boolean enabled, URI baseUri, String clientId, String clientSecret,
      Duration timeout, HttpClient httpClient, ObjectMapper objectMapper) {
    this.enabled = enabled;
    this.baseUri = baseUri;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.timeout = timeout;
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;

    if (enabled && (clientId.isBlank() || clientSecret.isBlank())) {
      throw new IllegalStateException(
          "Skatteverket tax API is enabled but client ID or client secret is missing");
    }
  }

  public OptionalInt lookupPreliminaryTax(
      int taxYear, int tableNumber, int taxColumn, int grossSalary) {
    if (!enabled) {
      return OptionalInt.empty();
    }

    URI uri = URI.create(baseUri + "/skatteavdrag"
        + "?inkomstar=" + taxYear
        + "&typ=" + MONTHLY_SALARY_TYPE
        + "&tabell=" + tableNumber
        + "&kolumn=" + taxColumn
        + "&bruttobelopp=" + grossSalary);

    String correlationId = UUID.randomUUID().toString();
    HttpRequest request = HttpRequest.newBuilder(uri)
        .timeout(timeout)
        .header("Accept", "application/json")
        .header("client_id", clientId)
        .header("client_secret", clientSecret)
        .header("SKV-CORRELATIONID", correlationId)
        .GET()
        .build();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 401 || response.statusCode() == 403) {
        throw new IllegalStateException("Skatteverket rejected the configured API credentials");
      }
      if (response.statusCode() >= 500 || response.statusCode() == 429) {
        throw new SkatteverketUnavailableException(
            "Skatteverket tax API returned HTTP " + response.statusCode());
      }
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalArgumentException(
            "Skatteverket tax API rejected the request with HTTP " + response.statusCode());
      }

      List<TaxResponse> results = objectMapper.readValue(
          response.body(), new TypeReference<List<TaxResponse>>() {});
      if (results.size() != 1) {
        throw new SkatteverketUnavailableException(
            "Skatteverket tax API returned an unexpected number of results");
      }

      TaxResponse result = results.getFirst();
      if (result.felkod() != 0 || result.skatteavdrag() == null) {
        throw new IllegalArgumentException(
            "Skatteverket rejected the tax parameters (code " + result.felkod() + "): "
                + (result.felmeddelande() == null ? "no details" : result.felmeddelande()));
      }
      return OptionalInt.of(result.skatteavdrag());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new SkatteverketUnavailableException("Skatteverket tax request was interrupted", exception);
    } catch (IOException exception) {
      throw new SkatteverketUnavailableException("Skatteverket tax request failed", exception);
    }
  }

  private record TaxResponse(Integer skatteavdrag, int felkod, String felmeddelande) {}

  public static final class SkatteverketUnavailableException extends RuntimeException {
    public SkatteverketUnavailableException(String message) {
      super(message);
    }

    public SkatteverketUnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
