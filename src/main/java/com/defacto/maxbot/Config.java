package com.defacto.maxbot;

import java.util.Objects;

public class Config {
  public final String accessToken;
  public final String apiBase;
  public final String operatorUserId;
  public final String operatorChatUrl;
  public final String dbPath;
  public final String mode;
  public final String webhookUrl;
  public final String webhookSecret;
  public final int port;

  private Config(
      String accessToken,
      String apiBase,
      String operatorUserId,
      String operatorChatUrl,
      String dbPath,
      String mode,
      String webhookUrl,
      String webhookSecret,
      int port
  ) {
    this.accessToken = accessToken;
    this.apiBase = apiBase;
    this.operatorUserId = operatorUserId;
    this.operatorChatUrl = operatorChatUrl;
    this.dbPath = dbPath;
    this.mode = mode;
    this.webhookUrl = webhookUrl;
    this.webhookSecret = webhookSecret;
    this.port = port;
  }

  public static Config fromEnv() {
    String accessToken = getenv("MAX_ACCESS_TOKEN", "");
    String apiBase = getenv("MAX_API_BASE", "https://platform-api.max.ru");
    String operatorUserId = getenv("OPERATOR_USER_ID", "");
    String operatorChatUrl = getenv("OPERATOR_CHAT_URL", "");
    String dbPath = getenv("DB_PATH", "./data/bot.db");
    String mode = getenv("MODE", "polling").toLowerCase();
    String webhookUrl = getenv("WEBHOOK_URL", "");
    String webhookSecret = getenv("WEBHOOK_SECRET", "");
    int port = Integer.parseInt(getenv("PORT", "8080"));

    return new Config(
        accessToken,
        apiBase,
        operatorUserId,
        operatorChatUrl,
        dbPath,
        mode,
        webhookUrl,
        webhookSecret,
        port
    );
  }

  public void validate() {
    if (accessToken.isBlank()) {
      throw new IllegalStateException("MAX_ACCESS_TOKEN is required");
    }
    if (operatorUserId.isBlank()) {
      throw new IllegalStateException("OPERATOR_USER_ID is required");
    }
    if (Objects.equals(mode, "webhook") && webhookUrl.isBlank()) {
      throw new IllegalStateException("WEBHOOK_URL is required when MODE=webhook");
    }
  }

  private static String getenv(String key, String def) {
    String v = System.getenv(key);
    return v == null ? def : v;
  }
}
