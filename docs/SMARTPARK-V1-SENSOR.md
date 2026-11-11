# SmartPark V1 — XIAO ESP32-S3 Sense camera slot sensor

Status: engineering prototype specification  
Mounting: 3.0 m indoor ceiling, one camera per marked bay, Wi-Fi, wired DC power.

## 1. Scope and non-goals

A sensor reports the occupancy of exactly one configured parking slot. It does not perform ANPR, identify a person, record continuous video, or control a barrier. Image processing is local; images are not uploaded in normal operation. The server receives occupancy events and health telemetry only.

The existing Android ANPR pipeline remains separate. Both subsystems can contribute to the same parking-management product, but a slot sensor is not a substitute for a plate-reading lane camera.

## 2. Camera and optical geometry

Use the camera module supplied with the Seeed Studio XIAO ESP32S3 Sense. Current Seeed production revisions may contain OV2640, OV3660 or OV5640; firmware logs the detected sensor PID and must not assume one model. The OV5640 revision includes autofocus; OV2640/OV3660 are fixed-focus. Seeed's current camera guide supports its camera examples across these variants.

Initial capture profile: QVGA 320x240 grayscale, one framebuffer in PSRAM, 1 Hz sampling. This is a baseline-change prototype detector, not a trained vehicle classifier. Do not claim production-grade AI accuracy until a labeled site dataset and quantized model are evaluated.

For a nominal 5.0 m x 2.5 m bay viewed from 3.0 m directly overhead:
- Target horizontal field of view along the 5 m bay axis: about 80 degrees.
- Target vertical field of view across the bay: about 63 degrees for a 4:3 image.
- Confirm the actual supplied lens with a calibration photograph; its FOV is not guaranteed by this specification.
- Keep adjacent bays out of the ROI where practical. If the supplied lens cannot frame one bay, use a compatible lens/module with documented FOV or revise the mounting point. Do not crop so tightly that bumpers routinely leave the ROI.

Optical acceptance: all four bay boundaries and a 10–15% margin are visible; neighboring bays contribute as little as practical; the image remains sharp at the ground plane; no window reflections or direct luminaire glare.

## 3. Enclosure and mounting

Prototype envelope: 100 x 80 x 45 mm (excluding cable gland and bracket). A parametric OpenSCAD starting model is provided at `hardware/enclosure/smartpark-v1.scad`; export `part = "base"` and `part = "lid"` separately. Final dimensions, clip positions and lens aperture must be adjusted after measuring the actual camera carrier and lens protrusion.

- Ceiling bracket: two fixing points, slotted adjustment for aiming, mechanical anti-rotation feature.
- Housing: PETG prototype; production candidate flame-retardant ABS or PC/ABS.
- Camera window: clear optical-grade polycarbonate or acrylic, flat and replaceable; lens close to the inner surface. Do not use smoked acrylic.
- Camera opening: size to the actual lens barrel; do not assume a universal 18–25 mm hole before measuring.
- PCB: insulated standoffs; no conductive hardware beneath exposed pads.
- Cable: rear strain-relieved gland; separate low-voltage power compartment from camera optics.
- Indoor target: dust-resistant, wipeable enclosure. Validate condensation and temperature before assigning an IP rating.
- No IR illuminator in V1. Use existing indoor lighting; evaluate flicker and night scenes during site capture.

## 4. Power

Preferred distribution: 12 V DC trunk to each bay, local regulated 5 V buck converter at the node. This avoids 5 V drop on long ceiling cable runs.

Per node:
- 12 V input protection: correctly rated fuse, reverse-polarity protection and transient suppression.
- Buck: regulated 5.0 V, at least 1 A continuous output with thermal headroom; select a 2 A-rated module for margin.
- Local decoupling: 470–1000 uF low-ESR bulk capacitor plus 100 nF ceramic near the 5 V input.
- Feed the XIAO through its documented 5 V input/USB-C power path. Never connect 12 V to the board.
- Verify 5 V at the board under Wi-Fi transmit and camera capture load. Do not rely on a long thin USB cable.

