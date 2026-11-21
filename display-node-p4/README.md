# Parking Management Display Node

ESP32-S3 based customer-facing display node for HUB75 RGB LED matrix panels.

## Reference hardware

- MCU: ESP32-S3
- Reference panel: Waveshare RGB Matrix P4 80x40
- Interface: HUB75
- Supply: external 5V power supply for LED panel
- Network: Wi-Fi

## First milestone

The first milestone is a standalone 80x40 display node with:

1. persistent configuration;
2. Wi-Fi provisioning and local setup access;
3. configurable panel geometry;
4. configurable HUB75 pin mapping;
5. brightness control;
6. display test patterns;
7. parking availability renderer;
8. Master connection settings;
9. diagnostics and firmware metadata.

The firmware must not hard-code the Waveshare panel. `80x40` is the default profile; panel dimensions and chain geometry remain configuration-driven.

## Data contract

The renderer consumes a normalized parking snapshot:

```json
{
  "site_id": "palarivattom",
  "available": 35,
  "capacity": 50,
  "occupied": 15,
  "status": "available",
  "updated_at": 0
}
```

Future Master versions may add areas without breaking the basic display contract.
