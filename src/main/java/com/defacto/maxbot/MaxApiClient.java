package com.defacto.maxbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class MaxApiClient {
  private final OkHttpClient http;
  private final ObjectMapper mapper;
  private final String baseUrl;
  private final String accessToken;

  public MaxApiClient(String baseUrl, String accessToken, ObjectMapper mapper) {
    this.http = new OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(10))
        .readTimeout(Duration.ofSeconds(40))
        .writeTimeout(Duration.ofSeconds(10))
        .callTimeout(Duration.ofSeconds(45))
        .build();
    this.mapper = mapper;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.accessToken = accessToken;
  }

  public JsonNode getUpdates(Long marker, int timeoutSeconds, int limit, String typesCsv) throws IOException {
    HttpUrl.Builder url = HttpUrl.parse(baseUrl + "/updates").newBuilder()
        .addQueryParameter("timeout", String.valueOf(timeoutSeconds))
        .addQueryParameter("limit", String.valueOf(limit));
    if (marker != null) {
      url.addQueryParameter("marker", String.valueOf(marker));
    }
    if (typesCsv != null && !typesCsv.isBlank()) {
      url.addQueryParameter("types", typesCsv);
    }

    Request request = new Request.Builder()
        .url(url.build())
        .get()
        .header("Authorization", accessToken)
        .build();

    try (Response resp = http.newCall(request).execute()) {
      if (!resp.isSuccessful()) {
        throw new IOException("GET /updates failed: " + resp.code());
      }
      String body = resp.body() == null ? "{}" : resp.body().string();
      return mapper.readTree(body);
    }
  }

  public void sendMessage(long userId, String text, List<List<Button>> buttons) throws IOException {
    HttpUrl url = HttpUrl.parse(baseUrl + "/messages").newBuilder()
        .addQueryParameter("user_id", String.valueOf(userId))
        .build();

    Map<String, Object> body = MessageBuilder.textWithKeyboard(text, buttons);
    String json = mapper.writeValueAsString(body);

    Request request = new Request.Builder()
        .url(url)
        .post(RequestBody.create(json, MediaType.parse("application/json")))
        .header("Authorization", accessToken)
        .build();

    try (Response resp = http.newCall(request).execute()) {
      if (!resp.isSuccessful()) {
        throw new IOException("POST /messages failed: " + resp.code());
      }
    }
  }

  public void sendMessage(long userId, String text) throws IOException {
    sendMessage(userId, text, null);
  }

  public void answerCallback(String callbackId, String notification) throws IOException {
    HttpUrl url = HttpUrl.parse(baseUrl + "/answers").newBuilder().build();

    Map<String, Object> payload = MessageBuilder.answerCallback(callbackId, notification);
    String json = mapper.writeValueAsString(payload);

    Request request = new Request.Builder()
        .url(url)
        .post(RequestBody.create(json, MediaType.parse("application/json")))
        .header("Authorization", accessToken)
        .build();

    try (Response resp = http.newCall(request).execute()) {
      if (!resp.isSuccessful()) {
        throw new IOException("POST /answers failed: " + resp.code());
      }
    }
  }

  public void subscribeWebhook(String url, String secret, String updateTypesCsv) throws IOException {
    HttpUrl endpoint = HttpUrl.parse(baseUrl + "/subscriptions").newBuilder().build();
    Map<String, Object> body = MessageBuilder.webhookSubscription(url, secret, updateTypesCsv);
    String json = mapper.writeValueAsString(body);

    Request request = new Request.Builder()
        .url(endpoint)
        .post(RequestBody.create(json, MediaType.parse("application/json")))
        .header("Authorization", accessToken)
        .build();

    try (Response resp = http.newCall(request).execute()) {
      if (!resp.isSuccessful()) {
        throw new IOException("POST /subscriptions failed: " + resp.code());
      }
    }
  }
}
