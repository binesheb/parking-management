# ANPR architecture

## Data flow

Each phone is an independent edge device. CameraX supplies frames to ML Kit text recognition. `PlateParser` normalizes candidate text and accepts only a constrained Indian-style registration pattern. Three consecutive matching observations are required before an event is emitted, followed by a four-second cooldown for the same camera.

Events are written to SQLite first. If a server URL is configured, the event is POSTed to `/api/v1/events`. The central service persists the event and derives state from the ordered event stream.

## State model

`COMPOUND_IN` sets compound presence true; `COMPOUND_OUT` sets it false. `PARKING_IN` sets parking presence true; `PARKING_OUT` sets it false. A vehicle can therefore be inside the compound without being in parking, or inside both.

## Failure modes

- Camera unavailable: app shows permission/error state.
- OCR uncertain: no event is emitted until a candidate is stable.
- Network unavailable: local SQLite still records the event.
- Invalid API event: server returns 400 and does not persist it.
- Update checksum mismatch: APK is deleted and installation is not attempted.

## Production hardening

The current HTTP sync is intentionally small. Before public deployment, use HTTPS, device authentication, replay-safe event IDs, server-side clock policy, a durable outbound queue, and role/device registration. For high-accuracy ANPR, replace the generic OCR candidate stage with a dedicated license-plate detector plus OCR ensemble.