Electrical work on building mains must be performed by a qualified electrician. This design is low-voltage DC at the sensor.

## 5. Detection V1

Commissioning requires an empty slot. The installer invokes the local calibration endpoint after confirming the bay is empty. Firmware stores a compact 32x24 grayscale reference signature in NVS.

At each sample:
1. Capture QVGA grayscale frame.
2. Sample the configured central ROI into a 32x24 signature.
3. Compensate for global brightness shift.
4. Compute the fraction of cells whose local difference exceeds the configured pixel threshold.
5. Apply temporal hysteresis: 3 consecutive occupied candidates to set OCCUPIED; 5 consecutive clear candidates to set AVAILABLE.
6. If camera capture fails or baseline is absent, publish UNKNOWN / detector fault, never AVAILABLE.

The reference-difference detector is a commissioning baseline only. It can be affected by shadows, cleaning, changed lighting, moved camera, and objects other than vehicles. Phase 1 data collection must capture empty/occupied images at different times and with different vehicle colors. Phase 2 should replace the heuristic with a quantized EMPTY/OCCUPIED model trained on site data, retaining the same OccupancyDetector interface and server protocol.

## 6. Firmware

Dedicated PlatformIO project: `firmware/xiao-sense`. It is separate from the existing generic ESP32 manual controller profile.

Responsibilities:
- XIAO ESP32S3 Sense camera pin profile and runtime sensor identification.
- First-boot Wi-Fi provisioning AP.
- Bootstrap registration and per-device bearer token storage in NVS.
- Local occupancy sampling and temporal state machine.
- Durable bounded event outbox; retry after network recovery.
- 30-second heartbeat with uptime, RSSI, camera/detector state and firmware version.
- Local diagnostics and authenticated-by-setup-key calibration endpoint.
- No continuous image streaming and no image upload.

## 7. Server protocol

The existing Fastify + SQLite server receives:
- `POST /api/v1/sensors/register` — bootstrap-authenticated, one-time registration.
- `POST /api/v1/sensors/events` — per-device bearer token, idempotent event ID and strictly increasing sequence.
- `POST /api/v1/sensors/heartbeat` — per-device bearer token.
- `GET /api/v1/sensors/slots` — slot state and derived online/offline health.

Set `SENSOR_BOOTSTRAP_TOKEN` to a high-entropy secret before enrolling devices (for example, `export SENSOR_BOOTSTRAP_TOKEN=$(openssl rand -hex 32)` before `docker compose up --build`). Registration returns a random per-device token once; firmware stores it in NVS. Store only SHA-256 token hashes on the server. Keep the API on the trusted parking LAN or behind HTTPS/reverse-proxy authentication. Never commit Wi-Fi credentials or bootstrap tokens.

Device states: PENDING/ACTIVE at registration; health becomes OFFLINE after 120 seconds without heartbeat. A stale device's slot is presented as UNKNOWN, not AVAILABLE. Duplicate event IDs are acknowledged without applying twice; stale sequence numbers are rejected.

## 8. Device identity

Example:
- deviceId: `SP-KOCHI-P01-S001`
- areaId: `KOCHI-P01`
- slotId: `S001`

The device-to-slot assignment is provisioned at installation and can be changed only through a controlled re-provisioning process.

## 9. Prototype acceptance

- 100 empty/occupied transitions observed under normal indoor lighting.
- No false AVAILABLE state when camera is disconnected or calibration is absent.
- Occupied confirmation target <= 5 seconds; clear confirmation target <= 8 seconds.
- Wi-Fi outage does not stop local detection; queued events synchronize after reconnect.
- Duplicate events do not alter state twice.
- Offline device becomes UNKNOWN after 120 seconds.
- 24-hour burn-in without camera task leak, reboot loop or lost event sequence.
- Test shadows, headlights, floor cleaning, people walking through the ROI, trolley movement and parked motorcycles separately.

Do not deploy as a safety or access-control system until field accuracy is measured and a failure-mode review is complete.
