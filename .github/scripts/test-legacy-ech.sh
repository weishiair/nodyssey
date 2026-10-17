#!/usr/bin/env bash
set -euo pipefail

# Keep logs even when an old image's instrumentation discovers no tests and returns success.
mkdir -p ech-device-log
capture_log() { adb logcat -d > ech-device-log/logcat.txt || true; }
trap capture_log EXIT
adb shell dumpsys webviewupdate > ech-device-log/webview.txt

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.nodyssey.BundledEchDeviceTest \
  -Pandroid.testInstrumentationRunnerArguments.liveEch=true \
  "-Pandroid.testInstrumentationRunnerArguments.dohEndpoint=$1"
python3 .github/scripts/check-ech-device-results.py
