#!/usr/bin/env bash
#
# Kroma build helper.
#   * Forces an arm64 JDK (the default `java` on this Mac is a broken x86 build).
#   * Points GRADLE_USER_HOME at the external drive so the ~0.5 GB Gradle
#     distribution + dependency cache never touch the (tight) internal disk.
#
# Usage:
#   ./build.sh :app:testDebugUnitTest :app:assembleDebug
#   ./build.sh :app:assembleDebug
#
set -euo pipefail

cd "$(dirname "$0")"

export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home}"

EXTERNAL_DRIVE="/Volumes/ERFARA"
SPARSE_IMAGE="$EXTERNAL_DRIVE/dev/GradleHome.sparseimage"
EXTERNAL_GRADLE_HOME="/Volumes/GradleHome"

# Auto-attach the APFS sparse image if the drive is present but not yet mounted.
if [ ! -d "$EXTERNAL_GRADLE_HOME" ] && [ -f "$SPARSE_IMAGE" ]; then
  echo "attaching $SPARSE_IMAGE ..."
  hdiutil attach "$SPARSE_IMAGE" >/dev/null 2>&1 || true
fi

if [ -d "$EXTERNAL_GRADLE_HOME" ]; then
  export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$EXTERNAL_GRADLE_HOME}"
else
  echo "warning: external Gradle home unavailable — falling back to ~/.gradle" >&2
fi

echo "JAVA_HOME=$JAVA_HOME"
echo "GRADLE_USER_HOME=${GRADLE_USER_HOME:-$HOME/.gradle}"
echo

exec ./gradlew "$@"
