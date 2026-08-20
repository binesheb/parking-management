# Hardware

Initial target: standard ESP32 development board.

Release 0.1.0 hardware interface:

- ESP32
- 4 momentary push buttons
- optional local display in a later hardware profile

Buttons map logically to:

1. CAR IN
2. CAR OUT
3. SLOT +
4. SLOT -

GPIO assignments are intentionally not fixed in the core. A board profile will define them so the same firmware architecture can support different enclosures and ESP32 variants.
