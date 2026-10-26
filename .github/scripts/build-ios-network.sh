#!/usr/bin/env bash
set -euo pipefail
target="$1"
case "$target" in
  aarch64-apple-ios|aarch64-apple-ios-sim|aarch64-apple-darwin) ;;
  *) echo 'Unsupported Apple network target' >&2; exit 1 ;;
esac
command -v cargo >/dev/null || { echo 'Rust is required to build the iOS DoH/ECH transport.' >&2; exit 1; }
cd native/ios-network
rustup target add "$target"
export IPHONEOS_DEPLOYMENT_TARGET=16.0
cargo build --locked --release --target "$target"
