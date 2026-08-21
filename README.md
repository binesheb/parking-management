# Parking Management — Android ANPR

A camera-first vehicle movement tracker for compounds and parking areas. Each Android phone is assigned a role and watches a fixed lane. The app recognizes Indian-style registration plates using on-device ML Kit text recognition, records events locally, and optionally syncs them to the central API.

## Camera roles

- **Compound Entry** → `COMPOUND_IN`
- **Compound Exit** → `COMPOUND_OUT`
- **Parking Entry** → `PARKING_IN`
- **Parking Exit** → `PARKING_OUT`

A deployment can therefore use four phones: compound entrance, compound exit, parking entrance and parking exit. More lanes can be added by assigning additional phones the same role.

## Current release

Version **0.2.0** is the first Android ANPR vertical slice. It includes camera analysis, plate candidate stabilization, local event persistence, configurable API sync, vehicle-state API, a live web dashboard, CI, and signed/integrity-checked update preparation.

## Important ANPR limitation

This release uses general text recognition rather than a dedicated license-plate detector. Camera placement, lighting, plate size, motion blur and regional plate formats materially affect recognition. A dedicated Indian license-plate detection/OCR model should be added before unattended barrier control or enforcement use.

## Build

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

The GitHub Actions workflow builds and publishes the debug APK artifact. Tagged releases publish the APK as a GitHub Release.

## Server

```bash
cd server
npm install
npm test
npm start
```

Or:

```bash
docker compose up --build
```

The API stores events in SQLite. Set `DB_PATH` for another database location and `PORT` for the HTTP port.

## Android setup

1. Install the APK on a phone dedicated to one lane.
2. Grant camera permission.
3. Select the camera role.
4. Open **Server** and enter the central API URL, for example `http://192.168.1.10:8080`.
5. Mount the phone so the registration plate fills a predictable portion of the camera frame.
6. Keep a stable network path to the server if centralized tracking is required.

The app remains useful offline: recognized events are written locally even when the server is unavailable. Offline queue replay is planned for the next iteration.

## Architecture

`Android CameraX → ML Kit OCR → PlateParser → local SQLite → optional HTTP sync → Fastify API/SQLite → dashboard`

See `docs/PRD.md` and `docs/ARCHITECTURE-ANPR.md`.

## Security

- No cloud image upload is performed by the Android recognition pipeline.
- APK updates are downloaded only after a GitHub `main` manifest check and SHA-256 validation.
- The server validates event type, timestamp and plate shape.
- Production deployments should put the API behind TLS and authentication before use on an untrusted network.

## Roadmap

Dedicated plate detection, confidence scoring, offline sync queue, authentication, role/device registration, richer dashboard, audit trail, retention policies, multi-site support, barrier/relay integration and automated instrumentation testing are next.
