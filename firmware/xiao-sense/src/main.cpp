#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include <HTTPClient.h>
#include <Preferences.h>
#include <ArduinoJson.h>
#include <esp_camera.h>
#include <time.h>
#include <vector>
#include <cmath>

static constexpr char VERSION[] = "0.1.0";
static constexpr uint16_t GRID_W = 32;
static constexpr uint16_t GRID_H = 24;
static constexpr size_t GRID_SIZE = GRID_W * GRID_H;
static constexpr uint32_t SAMPLE_MS = 1000;
static constexpr uint32_t HEARTBEAT_MS = 30000;
static constexpr uint32_t RETRY_MS = 5000;
static constexpr float OCCUPIED_THRESHOLD = 0.18f;

Preferences prefs;
WebServer localServer(80);

struct Settings {
  String wifiSsid, wifiPassword, serverUrl, deviceId, areaId, slotId, bootstrapToken, deviceToken;
} cfg;

struct QueuedEvent {
  String eventId;
  uint32_t sequence;
  String occupancy;
  float confidence;
  String capturedAt;
};
std::vector<QueuedEvent> outbox;

uint8_t baseline[GRID_SIZE];
bool baselineReady = false;
String occupancy = "UNKNOWN";
String cameraStatus = "NOT_INITIALIZED";
String detectorStatus = "CALIBRATION_REQUIRED";
uint32_t sequenceNumber = 0;
uint32_t lastSample = 0, lastHeartbeat = 0, lastRetry = 0;
uint8_t occupiedStreak = 0, clearStreak = 0;
float lastScore = 0.0f;
String setupKey;

String trimSlash(String value) {
  while (value.endsWith("/")) value.remove(value.length() - 1);
  return value;
}

String isoTimestamp() {
  const time_t now = time(nullptr);
  if (now < 1700000000) return "";
  struct tm utc;
  gmtime_r(&now, &utc);
  char value[25];
  strftime(value, sizeof(value), "%Y-%m-%dT%H:%M:%SZ", &utc);
  return String(value);
}

String eventIdFor(uint32_t seq) {
  char suffix[12];
  snprintf(suffix, sizeof(suffix), "-%08lu", static_cast<unsigned long>(seq));
  return cfg.deviceId + suffix;
}

void persistOutbox() {
  JsonDocument doc;
  JsonArray arr = doc.to<JsonArray>();
  for (const auto &event : outbox) {
    JsonObject item = arr.add<JsonObject>();
    item["eventId"] = event.eventId;
    item["sequence"] = event.sequence;
    item["occupancy"] = event.occupancy;
    item["confidence"] = event.confidence;
    item["capturedAt"] = event.capturedAt;
  }
  String json;
  serializeJson(doc, json);
  prefs.putString("outbox", json);
}

void loadOutbox() {
  outbox.clear();
  JsonDocument doc;
  const String raw = prefs.getString("outbox", "[]");
  if (deserializeJson(doc, raw)) return;
  for (JsonObject item : doc.as<JsonArray>()) {
    QueuedEvent event;
    event.eventId = item["eventId"] | "";
    event.sequence = item["sequence"] | 0;
    event.occupancy = item["occupancy"] | "UNKNOWN";
    event.confidence = item["confidence"] | 0.0f;
    event.capturedAt = item["capturedAt"] | "";
    if (event.eventId.length()) outbox.push_back(event);
  }
}

void enqueueEvent(const String &next, float confidence) {
  if (outbox.size() >= 32) {
    // Preserve the newest state transitions; oldest pending item is discarded only at hard capacity.
    outbox.erase(outbox.begin());
  }
  QueuedEvent event;
  event.sequence = ++sequenceNumber;
  event.eventId = eventIdFor(event.sequence);
  event.occupancy = next;
  event.confidence = constrain(confidence, 0.0f, 1.0f);
  event.capturedAt = isoTimestamp();
  // If NTP is not ready yet, keep the event with an empty timestamp.
  // flushOutbox fills it once the clock is synchronized.
  outbox.push_back(event);
  prefs.putUInt("sequence", sequenceNumber);
  persistOutbox();
}

