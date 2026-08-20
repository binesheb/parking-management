# Firmware

PlatformIO firmware for ESP32-based Parking Management devices.

## 0.1.0 target

One firmware supports both roles. First boot opens a provisioning AP. Configuration is stored in Preferences/NVS. Master mode exposes the local dashboard and API. Client mode is reserved for discovery and parking input integration.

## Build

```bash
cd firmware
pio run
pio run -t upload
pio device monitor
```

The initial hardware profile is `esp32dev`. Button and display pin mappings will be added as hardware profiles rather than hard-coded into business logic.
