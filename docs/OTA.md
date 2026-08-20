# OTA Update Architecture

## Overview

Parking Management devices determine update availability through a small release manifest rather than inspecting Git branches.

Each firmware image embeds its current semantic version, for example `0.1.0`.

Devices are assigned an update channel:

- `stable`
- `beta`

The device fetches the corresponding manifest, compares versions, and only proceeds when the manifest contains a newer compatible version with a valid firmware URL and SHA-256 checksum.

## Manifest URLs

Stable:

`releases/stable.json`

Beta:

`releases/beta.json`

In production firmware these paths should be resolved to the raw GitHub URL or a release/CDN endpoint.

## Update decision

1. Read installed version.
2. Fetch selected channel manifest.
3. Parse semantic version.
4. Reject malformed manifests.
5. If available version is not newer, stop.
6. Verify compatibility requirements.
7. Require a non-empty firmware URL and SHA-256.
8. Download firmware.
9. Verify SHA-256.
10. Write to the inactive OTA partition.
11. Reboot into the new image.
12. Run health checks and mark the image valid only after successful startup.

## Safety

A manifest without firmware artifacts is valid as a baseline record but must never trigger an OTA installation.

Production releases must provide:

- firmware URL
- SHA-256 checksum
- version
- channel
- publication timestamp
- release notes

## Check schedule

Devices should check on boot after network connectivity is established and periodically thereafter. The initial production recommendation is every 12 hours with jitter to avoid synchronized requests.

## Version policy

Use semantic versions:

`MAJOR.MINOR.PATCH`

Examples:

- `0.1.0`
- `0.2.0`
- `1.0.0`

Beta builds may use a prerelease suffix such as `0.2.0-beta.1`.

Stable devices must not install beta manifests.
