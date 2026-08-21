# PRD — Vehicle Movement & Parking ANPR

## Goal
Know which vehicles entered the compound, which left, which are currently in the parking area, and the complete event history.

## Core workflow
1. Entry phone recognizes a registration number and records `COMPOUND_IN`.
2. Exit phone recognizes it and records `COMPOUND_OUT`.
3. Parking-entry phone records `PARKING_IN`.
4. Parking-exit phone records `PARKING_OUT`.
5. The central service derives current compound and parking state from the event stream.
6. Operators can inspect the event log and vehicle history.

## Functional requirements
- Fixed-camera Android operation.
- Four configurable camera roles.
- Indian-style registration plate normalization.
- Duplicate suppression through repeated recognition stability and time cooldown.
- Offline local event storage.
- Configurable central API synchronization.
- Vehicle history endpoint.
- Live dashboard with counts and event log.
- Versioned Android releases and update manifest.

## Non-functional requirements
- Recognition must be on-device by default.
- Events must be durable locally before network synchronization.
- API input must be validated.
- Builds must run automatically on GitHub Actions.
- Dependencies must remain maintained and pinned.
- Main is the authoritative development branch.

## Acceptance criteria for 0.2.0
- App launches and requests camera permission.
- All four roles can be selected and persist across restart.
- CameraX analysis produces OCR results without blocking the UI.
- A stable plate candidate generates one event after three matching observations.
- Local SQLite stores the event.
- Configured API receives the event.
- API exposes health, event list and vehicle state.
- Dashboard displays event counts and recent events.
- Unit tests cover plate normalization and event mapping.
- CI builds the APK and runs tests.

## Future acceptance gates
A production unattended deployment additionally requires dedicated plate detection/OCR, confidence thresholds, offline replay, authenticated TLS API, device identity, audit logging, data retention controls, instrumentation tests on target phones, and a field accuracy test set from the actual gates.