bool postJson(const String &path, const String &payload, const String &token, JsonDocument *response = nullptr) {
  if (WiFi.status() != WL_CONNECTED) return false;
  HTTPClient http;
  const String url = cfg.serverUrl + path;
  if (!http.begin(url)) return false;
  http.setTimeout(4000);
  http.addHeader("Content-Type", "application/json");
  if (token.length()) http.addHeader("Authorization", "Bearer " + token);
  const int code = http.POST(payload);
  String body = code > 0 ? http.getString() : "";
  http.end();
  if (response && body.length()) deserializeJson(*response, body);
  return code == 200 || code == 201;
}

bool registerDevice() {
  if (cfg.deviceToken.length()) return true;
  if (!cfg.bootstrapToken.length()) return false;
  JsonDocument body;
  body["deviceId"] = cfg.deviceId;
  body["areaId"] = cfg.areaId;
  body["slotId"] = cfg.slotId;
  body["firmwareVersion"] = VERSION;
  String payload;
  serializeJson(body, payload);
  JsonDocument response;
  if (!postJson("/api/v1/sensors/register", payload, cfg.bootstrapToken, &response)) {
    Serial.println("Registration failed; check bootstrap token and server URL");
    return false;
  }
  cfg.deviceToken = response["deviceToken"] | "";
  if (!cfg.deviceToken.length()) return false;
  prefs.putString("deviceToken", cfg.deviceToken);
  prefs.remove("bootstrapToken");
  cfg.bootstrapToken = "";
  Serial.println("Device registered; per-device token stored in NVS");
  return true;
}

bool sendEvent(const QueuedEvent &event) {
  JsonDocument body;
  body["eventId"] = event.eventId;
  body["deviceId"] = cfg.deviceId;
  body["sequence"] = event.sequence;
  body["occupancy"] = event.occupancy;
  body["confidence"] = event.confidence;
  body["capturedAt"] = event.capturedAt;
  body["firmwareVersion"] = VERSION;
  String payload;
  serializeJson(body, payload);
  return postJson("/api/v1/sensors/events", payload, cfg.deviceToken);
}

void flushOutbox() {
  if (outbox.empty() || WiFi.status() != WL_CONNECTED || !cfg.deviceToken.length()) return;
  while (!outbox.empty()) {
    if (!outbox.front().capturedAt.length()) {
      outbox.front().capturedAt = isoTimestamp();
      if (!outbox.front().capturedAt.length()) break;
      persistOutbox();
    }
    if (!sendEvent(outbox.front())) break;
    outbox.erase(outbox.begin());
    persistOutbox();
  }
}

void sendHeartbeat() {
  if (WiFi.status() != WL_CONNECTED || !cfg.deviceToken.length()) return;
  JsonDocument body;
  body["uptimeSeconds"] = millis() / 1000;
  body["wifiRssi"] = WiFi.RSSI();
  body["cameraStatus"] = cameraStatus;
  body["detectorStatus"] = detectorStatus;
  body["firmwareVersion"] = VERSION;
  String payload;
  serializeJson(body, payload);
  postJson("/api/v1/sensors/heartbeat", payload, cfg.deviceToken);
}

