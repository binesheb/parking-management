# Parking Management

A local-first, real-time parking management system for retail locations using ESP32 controllers.

## Release 0.1.0

The first release is a complete vertical slice:

- Master and Client device roles
- First-boot provisioning portal
- Persistent configuration and parking state
- Wi-Fi master discovery and client registration
- Admin approval of newly discovered clients
- `CAR IN`, `CAR OUT`, `SLOT +`, and `SLOT -` actions
- Event IDs and duplicate-event protection
- Device heartbeats and online/offline status
- Live local web dashboard
- Offline-ready event model

LoRa, remote parking deployment, production OTA, advanced displays, analytics, ANPR, and barrier integration follow in later releases.

## Quick architecture

`Parking Client -> Wi-Fi -> Parking Master -> Live Dashboard`

The product is designed so the same ESP32 core concepts can later support LoRa transport without changing the parking domain model.

## Development

Firmware uses PlatformIO and targets ESP32 devices. See [`firmware/README.md`](firmware/README.md).

## Documentation

- [`Product Requirements`](docs/PRD.md)
- [`Architecture`](docs/ARCHITECTURE.md)
- [`Device Protocol`](docs/DEVICE-PROTOCOL.md)
- [`Hardware`](docs/HARDWARE.md)
- [`Development Plan`](docs/DEVELOPMENT.md)

## Status

**0.1.0 — Initial vertical slice under development.**
