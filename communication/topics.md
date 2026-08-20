# MQTT Topic Contract

All topics begin with `parking/v1/{site_id}`.

## Device discovery

`parking/v1/{site_id}/devices/{device_id}/presence`

Retained device metadata and online state.

## Heartbeat

`parking/v1/{site_id}/devices/{device_id}/heartbeat`

Periodic health payload including firmware version and uptime.

## Events

`parking/v1/{site_id}/events`

Payload:

```json
{
  "event_id": "uuid",
  "device_id": "controller-01",
  "area_id": "main",
  "type": "CAR_IN",
  "timestamp": "ISO-8601"
}
```

Publish with QoS 1. The Core deduplicates by `event_id`.

## Authoritative status

`parking/v1/{site_id}/status`

Retained. Published after every accepted state change.

## Device configuration

`parking/v1/{site_id}/devices/{device_id}/config`

## Commands

`parking/v1/{site_id}/devices/{device_id}/command`

Commands are not state-changing parking events. Examples include reboot, diagnostics and display test mode.

## Acknowledgement

`parking/v1/{site_id}/events/ack/{device_id}`

The Core acknowledges accepted or rejected events using `event_id` and resulting state revision.
