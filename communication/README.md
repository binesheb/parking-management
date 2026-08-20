# Communication Layer

## Architecture

MQTT is the device message bus. The Parking Core is authoritative for state. The web dashboard consumes Core APIs and receives live updates through WebSocket.

```
ESP32 Controllers ─┐
ESP32 Displays ────┼── MQTT Broker ── Parking Core ── REST/WebSocket ── Dashboard
Gate Nodes ────────┘
```

Devices publish immutable events. They do not modify shared parking counts directly.

The Core validates each event, applies it once, persists it, increments the state revision and publishes the resulting status.

## Reliability

- QoS 1 for state-changing events
- unique event_id for idempotency
- device outbox for retry while offline
- retained status messages for fast device recovery
- periodic device heartbeat
- Last Will and Testament for offline detection
- monotonically increasing state revision

## Security baseline

The production deployment must use authenticated MQTT credentials and isolated site topics. TLS is recommended when traffic leaves the trusted local network.
