# Local Android Server

The master Android device is the local source of truth and HTTP monitoring server.

## Camera roles

- COMPOUND_IN: compound entrance
- COMPOUND_OUT: compound exit
- PARKING_IN: parking entrance
- PARKING_OUT: parking exit
- MASTER: optional master/server device

## Monitoring

Other phones, tablets and PCs on the same LAN can open the master phone's local address and view vehicle counts, parking occupancy, free spaces and recent events.

The master stores events locally and continues operating without Internet access. The Android server should run as a foreground service for reliable unattended operation.

Read-only endpoints: `/`, `/api/status`, `/api/events`, `/api/vehicles/{plate}`, `/api/health`.

Administrative actions remain local to the master application. Use an isolated LAN/VLAN and optional monitoring authentication when the network is not trusted.
