# Display Node Configuration

## Device

- device name
- enabled state
- role

## Network

- Wi-Fi provisioning
- DHCP/static addressing
- automatic Master discovery
- manual Master URL fallback

## HUB75 panel

The firmware stores panel geometry as:

`panel_width * panels_horizontal`

and

`panel_height * panels_vertical`.

The default reference profile is `80x40`, one panel.

The setup UI must expose:

- panel width
- panel height
- horizontal panel count
- vertical panel count
- scan profile
- brightness
- color order
- HUB75 pin mapping

## Content

- parking availability mode
- header text
- normal/low/full messages
- low availability threshold

## Diagnostics

The configuration UI must include test patterns for red, green, blue, white, grid and text, plus network and Master connection status.

## Persistence

Validated settings are stored in ESP32 NVS. Invalid panel geometry or duplicate/unsupported GPIO assignments must be rejected before activation.