bool initCamera() {
  camera_config_t c = {};
  c.ledc_channel = LEDC_CHANNEL_0;
  c.ledc_timer = LEDC_TIMER_0;
  c.pin_d0 = 15; c.pin_d1 = 17; c.pin_d2 = 18; c.pin_d3 = 16;
  c.pin_d4 = 14; c.pin_d5 = 12; c.pin_d6 = 11; c.pin_d7 = 48;
  c.pin_xclk = 10;
  c.pin_pclk = 13;
  c.pin_vsync = 38;
  c.pin_href = 47;
  c.pin_sccb_sda = 40;
  c.pin_sccb_scl = 39;
  c.pin_pwdn = -1;
  c.pin_reset = -1;
  c.xclk_freq_hz = 20000000;
  c.pixel_format = PIXFORMAT_GRAYSCALE;
  c.frame_size = FRAMESIZE_QVGA;
  c.jpeg_quality = 12;
  c.fb_count = 1;
  c.fb_location = CAMERA_FB_IN_PSRAM;
  c.grab_mode = CAMERA_GRAB_LATEST;
  const esp_err_t result = esp_camera_init(&c);
  if (result != ESP_OK) {
    Serial.printf("Camera init failed: 0x%x\n", result);
    cameraStatus = "ERROR";
    return false;
  }
  sensor_t *sensor = esp_camera_sensor_get();
  if (sensor) Serial.printf("Camera sensor PID: 0x%04x\n", sensor->id.PID);
  cameraStatus = "OK";
  return true;
}

bool captureSignature(uint8_t *target) {
  camera_fb_t *frame = esp_camera_fb_get();
  if (!frame) return false;
  if (frame->format != PIXFORMAT_GRAYSCALE || frame->width < 64 || frame->height < 64 ||
      frame->len < static_cast<size_t>(frame->width) * frame->height) {
    esp_camera_fb_return(frame);
    return false;
  }
  const int x0 = frame->width / 10;
  const int y0 = frame->height / 10;
  const int roiW = frame->width * 8 / 10;
  const int roiH = frame->height * 8 / 10;
  for (int gy = 0; gy < GRID_H; gy++) {
    const int y = y0 + (gy * (roiH - 1)) / (GRID_H - 1);
    for (int gx = 0; gx < GRID_W; gx++) {
      const int x = x0 + (gx * (roiW - 1)) / (GRID_W - 1);
      target[gy * GRID_W + gx] = frame->buf[y * frame->width + x];
    }
  }
  esp_camera_fb_return(frame);
  return true;
}

float differenceScore(const uint8_t *current) {
  int32_t totalOffset = 0;
  for (size_t i = 0; i < GRID_SIZE; i++) totalOffset += static_cast<int32_t>(current[i]) - baseline[i];
  const int32_t offset = totalOffset / static_cast<int32_t>(GRID_SIZE);
  size_t changed = 0;
  for (size_t i = 0; i < GRID_SIZE; i++) {
    const int32_t corrected = static_cast<int32_t>(current[i]) - baseline[i] - offset;
    if (abs(corrected) > 25) changed++;
  }
  return static_cast<float>(changed) / static_cast<float>(GRID_SIZE);
}

void persistState(const String &next, float confidence) {
  if (next == occupancy) return;
  occupancy = next;
  prefs.putString("occupancy", occupancy);
  enqueueEvent(occupancy, confidence);
  Serial.printf("Occupancy changed: %s (score %.3f)\n", occupancy.c_str(), confidence);
}

void sampleOccupancy() {
  uint8_t current[GRID_SIZE];
  if (!captureSignature(current)) {
    cameraStatus = "ERROR";
    detectorStatus = "CAMERA_CAPTURE_FAILED";
    persistState("UNKNOWN", 0.0f);
    return;
  }
  cameraStatus = "OK";
  if (!baselineReady) {
    detectorStatus = "CALIBRATION_REQUIRED";
    persistState("UNKNOWN", 0.0f);
    return;
  }
  detectorStatus = "READY";
  lastScore = differenceScore(current);
  const bool candidateOccupied = lastScore >= OCCUPIED_THRESHOLD;
  if (candidateOccupied) {
    if (occupiedStreak < 10) occupiedStreak++;
    clearStreak = 0;
    if (occupiedStreak >= 3) persistState("OCCUPIED", lastScore);
  } else {
    if (clearStreak < 10) clearStreak++;
    occupiedStreak = 0;
    if (clearStreak >= 5) persistState("AVAILABLE", 1.0f - lastScore);
  }
}

