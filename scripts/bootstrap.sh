#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
firmware_dir="$repo_root/firmware"

if ! command -v pio >/dev/null 2>&1; then
  echo "PlatformIO CLI (pio) is required. Install PlatformIO Core and rerun this script." >&2
  exit 1
fi

if [[ ! -f "$firmware_dir/platformio.ini" ]]; then
  echo "Missing firmware/platformio.ini; run this script from a complete checkout." >&2
  exit 1
fi

echo "Installing declared PlatformIO dependencies and validating the firmware..."
pio pkg install --project-dir "$firmware_dir"
pio run --project-dir "$firmware_dir"

echo "Bootstrap and firmware validation completed successfully."
