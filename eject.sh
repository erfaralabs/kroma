#!/usr/bin/env bash
#
# Safely release Kroma's external build storage so the drive can be ejected.
#
# Two things are mounted:
#   /Volumes/GradleHome  <- APFS sparse image (the Gradle cache)
#   /Volumes/ERFARA      <- the physical USB drive (the image lives inside it)
#
# Order matters: detach the image BEFORE ejecting the physical drive.
#
# Usage:
#   ./eject.sh            # stop Gradle daemons + detach the APFS image
#   ./eject.sh --drive    # ...and also eject the physical drive
#
set -uo pipefail

cd "$(dirname "$0")"

export JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/Volumes/GradleHome}"

# 1. Stop Gradle daemons — they hold files open on the image and block detach.
echo "Stopping Gradle daemons..."
./gradlew --stop >/dev/null 2>&1 || true
pkill -f GradleDaemon 2>/dev/null || true
sleep 1

# 2. Detach the APFS sparse image (retry, then force).
if [ -d /Volumes/GradleHome ]; then
  echo "Detaching /Volumes/GradleHome ..."
  hdiutil detach /Volumes/GradleHome -quiet 2>/dev/null \
    || { sleep 2; hdiutil detach /Volumes/GradleHome -quiet 2>/dev/null; } \
    || hdiutil detach /Volumes/GradleHome -force 2>/dev/null \
    || echo "  (could not detach — something still has it open)"
  [ -d /Volumes/GradleHome ] && echo "  still mounted" || echo "  detached OK"
else
  echo "/Volumes/GradleHome not mounted."
fi

# 3. Optionally eject the physical drive.
if [ "${1:-}" = "--drive" ]; then
  echo "Ejecting /Volumes/ERFARA ..."
  diskutil eject /Volumes/ERFARA || echo "  (eject failed — close anything using it)"
fi

echo
echo "Done. You can now eject in Finder."
