# Simple ESP32 Controller

Configure Wi-Fi and `SERVER_URL`, then map four momentary buttons to `CAR_IN`, `CAR_OUT`, `SLOT_PLUS`, and `SLOT_MINUS`.

Each button sends:

`POST {SERVER_URL}/api/event`

with JSON:

```json
{"type":"CAR_IN","source":"controller-01"}
```

For beta deployment, failed requests should be visibly indicated and retried by the controller firmware. Hardware pin mapping remains configurable for the actual enclosure.
