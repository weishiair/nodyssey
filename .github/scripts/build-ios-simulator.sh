#!/usr/bin/env bash
set -euo pipefail
output="$1"
version=$(sed -n 's/^nodyssey\.versionName=//p' gradle.properties | head -1)
code=$(sed -n 's/^nodyssey\.versionCode=//p' gradle.properties | head -1)
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][A-Za-z0-9.-]+)?$ ]]
[[ "$code" =~ ^[1-9][0-9]*$ ]]
mkdir -p "$output"
xcodebuild \
  -project iosapp/Nodyssey.xcodeproj \
  -scheme Nodyssey \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath "$output/DerivedData" \
  build \
  CODE_SIGNING_ALLOWED=NO \
  ARCHS=arm64 \
  ONLY_ACTIVE_ARCH=YES \
  MARKETING_VERSION="$version" \
  CURRENT_PROJECT_VERSION="$code" 2>&1 | tee "$output/xcodebuild.log"
app="$output/DerivedData/Build/Products/Debug-iphonesimulator/Nodyssey.app"
test -d "$app"
test "$(/usr/libexec/PlistBuddy -c 'Print CFBundleShortVersionString' "$app/Info.plist")" = "$version"
test "$(/usr/libexec/PlistBuddy -c 'Print CFBundleVersion' "$app/Info.plist")" = "$code"
test "$(/usr/libexec/PlistBuddy -c 'Print CFBundleIdentifier' "$app/Info.plist")" = 'io.github.nodyssey'
test "$(/usr/libexec/PlistBuddy -c 'Print CFBundleSupportedPlatforms:0' "$app/Info.plist")" = 'iPhoneSimulator'
executable=$(/usr/libexec/PlistBuddy -c 'Print CFBundleExecutable' "$app/Info.plist")
test "$(lipo -archs "$app/$executable")" = 'arm64'
xcrun vtool -show-build "$app/$executable" | tee "$output/binary-platform.txt"
grep -q 'platform IOSSIMULATOR' "$output/binary-platform.txt"
binary_sdk=$(awk '$1 == "sdk" { print $2 }' "$output/binary-platform.txt")
if [[ ! "$binary_sdk" =~ ^([0-9]+)\. ]] || (( ${BASH_REMATCH[1]} < 26 )); then
  echo "Liquid Glass requires linking with iOS SDK 26 or newer; simulator app uses $binary_sdk." >&2
  exit 1
fi
python3 .github/scripts/native-network-notices.py "$app/NativeNetworkLicenses.txt"
cp -R "$app" "$output/Nodyssey.app"
name="nodyssey-$version-simulator-arm64.zip"
ditto -c -k --sequesterRsrc --keepParent "$output/Nodyssey.app" "$output/$name"
unzip -t "$output/$name" > "$output/app-zip-check.txt"
git rev-parse HEAD > "$output/source-commit.txt"
printf 'version=%s\nbuild=%s\nplatform=iPhoneSimulator\narchitecture=arm64\nsdk=%s\n' "$version" "$code" "$binary_sdk" > "$output/build-info.txt"
(cd "$output" && shasum -a 256 "$name" > SHA256SUMS.txt)
