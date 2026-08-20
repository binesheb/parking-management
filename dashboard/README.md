# Parking Management Dashboard

## v1 Overview

The dashboard consumes the Core live status contract and never maintains an independent parking count.

### Overview cards

- available slots
- occupied slots
- total capacity
- parking status
- predicted next slot when full and prediction is available

### Areas

Show capacity, occupied and available values for every configured parking area.

### Live events

Display accepted CAR_IN, CAR_OUT, SLOT_PLUS and SLOT_MINUS events with source device and timestamp.

### Devices

Display Master, controller and display node health.

Cars waiting and queue management are intentionally outside v1 scope.
