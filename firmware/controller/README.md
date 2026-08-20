# ESP32 Parking Controller

## Inputs

- CAR IN
- CAR OUT
- SLOT +
- SLOT -

## Event safety

Each button action generates a unique event id. The controller retries delivery when disconnected, but the Master processes each event id only once.

The controller periodically receives the authoritative state revision so that reconnecting devices converge without changing counts twice.

## v1 deployment

The initial Palarivattom deployment may start with one controller and one parking area, but area id and device identity remain configurable.
