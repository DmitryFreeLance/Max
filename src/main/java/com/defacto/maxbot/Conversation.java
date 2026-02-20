package com.defacto.maxbot;

import java.util.HashMap;
import java.util.Map;

public class Conversation {
  public enum State {
    START,
    REPLAN_1,
    REPLAN_2,
    REPLAN_CITY,
    KAD_1,
    PRIREZ_1,
    PRIREZ_2,
    TAX_1,
    BUILD_1,
    BUILD_2,
    LAND_1,
    LAND_2,
    CONST_1,
    CONST_2,
    CONST_ISSUE,
    LEAD_PHONE_PROMPT,
    LEAD_PHONE_INPUT,
    LEAD_TIME
  }

  public long userId;
  public State state;
  public String topic;
  public String phone;
  public String timePref;
  public Map<String, String> data;

  public Conversation(long userId) {
    this.userId = userId;
    this.state = State.START;
    this.topic = "";
    this.phone = "";
    this.timePref = "";
    this.data = new HashMap<>();
  }
}
