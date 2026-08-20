#include <Arduino.h>
#include <WiFi.h>
#include <Preferences.h>
#include <ESPAsyncWebServer.h>
#include <ArduinoJson.h>

Preferences prefs;
AsyncWebServer server(80);

struct ParkingState { int capacity = 0; int occupied = 0; } state;
String role;
String showroom;
String deviceName;

int available() { return max(0, state.capacity - state.occupied); }

void saveState() {
  prefs.putInt("capacity", state.capacity);
  prefs.putInt("occupied", state.occupied);
}

void sendState(AsyncWebServerRequest *request) {
  JsonDocument doc;
  doc["role"] = role;
  doc["showroom"] = showroom;
  doc["device_name"] = deviceName;
  doc["capacity"] = state.capacity;
  doc["occupied"] = state.occupied;
  doc["available"] = available();
  doc["version"] = PARKING_VERSION;
  String out; serializeJson(doc, out);
  request->send(200, "application/json", out);
}

bool applyAction(const String &action) {
  if (action == "car_in" && state.occupied < state.capacity) state.occupied++;
  else if (action == "car_out" && state.occupied > 0) state.occupied--;
  else if (action == "slot_plus") state.capacity++;
  else if (action == "slot_minus" && state.capacity > state.occupied) state.capacity--;
  else return false;
  saveState();
  return true;
}

void startPortal() {
  WiFi.mode(WIFI_AP);
  String ssid = "PARKING-SETUP-" + String((uint32_t)ESP.getEfuseMac(), HEX).substring(0, 4);
  WiFi.softAP(ssid.c_str(), "parking123");
  server.on("/", HTTP_GET, [](AsyncWebServerRequest *r) {
    r->send(200, "text/html", "<h1>Parking Management Setup</h1><form method='post' action='/configure'>Role <select name='role'><option>master</option><option>client</option></select><br>Showroom <input name='showroom'><br>Device <input name='device'><br>WiFi SSID <input name='ssid'><br>Password <input type='password' name='password'><br><button>Save</button></form>");
  });
  server.on("/configure", HTTP_POST, [](AsyncWebServerRequest *r) {
    prefs.putString("role", r->arg("role")); prefs.putString("showroom", r->arg("showroom"));
    prefs.putString("device", r->arg("device")); prefs.putString("ssid", r->arg("ssid")); prefs.putString("password", r->arg("password"));
    r->send(200, "text/html", "Saved. Restarting..."); delay(500); ESP.restart();
  });
  server.begin();
}

void startMaster() {
  server.on("/api/state", HTTP_GET, sendState);
  server.on("/api/action", HTTP_POST, [](AsyncWebServerRequest *r) {
    if (!r->hasParam("action", true)) { r->send(400, "text/plain", "missing action"); return; }
    bool ok = applyAction(r->getParam("action", true)->value());
    r->send(ok ? 200 : 409, "application/json", ok ? "{\"ok\":true}" : "{\"ok\":false}");
  });
  server.on("/", HTTP_GET, [](AsyncWebServerRequest *r) {
    r->send(200, "text/html", "<h1>Parking Management</h1><h2 id='n'>Loading...</h2><p id='e'></p><script>async function x(){try{let r=await fetch('/api/state');if(!r.ok)throw Error();let s=await r.json();document.getElementById('n').innerText=s.available+' slots available';document.getElementById('e').innerText='';}catch(e){document.getElementById('e').innerText='Waiting for connection…';}}x();setInterval(x,2000)</script>");
  });
  server.begin();
}

void setup() {
  Serial.begin(115200);
  prefs.begin("parking", false);
  role = prefs.getString("role", "");
  if (role.isEmpty()) { startPortal(); return; }
  showroom = prefs.getString("showroom", ""); deviceName = prefs.getString("device", "ESP32");
  state.capacity = prefs.getInt("capacity", 0); state.occupied = prefs.getInt("occupied", 0);
  WiFi.mode(WIFI_STA);
  WiFi.begin(prefs.getString("ssid", "").c_str(), prefs.getString("password", "").c_str());
  unsigned long deadline = millis() + 15000;
  while (WiFi.status() != WL_CONNECTED && millis() < deadline) delay(250);
  if (WiFi.status() != WL_CONNECTED) { startPortal(); return; }
  if (role == "master") startMaster();
}

void loop() { }
