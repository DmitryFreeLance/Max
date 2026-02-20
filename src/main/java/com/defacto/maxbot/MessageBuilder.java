package com.defacto.maxbot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MessageBuilder {
  private MessageBuilder() {}

  public static Map<String, Object> textWithKeyboard(String text, List<List<Button>> buttons) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("text", text);
    if (buttons != null && !buttons.isEmpty()) {
      body.put("attachments", List.of(inlineKeyboard(buttons)));
    }
    return body;
  }

  public static Map<String, Object> answerCallback(String callbackId, String notification) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("callback_id", callbackId);
    if (notification != null && !notification.isBlank()) {
      body.put("notification", notification);
    }
    return body;
  }

  public static Map<String, Object> webhookSubscription(String url, String secret, String updateTypesCsv) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("url", url);
    if (secret != null && !secret.isBlank()) {
      body.put("secret", secret);
    }
    if (updateTypesCsv != null && !updateTypesCsv.isBlank()) {
      List<String> types = new ArrayList<>();
      for (String t : updateTypesCsv.split(",")) {
        String trimmed = t.trim();
        if (!trimmed.isBlank()) {
          types.add(trimmed);
        }
      }
      body.put("update_types", types);
    }
    return body;
  }

  private static Map<String, Object> inlineKeyboard(List<List<Button>> rows) {
    List<List<Map<String, Object>>> buttons = new ArrayList<>();
    for (List<Button> row : rows) {
      List<Map<String, Object>> rowList = new ArrayList<>();
      for (Button b : row) {
        rowList.add(b.toMap());
      }
      buttons.add(rowList);
    }
    Map<String, Object> attachment = new LinkedHashMap<>();
    attachment.put("type", "inline_keyboard");
    attachment.put("payload", Map.of("buttons", buttons));
    return attachment;
  }
}
