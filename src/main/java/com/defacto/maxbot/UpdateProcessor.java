package com.defacto.maxbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UpdateProcessor {
  private final MaxApiClient client;
  private final ConversationStore store;
  private final Config config;
  private final ObjectMapper mapper;

  private static final String MENU_TAX = "Снижение кадастровой стоимости";
  private static final String MENU_REPLAN = "Перепланировка";
  private static final String MENU_KAD = "Кадастровые работы";
  private static final String MENU_PRIREZ = "Прирезка земли";
  private static final String MENU_BUILD = "Оформить дом / реконструкцию";
  private static final String MENU_LAND = "Земельные споры";
  private static final String MENU_CONST = "Споры в строительстве (для бизнеса)";
  private static final String MENU_CONTACT = "Связаться с юристом";
  private static final String DATA_LAST_MENU_AT = "last_menu_at";
  private static final String PRIVACY_URL = "https://disk.yandex.ru/i/XCoJa306kaZgiQ";

  public UpdateProcessor(MaxApiClient client, ConversationStore store, Config config, ObjectMapper mapper) {
    this.client = client;
    this.store = store;
    this.config = config;
    this.mapper = mapper;
  }

  public void handleUpdate(JsonNode update) throws IOException {
    String type = text(update, "update_type");
    if ("bot_started".equals(type)) {
      long userId = update.path("user").path("user_id").asLong(0);
      if (userId != 0) {
        Conversation c = store.getConversation(userId);
        if (!recentlySentMenu(c)) {
          sendMainMenu(c.userId);
        }
        store.resetConversation(c);
        markMenuSent(c);
      }
      return;
    }
    if ("message_created".equals(type)) {
      JsonNode message = update.get("message");
      if (message == null) return;
      JsonNode sender = message.get("sender");
      if (sender == null) return;
      boolean isBot = sender.path("is_bot").asBoolean(false);
      if (isBot) return;
      long userId = sender.path("user_id").asLong(0);
      if (userId == 0) return;
      System.out.println("[INFO] Incoming message from user_id=" + userId);
      String text = message.path("body").path("text").asText("").trim();
      if (text.isBlank()) return;
      handleText(userId, text);
      return;
    }

    if ("message_callback".equals(type)) {
      JsonNode callback = update.get("callback");
      if (callback == null) return;
      String callbackId = callback.path("callback_id").asText("");
      String payload = callback.path("payload").asText("");
      long userId = callback.path("user_id").asLong(0);
      if (userId == 0) {
        userId = callback.path("user").path("user_id").asLong(0);
      }
      if (userId == 0) {
        JsonNode message = update.get("message");
        if (message != null) {
          userId = message.path("sender").path("user_id").asLong(0);
        }
      }
      if (userId != 0) {
        System.out.println("[INFO] Callback from user_id=" + userId);
      }
      if (!payload.isBlank() && userId != 0) {
        handleText(userId, payload);
      }
      if (!callbackId.isBlank()) {
        client.answerCallback(callbackId, "Готово");
      }
    }
  }

  public void handleWebhookPayload(String body) throws IOException {
    JsonNode root = mapper.readTree(body);
    if (root.has("updates")) {
      for (JsonNode upd : root.get("updates")) {
        handleUpdate(upd);
      }
    } else if (root.has("update_type")) {
      handleUpdate(root);
    }
  }

  private void handleText(long userId, String text) throws IOException {
    String normalized = normalize(text);
    if (isMenuCommand(normalized)) {
      Conversation c = store.getConversation(userId);
      if (!recentlySentMenu(c)) {
        sendMainMenu(c.userId);
      }
      store.resetConversation(c);
      markMenuSent(c);
      return;
    }

    if (isContactShortcut(normalized)) {
      Conversation c = store.getConversation(userId);
      if (c.topic == null || c.topic.isBlank()) {
        c.topic = "Связаться с юристом";
      }
      store.upsertConversation(c);
      goLeadPhone(c);
      return;
    }

    Conversation c = store.getConversation(userId);

    switch (c.state) {
      case START -> handleStart(c, text);
      case REPLAN_1 -> handleReplan1(c, text);
      case REPLAN_2 -> handleReplan2(c, text);
      case REPLAN_CITY -> handleReplanCity(c, text);
      case KAD_1 -> handleKad1(c, text);
      case PRIREZ_1 -> handlePrirez1(c, text);
      case PRIREZ_2 -> handlePrirez2(c, text);
      case TAX_1 -> handleTax1(c, text);
      case BUILD_1 -> handleBuild1(c, text);
      case BUILD_2 -> handleBuild2(c, text);
      case LAND_1 -> handleLand1(c, text);
      case LAND_2 -> handleLand2(c, text);
      case CONST_1 -> handleConst1(c, text);
      case CONST_2 -> handleConst2(c, text);
      case CONST_ISSUE -> handleConstIssue(c, text);
      case LEAD_PHONE_PROMPT, LEAD_PHONE_INPUT -> handleLeadPhone(c, text);
      case LEAD_TIME -> handleLeadTime(c, text);
      default -> sendMainMenu(userId);
    }
  }

  private void handleStart(Conversation c, String text) throws IOException {
    String normalized = normalize(text);
    if (equalsAny(normalized, MENU_REPLAN)) {
      c.state = Conversation.State.REPLAN_1;
      c.topic = "Перепланировка";
      store.upsertConversation(c);
      sendReplan1(c.userId);
      return;
    }
    if (equalsAny(normalized, MENU_KAD)) {
      c.state = Conversation.State.KAD_1;
      c.topic = "Кадастровые работы";
      store.upsertConversation(c);
      sendKad1(c.userId);
      return;
    }
    if (equalsAny(normalized, MENU_PRIREZ)) {
      c.state = Conversation.State.PRIREZ_1;
      c.topic = "Прирезка";
      store.upsertConversation(c);
      sendPrirez1(c.userId);
      return;
    }
    if (equalsAny(normalized, MENU_TAX)) {
      c.state = Conversation.State.TAX_1;
      c.topic = "Снижение налога/аренды";
      store.upsertConversation(c);
      sendTax1(c.userId);
      return;
    }
    if (equalsAny(normalized, MENU_BUILD)) {
      c.state = Conversation.State.BUILD_1;
      c.topic = "Оформление/реконструкция";
      store.upsertConversation(c);
      sendBuild1(c.userId);
      return;
    }
    if (equalsAny(normalized, MENU_LAND)) {
      c.state = Conversation.State.LAND_1;
      c.topic = "Земельный спор";
      store.upsertConversation(c);
      sendLand1(c.userId);
      return;
    }
    if (equalsAny(normalized, MENU_CONST)) {
      c.state = Conversation.State.CONST_1;
      c.topic = "Строительный спор";
      store.upsertConversation(c);
      sendConst1(c.userId);
      return;
    }
    sendMainMenu(c.userId);
  }

  private void handleReplan1(Conversation c, String text) throws IOException {
    String normalized = normalize(text);
    if (equalsAny(normalized, "Жилое")) {
      c.data.put("replan_type", "жилое");
    } else if (equalsAny(normalized, "Нежилое")) {
      c.data.put("replan_type", "нежилое");
    } else {
      sendReplan1(c.userId);
      return;
    }
    c.state = Conversation.State.REPLAN_2;
    store.upsertConversation(c);
    sendReplan2(c.userId);
  }

  private void handleReplan2(Conversation c, String text) throws IOException {
    String normalized = normalize(text);
    if (equalsAny(normalized, "Ставрополь")) {
      c.data.put("replan_city", "Ставрополь");
      store.upsertConversation(c);
      goLeadPhone(c);
      return;
    }
    if (equalsAny(normalized, "Другой город")) {
      c.state = Conversation.State.REPLAN_CITY;
      store.upsertConversation(c);
      sendTextWithContact(c.userId, "Укажите город/район (одной строкой).\nНапример: Ставрополь.");
      return;
    }
    sendReplan2(c.userId);
  }

  private void handleReplanCity(Conversation c, String text) throws IOException {
    if (isMainMenuSelection(normalize(text))) {
      sendTextWithContact(c.userId, "Укажите город/район (одной строкой).\nНапример: Ставрополь.");
      return;
    }
    if (text.isBlank()) {
      sendTextWithContact(c.userId, "Укажите город/район (одной строкой).\nНапример: Ставрополь.");
      return;
    }
    c.data.put("replan_city", text.trim());
    store.upsertConversation(c);
    goLeadPhone(c);
  }

  private void handleKad1(Conversation c, String text) throws IOException {
    String normalized = normalize(text);
    if (equalsAny(normalized, "Межевание земли")) {
      c.data.put("kad_type", "межевание");
    } else if (equalsAny(normalized, "Техплан на здание/помещение")) {
      c.data.put("kad_type", "техплан");
    } else {
      sendKad1(c.userId);
      return;
    }
    store.upsertConversation(c);
    goLeadPhone(c);
  }

  private void handlePrirez1(Conversation c, String text) throws IOException {
    String normalized = normalize(text);
    if (equalsAny(normalized, "ИЖС", "Садоводство", "Коммерция", "ЛПХ", "Другое")) {
      c.data.put("prirez_purpose", text.trim());
      c.state = Conversation.State.PRIREZ_2;
      store.upsertConversation(c);
      sendTextWithContact(c.userId, "Укажите населённый пункт.\nНапример: Ставрополь, Михайловск.");
      return;
    }
    sendPrirez1(c.userId);
  }

  private void handlePrirez2(Conversation c, String text) throws IOException {
    if (isMainMenuSelection(normalize(text))) {
      sendTextWithContact(c.userId, "Укажите населённый пункт (одной строкой).\nНапример: Ставрополь.");
      return;
    }
    if (text.isBlank()) {
      sendTextWithContact(c.userId, "Укажите населённый пункт (одной строкой).\nНапример: Ставрополь.");
      return;
    }
    c.data.put("prirez_settlement", text.trim());
    store.upsertConversation(c);
    goLeadPhone(c);
  }

  private void handleTax1(Conversation c, String text) throws IOException {
    if (isMainMenuSelection(normalize(text))) {
      sendTax1(c.userId);
      return;
    }
    if (text.isBlank()) {
      sendTax1(c.userId);
      return;
    }
    c.data.put("tax_input", text.trim());
    store.upsertConversation(c);
    goLeadPhone(c);
  }

  private void handleBuild1(Conversation c, String text) throws IOException {
    String normalized = normalize(text);
    if (equalsAny(normalized,
        "Жилой дом — реконструкция",
        "Жилой дом — новая постройка",
        "Коммерческое — реконструкция",
        "Коммерческое — новая постройка")) {
      c.data.put("build_type", text.trim());
      c.state = Conversation.State.BUILD_2;
      store.upsertConversation(c);
      sendTextWithContact(c.userId, "Укажите населённый пункт (одной строкой).\nНапример: Ставрополь.");
      return;
    }
    sendBuild1(c.userId);
  }

  private void handleBuild2(Conversation c, String text) throws IOException {
    if (isMainMenuSelection(normalize(text))) {
      sendTextWithContact(c.userId, "Укажите населённый пункт (одной строкой).\nНапример: Ставрополь.");
      return;
    }
    if (text.isBlank()) {
      sendTextWithContact(c.userId, "Укажите населённый пункт (одной строкой).\nНапример: Ставрополь.");
      return;
    }
    c.data.put("build_settlement", text.trim());
    store.upsertConversation(c);
    goLeadPhone(c);
  }

  private void handleLand1(Conversation c, String text) throws IOException {
    if (isMainMenuSelection(normalize(text))) {
      sendTextWithContact(c.userId, "Укажите населённый пункт (одной строкой).\nНапример: Ставрополь.");
      return;
    }
    if (text.isBlank()) {
      sendTextWithContact(c.userId, "Укажите населённый пункт (одной строкой).\nНапример: Ставрополь.");
      return;
    }
    c.data.put("land_settlement", text.trim());
    c.state = Conversation.State.LAND_2;
    store.upsertConversation(c);
    sendTextWithContact(c.userId, "Кратко опишите ситуацию (1–2 предложения).\nЭто поможет понять суть спора.");
  }

  private void handleLand2(Conversation c, String text) throws IOException {
    if (isMainMenuSelection(normalize(text))) {
      sendTextWithContact(c.userId, "Кратко опишите ситуацию (1–2 предложения).\nЭто поможет понять суть спора.");
      return;
    }
    if (text.isBlank()) {
      sendTextWithContact(c.userId, "Кратко опишите ситуацию (1–2 предложения).\nЭто поможет понять суть спора.");
      return;
    }
    c.data.put("land_desc", text.trim());
    store.upsertConversation(c);
    goLeadPhone(c);
  }

  private void handleConst1(Conversation c, String text) throws IOException {
    String normalized = normalize(text);
    if (equalsAny(normalized, "Заказчик", "Подрядчик", "Субподрядчик", "Поставщик")) {
      c.data.put("const_role", text.trim());
      c.state = Conversation.State.CONST_2;
      store.upsertConversation(c);
      sendConst2(c.userId);
      return;
    }
    sendConst1(c.userId);
  }

  private void handleConst2(Conversation c, String text) throws IOException {
    String normalized = normalize(text);
    if (equalsAny(normalized,
        "Не оплатили / удерживают оплату",
        "Срыв сроков / штрафы / неустойка",
        "Дефекты / переделка / качество работ",
        "Спор по актам (КС-2/КС-3/УПД)",
        "Поставка: брак / недопоставка",
        "Расторжение / односторонний отказ")) {
      c.data.put("const_issue", text.trim());
      store.upsertConversation(c);
      goLeadPhone(c);
      return;
    }
    if (equalsAny(normalized, "Другое (напишу)")) {
      c.state = Conversation.State.CONST_ISSUE;
      store.upsertConversation(c);
      sendTextWithContact(c.userId, "Коротко опишите проблему (одной строкой).\nНапример: «Не оплатили работы по договору».");
      return;
    }
    sendConst2(c.userId);
  }

  private void handleConstIssue(Conversation c, String text) throws IOException {
    if (isMainMenuSelection(normalize(text))) {
      sendTextWithContact(c.userId, "Коротко опишите проблему (одной строкой).\nНапример: «Не оплатили работы по договору».");
      return;
    }
    if (text.isBlank()) {
      sendTextWithContact(c.userId, "Коротко опишите проблему (одной строкой).\nНапример: «Не оплатили работы по договору».");
      return;
    }
    c.data.put("const_issue", text.trim());
    store.upsertConversation(c);
    goLeadPhone(c);
  }

  private void handleLeadPhone(Conversation c, String text) throws IOException {
    String normalized = normalize(text);
    if (equalsAny(normalized, "Оставить номер")) {
      c.state = Conversation.State.LEAD_PHONE_INPUT;
      store.upsertConversation(c);
      client.sendMessage(c.userId, "Пожалуйста, введите номер в формате +7…\nНапример: +7 900 123-45-67");
      return;
    }
    String phone = extractPhone(text);
    if (phone == null) {
      client.sendMessage(c.userId, "Пожалуйста, введите номер в формате +7…\nНапример: +7 900 123-45-67");
      c.state = Conversation.State.LEAD_PHONE_INPUT;
      store.upsertConversation(c);
      return;
    }
    c.phone = phone;
    c.state = Conversation.State.LEAD_TIME;
    store.upsertConversation(c);
    sendLeadTime(c.userId);
  }

  private void handleLeadTime(Conversation c, String text) throws IOException {
    String normalized = normalize(text);
    if (!equalsAny(normalized,
        "Утром (09:00–12:00)",
        "Днём (12:00–15:00)",
        "Вечером (15:00–18:00)",
        "Не важно")) {
      sendLeadTime(c.userId);
      return;
    }
    c.timePref = cleanLabel(text);
    c.topic = buildTopic(c);
    store.saveLead(c);
    sendLeadConfirm(c.userId);
    notifyOperator(c);
    store.resetConversation(c);
  }

  private void sendMainMenu(long userId) throws IOException {
    String text = "Здравствуйте! 👋\n" +
        "Вас приветствует юридический центр «Де‑Факто».\n" +
        "Выберите интересующий вас вопрос ниже 👇";
    List<List<Button>> buttons = new ArrayList<>();
    buttons.add(List.of(Button.message("💰 " + MENU_TAX)));
    buttons.add(List.of(Button.message("🏗️ " + MENU_REPLAN)));
    buttons.add(List.of(Button.message("📐 " + MENU_KAD)));
    buttons.add(List.of(Button.message("➕ " + MENU_PRIREZ)));
    buttons.add(List.of(Button.message("🏠 " + MENU_BUILD)));
    buttons.add(List.of(Button.message("🧭 " + MENU_LAND)));
    buttons.add(List.of(Button.message("🏢 " + MENU_CONST)));
    buttons.add(List.of(Button.message("👨‍⚖️ " + MENU_CONTACT)));
    client.sendMessage(userId, text, buttons);
  }

  private void sendReplan1(long userId) throws IOException {
    String text = "Перепланировка.\n" +
        "Какое помещение вас интересует?";
    List<List<Button>> buttons = withContactButton(
        List.of(
            List.of(Button.message("🏠 Жилое"), Button.message("🏢 Нежилое"))
        )
    );
    client.sendMessage(userId, text, buttons);
  }

  private void sendReplan2(long userId) throws IOException {
    String text = "Где находится объект?\n" +
        "Если не Ставрополь — выберите «Другой город».";
    List<List<Button>> buttons = withContactButton(
        List.of(
            List.of(Button.message("📍 Ставрополь"), Button.message("🌍 Другой город"))
        )
    );
    client.sendMessage(userId, text, buttons);
  }

  private void sendKad1(long userId) throws IOException {
    String text = "Кадастровые работы.\n" +
        "Что нужно сделать?";
    List<List<Button>> buttons = withContactButton(
        List.of(
            List.of(Button.message("📏 Межевание земли")),
            List.of(Button.message("🧾 Техплан на здание/помещение"))
        )
    );
    client.sendMessage(userId, text, buttons);
  }

  private void sendPrirez1(long userId) throws IOException {
    String text = "Прирезка земли.\n" +
        "Назначение участка?";
    List<List<Button>> buttons = withContactButton(
        List.of(
            List.of(Button.message("🏡 ИЖС"), Button.message("🌿 Садоводство")),
            List.of(Button.message("🏬 Коммерция"), Button.message("🐄 ЛПХ")),
            List.of(Button.message("❓ Другое"))
        )
    );
    client.sendMessage(userId, text, buttons);
  }

  private void sendTax1(long userId) throws IOException {
    String text = "Снижение платежей по недвижимости/земле.\n" +
        "Укажите кадастровый номер (если несколько — через запятую)\n" +
        "или адрес объекта.";
    client.sendMessage(userId, text, withContactButton(List.of()));
  }

  private void sendBuild1(long userId) throws IOException {
    String text = "Оформление/реконструкция.\n" +
        "Что нужно оформить?";
    List<List<Button>> buttons = withContactButton(
        List.of(
            List.of(Button.message("🏠 Жилой дом — реконструкция")),
            List.of(Button.message("🏡 Жилой дом — новая постройка")),
            List.of(Button.message("🏢 Коммерческое — реконструкция")),
            List.of(Button.message("🏗️ Коммерческое — новая постройка"))
        )
    );
    client.sendMessage(userId, text, buttons);
  }

  private void sendLand1(long userId) throws IOException {
    sendTextWithContact(userId, "Укажите населённый пункт (одной строкой).\nНапример: Ставрополь.");
  }

  private void sendConst1(long userId) throws IOException {
    String text = "Споры в строительстве.\n" +
        "Ваша роль в проекте?";
    List<List<Button>> buttons = withContactButton(
        List.of(
            List.of(Button.message("👤 Заказчик"), Button.message("🛠️ Подрядчик")),
            List.of(Button.message("🔧 Субподрядчик"), Button.message("📦 Поставщик"))
        )
    );
    client.sendMessage(userId, text, buttons);
  }

  private void sendConst2(long userId) throws IOException {
    String text = "Что случилось?\n" +
        "Выберите наиболее подходящий вариант.";
    List<List<Button>> buttons = withContactButton(
        List.of(
            List.of(Button.message("💸 Не оплатили / удерживают оплату")),
            List.of(Button.message("⏱️ Срыв сроков / штрафы / неустойка")),
            List.of(Button.message("🧱 Дефекты / переделка / качество работ")),
            List.of(Button.message("📄 Спор по актам (КС-2/КС-3/УПД)")),
            List.of(Button.message("📦 Поставка: брак / недопоставка")),
            List.of(Button.message("🧾 Расторжение / односторонний отказ")),
            List.of(Button.message("✍️ Другое (напишу)"))
        )
    );
    client.sendMessage(userId, text, buttons);
  }

  private void goLeadPhone(Conversation c) throws IOException {
    c.state = Conversation.State.LEAD_PHONE_PROMPT;
    store.upsertConversation(c);
    String text = "Чтобы юрист подсказал по вашему случаю, оставьте номер телефона.\n" +
        "Мы на связи Пн–Пт 09:00–18:00.\n" +
        "Номер используется только для связи по вашему обращению.\n" +
        "Отправляя номер, вы соглашаетесь на [политику конфиденциальности](" + PRIVACY_URL + ").";
    List<List<Button>> buttons = List.of(List.of(Button.message("📞 Оставить номер")));
    client.sendMessage(c.userId, text, buttons, "markdown");
  }

  private void sendLeadTime(long userId) throws IOException {
    String text = "Когда удобнее связаться?\n" +
        "Выберите подходящий интервал.";
    List<List<Button>> buttons = List.of(
        List.of(Button.message("🌅 Утром (09:00–12:00)")),
        List.of(Button.message("🌞 Днём (12:00–15:00)")),
        List.of(Button.message("🌆 Вечером (15:00–18:00)")),
        List.of(Button.message("✅ Не важно"))
    );
    client.sendMessage(userId, text, buttons);
  }

  private void sendLeadConfirm(long userId) throws IOException {
    String text = "Спасибо! Заявка принята ✅\n" +
        "Мы свяжемся с вами в ближайшее рабочее время (Пн–Пт 09:00–18:00).\n" +
        "Если удобно — можно написать юристу прямо сейчас или вернуться в меню.";
    List<List<Button>> buttons = new ArrayList<>();
    boolean hasLink = false;
    if (config.operatorChatUrl != null && !config.operatorChatUrl.isBlank()) {
      String url = config.operatorChatUrl.trim();
      if (url.startsWith("http://") || url.startsWith("https://")) {
        buttons.add(List.of(Button.link("Написать юристу прямо сейчас", url)));
        hasLink = true;
      } else {
        System.err.println("[WARN] OPERATOR_CHAT_URL must be http/https. Skipping link button.");
      }
    }
    buttons.add(List.of(Button.message("⬅️ В меню")));
    try {
      client.sendMessage(userId, text, buttons.isEmpty() ? null : buttons);
    } catch (IOException e) {
      if (hasLink) {
        System.err.println("[WARN] Lead confirm failed with link button, retrying without link: " + e.getMessage());
        client.sendMessage(userId, text, List.of(List.of(Button.message("⬅️ В меню"))));
        return;
      }
      throw e;
    }
  }

  private void notifyOperator(Conversation c) throws IOException {
    long operatorId = Long.parseLong(config.operatorUserId);
    String topic = buildTopic(c);
    String details = buildDetails(c.data);
    String serviceLine = "[ЗАЯВКА]\n" +
        "📌 Тема: " + topic + "\n" +
        "📞 Телефон: " + c.phone + "\n" +
        "🕒 Время: " + c.timePref + "\n" +
        "🗂 Данные:\n" + details;
    client.sendMessage(operatorId, serviceLine);
  }

  private String buildTopic(Conversation c) {
    String base = c.topic == null ? "" : c.topic;
    Map<String, String> d = c.data;
    return switch (base) {
      case "Перепланировка" -> joinNonEmpty("Перепланировка",
          value(d, "replan_type"), value(d, "replan_city"));
      case "Кадастровые работы" -> joinNonEmpty("Кадастровые работы",
          value(d, "kad_type"));
      case "Прирезка" -> joinNonEmpty("Прирезка",
          value(d, "prirez_purpose"), value(d, "prirez_settlement"));
      case "Снижение налога/аренды" -> joinNonEmpty("Снижение налога/аренды",
          value(d, "tax_input"));
      case "Оформление/реконструкция" -> joinNonEmpty("Оформление/реконструкция",
          value(d, "build_type"), value(d, "build_settlement"));
      case "Земельный спор" -> joinNonEmpty("Земельный спор",
          value(d, "land_settlement"), value(d, "land_desc"));
      case "Строительный спор" -> joinNonEmpty("Строительный спор",
          value(d, "const_role"), value(d, "const_issue"));
      default -> base.isBlank() ? "Связаться с юристом" : base;
    };
  }

  private String buildDetails(Map<String, String> data) {
    List<String> parts = new ArrayList<>();
    if (data.containsKey("replan_type")) parts.add("• Помещение: " + data.get("replan_type"));
    if (data.containsKey("replan_city")) parts.add("• Город: " + data.get("replan_city"));
    if (data.containsKey("kad_type")) parts.add("• Кадастр: " + data.get("kad_type"));
    if (data.containsKey("prirez_purpose")) parts.add("• Назначение: " + data.get("prirez_purpose"));
    if (data.containsKey("prirez_settlement")) parts.add("• Нас. пункт: " + data.get("prirez_settlement"));
    if (data.containsKey("tax_input")) parts.add("• Кадастр/адрес: " + data.get("tax_input"));
    if (data.containsKey("build_type")) parts.add("• Тип: " + data.get("build_type"));
    if (data.containsKey("build_settlement")) parts.add("• Нас. пункт: " + data.get("build_settlement"));
    if (data.containsKey("land_settlement")) parts.add("• Нас. пункт: " + data.get("land_settlement"));
    if (data.containsKey("land_desc")) parts.add("• Ситуация: " + data.get("land_desc"));
    if (data.containsKey("const_role")) parts.add("• Роль: " + data.get("const_role"));
    if (data.containsKey("const_issue")) parts.add("• Проблема: " + data.get("const_issue"));
    if (parts.isEmpty()) {
      return "• —";
    }
    return String.join("\n", parts);
  }

  private List<List<Button>> withContactButton(List<List<Button>> rows) {
    List<List<Button>> result = new ArrayList<>(rows);
    result.add(List.of(Button.message("👨‍⚖️ " + MENU_CONTACT)));
    return result;
  }

  private void sendTextWithContact(long userId, String text) throws IOException {
    client.sendMessage(userId, text, withContactButton(List.of()));
  }

  private String extractPhone(String text) {
    String raw = text == null ? "" : text.trim();
    if (raw.isBlank()) return null;
    String digits = raw.replaceAll("[^0-9]", "");
    if (digits.length() < 10 || digits.length() > 15) {
      return null;
    }
    return raw;
  }

  private boolean isMenuCommand(String normalized) {
    return equalsAny(normalized, "/start", "меню", "главное меню", "в меню");
  }

  private boolean isMainMenuSelection(String normalized) {
    return equalsAny(normalized,
        MENU_TAX,
        MENU_REPLAN,
        MENU_KAD,
        MENU_PRIREZ,
        MENU_BUILD,
        MENU_LAND,
        MENU_CONST
    );
  }

  private boolean isContactShortcut(String normalized) {
    return equalsAny(normalized, "связаться с юристом");
  }

  private boolean equalsAny(String normalized, String... options) {
    for (String o : options) {
      if (normalize(o).equals(normalized)) return true;
    }
    return false;
  }

  private String normalize(String s) {
    String t = s == null ? "" : s.trim().toLowerCase();
    t = t.replace("✅", "");
    t = t.replaceAll("[\\p{So}\\uFE0F\\u200D]", "");
    t = t.replaceAll("\\s+", " ").trim();
    return t;
  }

  private String cleanLabel(String s) {
    String t = s == null ? "" : s.trim();
    t = t.replaceAll("[\\p{So}\\uFE0F\\u200D]", "");
    t = t.replaceAll("\\s+", " ").trim();
    return t;
  }

  private void markMenuSent(Conversation c) {
    c.data.put(DATA_LAST_MENU_AT, String.valueOf(System.currentTimeMillis()));
    store.upsertConversation(c);
  }

  private boolean recentlySentMenu(Conversation c) {
    String raw = c.data.get(DATA_LAST_MENU_AT);
    if (raw == null || raw.isBlank()) return false;
    try {
      long last = Long.parseLong(raw);
      return System.currentTimeMillis() - last < 5000;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  private String value(Map<String, String> data, String key) {
    String v = data.get(key);
    return v == null ? "" : v;
  }

  private String joinNonEmpty(String first, String... rest) {
    List<String> parts = new ArrayList<>();
    if (first != null && !first.isBlank()) parts.add(first);
    if (rest != null) {
      for (String r : rest) {
        if (r != null && !r.isBlank()) parts.add(r);
      }
    }
    return String.join(" – ", parts);
  }

  private String text(JsonNode node, String field) {
    return node == null ? "" : node.path(field).asText("");
  }
}