void saveConfigFromRequest() {
  cfg.wifiSsid = localServer.arg("ssid");
  cfg.wifiPassword = localServer.arg("password");
  cfg.serverUrl = trimSlash(localServer.arg("server"));
  cfg.deviceId = localServer.arg("device");
  cfg.areaId = localServer.arg("area");
  cfg.slotId = localServer.arg("slot");
  cfg.bootstrapToken = localServer.arg("bootstrap");
  prefs.putString("ssid", cfg.wifiSsid);
  prefs.putString("password", cfg.wifiPassword);
  prefs.putString("server", cfg.serverUrl);
  prefs.putString("device", cfg.deviceId);
  prefs.putString("area", cfg.areaId);
  prefs.putString("slot", cfg.slotId);
  prefs.putString("bootstrapToken", cfg.bootstrapToken);
  localServer.send(200, "text/html", "<h2>Saved. Restarting sensor...</h2>");
  delay(500);
  ESP.restart();
}

void startProvisioning() {
  WiFi.mode(WIFI_AP);
  String suffix = String(static_cast<uint32_t>(ESP.getEfuseMac()), HEX);
  while (suffix.length() < 4) suffix = "0" + suffix;
  suffix = suffix.substring(suffix.length() - 4);
  const String ssid = "SP-SENSOR-" + suffix;
  const String password = "SPV1" + suffix;
  setupKey = suffix;
  WiFi.softAP(ssid.c_str(), password.c_str());
  Serial.printf("Provisioning AP: %s  password: %s  setup key: %s\n", ssid.c_str(), password.c_str(), setupKey.c_str());
  localServer.on("/", HTTP_GET, []() {
    String html = "<!doctype html><meta name='viewport' content='width=device-width'><h2>SmartPark Sensor Setup</h2><form method='post' action='/save'>";
    html += "Wi-Fi SSID <input name='ssid' required><br>Wi-Fi password <input name='password' type='password'><br>";
    html += "Server URL <input name='server' value='http://192.168.1.10:8080' required><br>";
    html += "Device ID <input name='device' placeholder='SP-KOCHI-P01-S001' required><br>";
    html += "Area ID <input name='area' placeholder='KOCHI-P01' required><br>Slot ID <input name='slot' placeholder='S001' required><br>";
    html += "Bootstrap token <input name='bootstrap' type='password' required><br><button>Save and restart</button></form>";
    localServer.send(200, "text/html", html);
  });
  localServer.on("/save", HTTP_POST, saveConfigFromRequest);
  localServer.begin();
}

void startLocalApi() {
  static const char *headerKeys[] = {"X-Setup-Key"};
  localServer.collectHeaders(headerKeys, 1);
  localServer.on("/api/status", HTTP_GET, []() {
    JsonDocument doc;
    doc["deviceId"] = cfg.deviceId;
    doc["slotId"] = cfg.slotId;
    doc["firmwareVersion"] = VERSION;
    doc["occupancy"] = occupancy;
    doc["confidence"] = lastScore;
    doc["cameraStatus"] = cameraStatus;
    doc["detectorStatus"] = detectorStatus;
    doc["baselineReady"] = baselineReady;
    doc["wifiRssi"] = WiFi.status() == WL_CONNECTED ? WiFi.RSSI() : 0;
    doc["queuedEvents"] = outbox.size();
    String body; serializeJson(doc, body);
    localServer.send(200, "application/json", body);
  });
  localServer.on("/api/calibrate", HTTP_POST, []() {
    if (localServer.header("X-Setup-Key") != setupKey) {
      localServer.send(401, "application/json", "{\"error\":\"unauthorized\"}");
      return;
    }
    uint8_t fresh[GRID_SIZE];
    if (!captureSignature(fresh)) {
      localServer.send(503, "application/json", "{\"error\":\"camera_capture_failed\"}");
      return;
    }
    memcpy(baseline, fresh, GRID_SIZE);
    prefs.putBytes("baseline", baseline, GRID_SIZE);
    baselineReady = true;
    occupiedStreak = clearStreak = 0;
    detectorStatus = "READY";
    persistState("AVAILABLE", 1.0f);
    localServer.send(200, "application/json", "{\"ok\":true,\"message\":\"Empty-bay reference saved\"}");
  });
  localServer.begin();
}

