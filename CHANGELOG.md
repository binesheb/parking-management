# Changelog

## 0.2.0 - Unreleased

### Added

- Android ANPR vertical slice with CameraX analysis, ML Kit OCR, candidate stabilization, durable local event storage, offline retry queue, configurable API synchronization, and a live vehicle-state dashboard.
- Parking capacity configuration and local occupancy calculation.
- SHA-256 validated Android update manifest and release publishing workflow.
- Unit coverage for occupancy and last-seen rules.

### Fixed

- Use an AppCompat-compatible theme for `MainActivity`.

## 0.1.1

### Added

- Guarded `scripts/update.sh` source updater that only follows `origin/main`, rejects local changes and diverged history, and validates the firmware after a successful fast-forward update.

### Fixed

- Keep the local dashboard responsive when `/api/state` temporarily fails instead of leaving the UI in an unhandled error state.

## 0.1.0

Initial firmware and repository foundation for the Parking Management vertical slice.
