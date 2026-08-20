# Device Protocol

## Event envelope

```json
{
  "event_id": "uuid-or-device-sequence",
  "device_id": "unique-device-id",
  "sequence": 42,
  "action": "car_in"
}
```

Supported actions:

- `car_in`
- `car_out`
- `slot_plus`
- `slot_minus`

## Registration states

`discovered -> pending -> approved -> active -> offline`

## Heartbeat

Clients periodically report device identity, firmware version, and connectivity. The Master records the last-seen timestamp.

The exact wire transport will be implemented with mDNS discovery plus a small local UDP/HTTP registration protocol.
