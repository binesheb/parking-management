# Next Slot Predictor

## Purpose

When total available parking is zero, estimate how long it may take for the next slot to become available.

## Inputs

Accepted CAR_OUT events create slot-release timestamps.

For ordered release timestamps `t1, t2, ... tn`, derive intervals between consecutive releases.

## v1 strategy

1. use the most recent valid release intervals when enough samples exist;
2. reject invalid or extreme intervals outside configured bounds;
3. calculate a robust central estimate from the retained sample;
4. if recent data is insufficient, fall back to historical intervals for the same configured time bucket;
5. if still insufficient, return `unknown`.

The predictor is advisory. It never changes the official parking count.

## Output

- estimated minutes until next release
- sample count
- confidence: low, medium, high, unknown
- source: recent or historical

The display should render `NEXT SLOT ~X MIN` only when a prediction is available. Otherwise it should show `PARKING FULL` without inventing a wait time.
