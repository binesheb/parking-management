# Architecture

## Release 0.1.0

```
ESP32 Client(s)
      |
      | Wi-Fi (discovery/registration protocol)
      v
ESP32 Master ---- local HTTP dashboard/API
      |
      +---- persistent parking state
```

### Master

The Master is the authoritative state owner. It stores capacity and occupied count, validates actions, and exposes the local dashboard.

### Client

Clients represent physical parking controllers. The 0.1.0 repository foundation keeps the role in the common configuration model; discovery, approval, event queue, and physical button drivers are the next implementation commits in the same release stream.

### Parking invariants

- `0 <= occupied <= capacity`
- `available = capacity - occupied`
- `CAR IN` cannot exceed capacity.
- `CAR OUT` cannot reduce occupied below zero.
- `SLOT -` cannot reduce capacity below occupied.

### Persistence

Configuration and current parking state use ESP32 Preferences/NVS in the initial release.
