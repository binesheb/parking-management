# Changelog

## 0.1.1 - Unreleased

### Added

- Guarded `scripts/update.sh` source updater that only follows `origin/main`, rejects local changes and diverged history, and validates the firmware after a successful fast-forward update.

### Fixed

- Keep the local dashboard responsive when `/api/state` temporarily fails instead of leaving the UI in an unhandled error state.

## 0.1.0

Initial firmware and repository foundation for the Parking Management vertical slice.
