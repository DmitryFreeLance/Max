package com.defacto.maxbot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class ConversationStore {
  private final String dbPath;
  private final ObjectMapper mapper;

  public ConversationStore(String dbPath, ObjectMapper mapper) {
    this.dbPath = dbPath;
    this.mapper = mapper;
    init();
  }

  private void init() {
    File file = new File(dbPath);
    File parent = file.getParentFile();
    if (parent != null && !parent.exists()) {
      parent.mkdirs();
    }
    try (Connection conn = connect();
         Statement st = conn.createStatement()) {
      st.execute("CREATE TABLE IF NOT EXISTS conversations (" +
          "user_id INTEGER PRIMARY KEY," +
          "state TEXT NOT NULL," +
          "topic TEXT," +
          "data TEXT," +
          "phone TEXT," +
          "time_pref TEXT," +
          "updated_at INTEGER" +
          ")");
      st.execute("CREATE TABLE IF NOT EXISTS leads (" +
          "id INTEGER PRIMARY KEY AUTOINCREMENT," +
          "user_id INTEGER NOT NULL," +
          "topic TEXT," +
          "data TEXT," +
          "phone TEXT," +
          "time_pref TEXT," +
          "created_at INTEGER" +
          ")");
    } catch (SQLException e) {
      throw new RuntimeException("Failed to init DB", e);
    }
  }

  private Connection connect() throws SQLException {
    return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
  }

  public Conversation getConversation(long userId) {
    try (Connection conn = connect();
         PreparedStatement ps = conn.prepareStatement(
             "SELECT state, topic, data, phone, time_pref FROM conversations WHERE user_id = ?")) {
      ps.setLong(1, userId);
      try (ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          Conversation c = new Conversation(userId);
          c.state = parseState(rs.getString("state"));
          c.topic = safe(rs.getString("topic"));
          c.phone = safe(rs.getString("phone"));
          c.timePref = safe(rs.getString("time_pref"));
          c.data = parseData(rs.getString("data"));
          return c;
        }
      }
    } catch (SQLException e) {
      throw new RuntimeException("DB read failed", e);
    }
    Conversation c = new Conversation(userId);
    upsertConversation(c);
    return c;
  }

  public void upsertConversation(Conversation c) {
    try (Connection conn = connect();
         PreparedStatement ps = conn.prepareStatement(
             "INSERT INTO conversations(user_id, state, topic, data, phone, time_pref, updated_at) " +
                 "VALUES(?, ?, ?, ?, ?, ?, ?) " +
                 "ON CONFLICT(user_id) DO UPDATE SET " +
                 "state=excluded.state, topic=excluded.topic, data=excluded.data, " +
                 "phone=excluded.phone, time_pref=excluded.time_pref, updated_at=excluded.updated_at")) {
      ps.setLong(1, c.userId);
      ps.setString(2, c.state.name());
      ps.setString(3, c.topic);
      ps.setString(4, serializeData(c.data));
      ps.setString(5, c.phone);
      ps.setString(6, c.timePref);
      ps.setLong(7, Instant.now().toEpochMilli());
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("DB upsert failed", e);
    }
  }

  public void saveLead(Conversation c) {
    try (Connection conn = connect();
         PreparedStatement ps = conn.prepareStatement(
             "INSERT INTO leads(user_id, topic, data, phone, time_pref, created_at) VALUES(?, ?, ?, ?, ?, ?)")) {
      ps.setLong(1, c.userId);
      ps.setString(2, c.topic);
      ps.setString(3, serializeData(c.data));
      ps.setString(4, c.phone);
      ps.setString(5, c.timePref);
      ps.setLong(6, Instant.now().toEpochMilli());
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("DB lead insert failed", e);
    }
  }

  public void resetConversation(Conversation c) {
    c.state = Conversation.State.START;
    c.topic = "";
    c.phone = "";
    c.timePref = "";
    c.data.clear();
    upsertConversation(c);
  }

  private Conversation.State parseState(String raw) {
    if (raw == null || raw.isBlank()) {
      return Conversation.State.START;
    }
    try {
      return Conversation.State.valueOf(raw);
    } catch (IllegalArgumentException e) {
      return Conversation.State.START;
    }
  }

  private Map<String, String> parseData(String raw) {
    if (raw == null || raw.isBlank()) {
      return new HashMap<>();
    }
    try {
      Map<String, String> map = mapper.readValue(raw, Map.class);
      return new HashMap<>(map);
    } catch (Exception e) {
      return new HashMap<>();
    }
  }

  private String serializeData(Map<String, String> data) {
    try {
      return mapper.writeValueAsString(data);
    } catch (JsonProcessingException e) {
      return "{}";
    }
  }

  private String safe(String s) {
    return s == null ? "" : s;
  }
}
