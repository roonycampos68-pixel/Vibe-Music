#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Configura ANDROID_HOME con tu SDK Android}"
apk="${1:-app/build/outputs/apk/debug/app-arm64-v8a-debug.apk}"
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --verbose "$apk"
sha256sum "$apk"
