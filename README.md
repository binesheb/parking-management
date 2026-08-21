# Parking Management — Android ANPR

A camera-first vehicle movement tracker for compounds and parking areas. Each Android phone is assigned a role and watches a fixed lane. The app recognizes Indian-style registration plates using on-device ML Kit text recognition, records events locally, and synchronizes them to a central API when configured.

## Camera roles

- **Compound Entry** → `COMPOUND_IN`
- **Compound Exit** → `COMPOUND_OUT`
- **Parking Entry** → `PARKING_IN`
- **Parking Exit** → `PARKING_OUT`

Use four phones for the four lanes, or add more phones for additional lanes. Each phone can be independently assigned a role.

## Current release

Version **0.2.0** is the first Android ANPR vertical slice. It includes camera analysis, plate candidate stabilization, durable local event persistence, an offline retry queue, configurable API sync, vehicle-state API, a live web dashboard, CI and a SHA-256 validated update mechanism.

## Important ANPR limitation

This release uses general text recognition rather than a dedicated license-plate detector. Camera placement, lighting, plate size, motion blur and regional plate formats materially affect recognition. A dedicated Indian license-plate detection/OCR model should be added before unattended barrier control or enforcement use.

## Build

```bash
gradle :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions builds the debug APK and runs unit tests. Tagged releases publish the APK as a GitHub Release.

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
6. Keep the phone powered and connected to the local network for centralized tracking.

Events are written locally before network synchronization. If the server is unavailable, they remain in the local queue and are retried every 30 seconds.

## Architecture

`Android CameraX → ML Kit OCR → PlateParser → local SQLite/outbox → HTTP sync → Fastify API/SQLite → dashboard`

See `docs/PRD-ANPR.md` and `docs/ARCHITECTURE-ANPR.md`.

## Update mechanism

The app checks a small update manifest hosted from the repository's `main` branch. A newer release is downloaded only after the manifest identifies it; the APK SHA-256 must match before Android's package installer is invoked. A failed checksum prevents installation. Tagged GitHub releases are responsible for publishing the APK and verified manifest.

## Security

- Recognition is on-device; camera frames are not uploaded by the Android OCR pipeline.
- API input is validated and event IDs make synchronization idempotent.
- Production deployments should use HTTPS and device authentication.
- The update manifest and APK are integrity checked before installation.

## Roadmap

Dedicated plate detection/OCR, confidence scoring, device authentication, role registration, richer dashboard, audit trail, retention policies, multi-site support, barrier/relay integration and automated instrumentation tests are next.
