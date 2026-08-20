# Simple Parking Management v1

The first deployable version is intentionally small.

## Components

1. Parking Server
2. ESP32 four-button controller
3. Web dashboard
4. ESP32-S3 LED display

## Data flow

```
Controller --HTTP--> Server <--HTTP/WebSocket--> Dashboard
                         |
                         +--HTTP polling/live update--> LED Display
```

## Four actions

- CAR_IN: increase occupied by 1 when capacity allows
- CAR_OUT: decrease occupied by 1 when occupied is above 0
- SLOT_PLUS: increase configured capacity by 1
- SLOT_MINUS: decrease configured capacity by 1, never below occupied

The server is authoritative and persists current state plus an event log.

## Minimal API

- `GET /api/status`
- `POST /api/event`
- `GET /api/events`

Example event:

```json
{"type":"CAR_IN","source":"controller-01"}
```

## Initial deployment

One site, Palarivattom. One total parking capacity. Additional areas and remote locations will be introduced later.