bool connectWifi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(cfg.wifiSsid.c_str(), cfg.wifiPassword.c_str());
  const uint32_t deadline = millis() + 20000;
  while (WiFi.status() != WL_CONNECTED && static_cast<int32_t>(deadline - millis()) > 0) delay(250);
  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("Wi-Fi connected: %s RSSI=%d\n", WiFi.localIP().toString().c_str(), WiFi.RSSI());
    configTime(0, 0, "pool.ntp.org", "time.google.com");
    return true;
  }
  return false;
}

void setup() {
  Serial.begin(115200);
  delay(300);
  prefs.begin("spv1", false);
  String macSuffix = String(static_cast<uint32_t>(ESP.getEfuseMac()), HEX);
  while (macSuffix.length() < 4) macSuffix = "0" + macSuffix;
  setupKey = macSuffix.substring(macSuffix.length() - 4);
  cfg.wifiSsid = prefs.getString("ssid", "");
  cfg.wifiPassword = prefs.getString("password", "");
  cfg.serverUrl = trimSlash(prefs.getString("server", ""));
  cfg.deviceId = prefs.getString("device", "");
  cfg.areaId = prefs.getString("area", "");
  cfg.slotId = prefs.getString("slot", "");
  cfg.bootstrapToken = prefs.getString("bootstrapToken", "");
  cfg.deviceToken = prefs.getString("deviceToken", "");
  sequenceNumber = prefs.getUInt("sequence", 0);
  occupancy = prefs.getString("occupancy", "UNKNOWN");
  baselineReady = prefs.getBytesLength("baseline") == GRID_SIZE;
  if (baselineReady) prefs.getBytes("baseline", baseline, GRID_SIZE);
  loadOutbox();

  initCamera();
  if (!cfg.wifiSsid.length() || !cfg.serverUrl.length() || !cfg.deviceId.length()) {
    startProvisioning();
    return;
  }
  if (!connectWifi()) {
    Serial.println("Wi-Fi connection failed; starting provisioning AP");
    startProvisioning();
    return;
  }
  startLocalApi();
  if (!registerDevice()) Serial.println("Device registration pending; will retry after network/configuration recovery");
  detectorStatus = baselineReady ? "READY" : "CALIBRATION_REQUIRED";
  Serial.printf("SmartPark %s device=%s slot=%s\n", VERSION, cfg.deviceId.c_str(), cfg.slotId.c_str());
}

void loop() {
  localServer.handleClient();
  if (WiFi.status() != WL_CONNECTED && cfg.wifiSsid.length()) {
    WiFi.reconnect();
    delay(20);
  }
  const uint32_t nowMs = millis();
  if (WiFi.status() == WL_CONNECTED && !cfg.deviceToken.length() && cfg.bootstrapToken.length()) registerDevice();
  if (nowMs - lastSample >= SAMPLE_MS && cameraStatus != "NOT_INITIALIZED") {
    lastSample = nowMs;
    sampleOccupancy();
  }
  if (nowMs - lastRetry >= RETRY_MS) {
    lastRetry = nowMs;
    flushOutbox();
  }
  if (nowMs - lastHeartbeat >= HEARTBEAT_MS) {
    lastHeartbeat = nowMs;
    sendHeartbeat();
  }
  delay(5);
}
