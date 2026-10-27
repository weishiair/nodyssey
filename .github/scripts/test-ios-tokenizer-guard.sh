#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/../.." && pwd)
out=${1:-"$root/build/tokenizer-guard-tests"}
mkdir -p "$out"
out=$(cd "$out" && pwd)
exec > >(tee "$out/run.log") 2>&1

device=""
cleanup() {
  if [[ -n "$device" ]]; then
    echo "Deleting harness simulator $device"
    xcrun simctl shutdown "$device" 2>/dev/null || true
    xcrun simctl delete "$device" || echo "Warning: could not delete $device" >&2
  fi
}
trap cleanup EXIT

test "$(uname -m)" = arm64 || { echo 'An Apple Silicon Mac is required for the arm64 simulator.' >&2; exit 1; }
command -v xcrun >/dev/null
command -v python3 >/dev/null
echo "Output: $out"
echo "DEVELOPER_DIR: ${DEVELOPER_DIR:-<default>}"
xcodebuild -version
sdk=$(xcrun --sdk iphonesimulator --show-sdk-path)
clang=$(xcrun --sdk iphonesimulator --find clang)
src="$root/iosapp/tests/tokenizer-guard"
guard="$root/iosapp/Nodyssey"
test -f "$guard/ComposeTokenizerGuard.m"

build_app() {
  local mode=$1 bundle=$2
  local app="$out/$mode.app"
  mkdir -p "$app"
  cat > "$app/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>CFBundleExecutable</key><string>TokenizerGuardTests</string>
<key>CFBundleIdentifier</key><string>$bundle</string>
<key>CFBundleName</key><string>TokenizerGuardTests</string>
<key>CFBundlePackageType</key><string>APPL</string>
<key>CFBundleVersion</key><string>1</string>
<key>CFBundleShortVersionString</key><string>1.0</string>
<key>LSRequiresIPhoneOS</key><true/>
<key>MinimumOSVersion</key><string>13.0</string>
<key>UILaunchScreen</key><dict/>
<key>UIApplicationSceneManifest</key><dict>
  <key>UIApplicationSupportsMultipleScenes</key><false/>
  <key>UISceneConfigurations</key><dict>
    <key>UIWindowSceneSessionRoleApplication</key><array><dict>
      <key>UISceneConfigurationName</key><string>Default Configuration</string>
      <key>UISceneDelegateClassName</key><string>HarnessSceneDelegate</string>
    </dict></array>
  </dict>
</dict>
</dict></plist>
PLIST
  local -a extra=()
  if [[ "$mode" == missing-class ]]; then
    extra=(-DMISSING_CLASS_TEST=1)
  else
    extra=("$src/fixture/CMPTextInputStringTokenizer.m")
  fi
  "$clang" -target arm64-apple-ios13.0-simulator -isysroot "$sdk" -fobjc-arc -fblocks \
    -Wall -Wextra -Wno-unused-parameter -I "$src/fixture" -I "$guard" \
    "$src/main.m" "$guard/ComposeTokenizerGuard.m" "${extra[@]}" \
    -framework Foundation -framework UIKit -o "$app/TokenizerGuardTests"
  codesign --force --sign - "$app"
}
build_app bridge io.github.nodyssey.tokenizerguard.bridge
build_app missing-class io.github.nodyssey.tokenizerguard.missing

xcrun simctl list runtimes --json > "$out/runtimes.json"
xcrun simctl list devicetypes --json > "$out/device-types.json"
# Prefer the newest installed iOS runtime and available iPhone device types. Try
# older device types if this runtime rejects the first candidate.
python3 - "$out" <<'PY'
import json, pathlib, sys
out = pathlib.Path(sys.argv[1])
runtimes = json.loads((out / 'runtimes.json').read_text())['runtimes']
types = json.loads((out / 'device-types.json').read_text())['devicetypes']
runtimes = [r for r in runtimes if r.get('isAvailable') and r['identifier'].startswith('com.apple.CoreSimulator.SimRuntime.iOS-')]
runtimes.sort(key=lambda r: tuple(int(x) for x in r['version'].split('.')), reverse=True)
types = [t for t in types if t['name'].startswith('iPhone')]
if not runtimes or not types:
    sys.exit('No available iOS simulator runtime or iPhone device type')
with (out / 'candidates.txt').open('w') as f:
    for r in runtimes:
        for t in reversed(types):
            f.write(r['identifier'] + '\t' + t['identifier'] + '\n')
PY
while IFS=$'\t' read -r runtime type; do
  echo "Trying runtime $runtime / device type $type"
  if device=$(xcrun simctl create "TokenizerGuard-$$" "$type" "$runtime"); then
    break
  fi
  device=""
done < "$out/candidates.txt"
[[ -n "$device" ]] || { echo 'Could not create a compatible simulator' >&2; exit 1; }
echo "Created test simulator: $device"
xcrun simctl boot "$device"
xcrun simctl bootstatus "$device" -b

run_app() {
  local mode=$1 bundle=$2
  xcrun simctl install "$device" "$out/$mode.app"
  # simctl launch can report success even when an app never ran its assertions.
  # Require both its successful exit and the explicit marker, with a timeout.
  python3 "$src/launch.py" "$device" "$bundle" "$out/$mode.log"
}
run_app bridge io.github.nodyssey.tokenizerguard.bridge
run_app missing-class io.github.nodyssey.tokenizerguard.missing
echo 'Both tokenizer guard simulator modes passed.'
