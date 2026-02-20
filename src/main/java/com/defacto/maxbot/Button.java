package com.defacto.maxbot;

import java.util.LinkedHashMap;
import java.util.Map;

public class Button {
  public final String type;
  public final String text;
  public final String url;
  public final String payload;

  private Button(String type, String text, String url, String payload) {
    this.type = type;
    this.text = text;
    this.url = url;
    this.payload = payload;
  }

  public static Button message(String text) {
    return new Button("message", text, null, null);
  }

  public static Button link(String text, String url) {
    return new Button("link", text, url, null);
  }

  public static Button callback(String text, String payload) {
    return new Button("callback", text, null, payload);
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("type", type);
    map.put("text", text);
    if (url != null) {
      map.put("url", url);
    }
    if (payload != null) {
      map.put("payload", payload);
    }
    return map;
  }
}
