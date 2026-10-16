#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export VIBE_TOOLS_DIR="${VIBE_TOOLS_DIR:-/workspace/.vibe-tools}"
python3 scripts/setup_cloud.py
# shellcheck source=/dev/null
source "$VIBE_TOOLS_DIR/env.sh"
./gradlew assembleDebug testDebugUnitTest lintDebug --console=plain
