# SmartPark XIAO ESP32-S3 Sense sensor

Dedicated PlatformIO firmware for one overhead camera per parking slot.

## Build

Install PlatformIO Core, then:

```bash
cd firmware/xiao-sense
pio run
pio run -t upload
pio device monitor
```

Target: `seeed_xiao_esp32s3`, Arduino framework, PSRAM enabled. Firmware identifies the attached camera PID at boot; supported board revisions may use OV2640, OV3660 or OV5640.

## First boot and provisioning

1. Power the sensor from a regulated 5 V supply.
2. Join the setup AP shown on serial (SSID `SP-SENSOR-xxxx`; password is `SPV1` plus the four-character setup suffix printed on serial).
3. Open `http://192.168.4.1`.
4. Enter 2.4 GHz Wi-Fi, server base URL, device ID, area ID, slot ID and the server's `SENSOR_BOOTSTRAP_TOKEN`.
5. The node registers once and stores its returned device bearer token in NVS.

Do not commit Wi-Fi credentials or bootstrap tokens.

## Commissioning

With the bay empty and the camera aimed:
- Open the device IP and POST to `/api/calibrate` with header `X-Setup-Key: <setup key printed on serial>`.
- Calibration stores a compact empty-bay grayscale signature.
- Check `/api/status` for camera, detector and occupancy state.
- Repeat calibration after moving the camera or changing the lens/window.

## Detection caveat

The V1 detector uses reference-image difference with global brightness compensation and temporal filtering. It is a field-test baseline, not a trained AI vehicle classifier. It can react to shadows or objects and must be evaluated with site images before production. The server protocol is independent of the detector, so a quantized EMPTY/OCCUPIED model can replace the heuristic later.

## API behavior

- Local: `GET /api/status`, `POST /api/calibrate`.
- Server: authenticated registration, occupancy events and heartbeat under `/api/v1/sensors/*`.
- Images are not sent to the server. Only status and health metadata are sent.
- A bounded NVS outbox retries events after Wi-Fi recovery.
