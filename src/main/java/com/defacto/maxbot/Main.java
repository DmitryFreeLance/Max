package com.defacto.maxbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class Main {
  public static void main(String[] args) throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    Config config = Config.fromEnv();
    config.validate();

    ConversationStore store = new ConversationStore(config.dbPath, mapper);
    MaxApiClient client = new MaxApiClient(config.apiBase, config.accessToken, mapper);
    UpdateProcessor processor = new UpdateProcessor(client, store, config, mapper);

    if ("webhook".equals(config.mode)) {
      client.subscribeWebhook(config.webhookUrl, config.webhookSecret, "message_created,message_callback,bot_started");
      startWebhookServer(config, processor);
      return;
    }

    runPollingLoop(client, processor);
  }

  private static void runPollingLoop(MaxApiClient client, UpdateProcessor processor) {
    Long marker = null;
    while (true) {
      try {
        JsonNode resp = client.getUpdates(marker, 30, 50, "message_created,message_callback,bot_started");
        if (resp.has("updates")) {
          for (JsonNode upd : resp.get("updates")) {
            processor.handleUpdate(upd);
          }
        }
        if (resp.has("marker")) {
          marker = resp.get("marker").asLong();
        }
      } catch (Exception e) {
        System.err.println("Polling error: " + e.getMessage());
        sleep(Duration.ofSeconds(2));
      }
    }
  }

  private static void startWebhookServer(Config config, UpdateProcessor processor) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(config.port), 0);
    server.createContext("/webhook", exchange -> handleWebhook(exchange, config, processor));
    server.setExecutor(null);
    server.start();
    System.out.println("Webhook server started on port " + config.port);
  }

  private static void handleWebhook(HttpExchange exchange, Config config, UpdateProcessor processor) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(405, -1);
      return;
    }
    if (!config.webhookSecret.isBlank()) {
      String header = exchange.getRequestHeaders().getFirst("X-Max-Bot-Api-Secret");
      if (header == null || !header.equals(config.webhookSecret)) {
        exchange.sendResponseHeaders(403, -1);
        return;
      }
    }

    String body;
    try (InputStream is = exchange.getRequestBody()) {
      body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
    try {
      processor.handleWebhookPayload(body);
      byte[] ok = "OK".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, ok.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(ok);
      }
    } catch (Exception e) {
      byte[] err = "ERROR".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(500, err.length);
      try (OutputStream os = exchange.getResponseBody()) {
        os.write(err);
      }
    }
  }

  private static void sleep(Duration d) {
    try {
      Thread.sleep(d.toMillis());
    } catch (InterruptedException ignored) {
      Thread.currentThread().interrupt();
    }
  }
}
