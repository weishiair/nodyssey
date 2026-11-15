#!/usr/bin/env bash
set -euo pipefail

# A standalone Kotlin/Native simulator process has no pasteboard server. Boot iOS before UIKit tests.
device=$(xcrun simctl list devices available -j | python3 -c '
import json
import sys

devices = json.load(sys.stdin)["devices"]
print(next(
    device["udid"]
    for runtime, entries in devices.items()
    if ".iOS-" in runtime
    for device in entries
    if device["name"].startswith("iPhone")
))
')
xcrun simctl bootstatus "$device" -b >&2
printf '%s\n' "$device"
