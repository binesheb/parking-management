# Update and Recovery

## Automatic update policy

Any future automatic firmware update mechanism must use this repository's `main` branch as its only source. It must not follow feature, development, or release branches.

Before applying an automatic update, the device or deployment should verify that the candidate is newer, complete any required dependency/build preparation, validate the artifact where supported, and keep a recovery path to the previously known-good firmware.

## Manual development update

From a clean checkout:

```bash
./scripts/bootstrap.sh
```

The helper installs the dependencies declared by `firmware/platformio.ini` and builds the firmware. It does not modify device configuration or flash hardware.

## Manual firmware recovery

Use PlatformIO to upload a validated build to the target controller:

```bash
pio run --project-dir firmware --target upload
```

Keep the previous known-good firmware artifact available until the updated controller has completed basic provisioning, dashboard access, event handling, and reboot checks.
