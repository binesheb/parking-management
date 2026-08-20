# Parking Management Core

The v1 core is the single source of truth for parking state.

## Scope

- parking areas and capacity
- CAR_IN and CAR_OUT events
- SLOT_PLUS and SLOT_MINUS corrections
- append-only event history
- persistent state
- live status API
- prediction of the next slot becoming free when all configured parking capacity is occupied

## Event model

Each accepted event contains an event id, source device id, event type, area id, timestamp and resulting state revision. Events are idempotent by event id.

## State invariant

For every area:

`0 <= occupied <= capacity`

`available = capacity - occupied`

Correction events must never violate these bounds.

## Prediction

Only CAR_OUT events create slot-release observations. When available capacity is zero, the predictor estimates the interval until the next slot release using recent intervals first and historical fallback data second. If insufficient observations exist, the result is `unknown` rather than an invented estimate.
