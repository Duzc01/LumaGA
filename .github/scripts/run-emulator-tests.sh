#!/usr/bin/env bash
# Launch an Android emulator (software rendering — the CI runners here have
# no KVM / nested virtualization), wait for boot, then run the instrumented
# tests with automatic retries. A reboot is issued between attempts because a
# starved system_server is the usual failure mode under load.
set -u

: "${ANDROID_HOME:=/usr/local/lib/android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
EMULATOR="$ANDROID_HOME/emulator/emulator"
IMG="system-images;android-30;google_apis;x86_64"
AVD_NAME="ci-test"
MAX_ATTEMPTS=3

log() { echo "[emu-test] $*"; }

log "installing system image (no-op if present)"
yes | sdkmanager --install "$IMG" --channel=0 > /dev/null

log "creating AVD"
echo no | avdmanager create avd --force -n "$AVD_NAME" --package "$IMG" > /dev/null

log "launching emulator"
"$EMULATOR" -avd "$AVD_NAME" -no-window -gpu swiftshader_indirect \
  -no-snapshot -noaudio -no-boot-anim \
  -camera-back none -camera-front none -memory 2048 &
EMU_PID=$!
trap 'kill $EMU_PID 2>/dev/null || true' EXIT

wait_for_boot() {
  "$ADB" wait-for-device > /dev/null 2>&1
  for _ in $(seq 1 120); do
    if [[ $("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r') == "1" ]]; then
      return 0
    fi
    sleep 5
  done
  return 1
}

wait_for_boot || { log "emulator failed to boot in time"; exit 1; }
log "emulator booted"

# Calm the UI down for UIAutomator stability.
"$ADB" shell settings put global window_animation_scale 0 > /dev/null
"$ADB" shell settings put global transition_animation_scale 0 > /dev/null
"$ADB" shell settings put global animator_duration_scale 0 > /dev/null
"$ADB" shell wm dismiss-keyguard > /dev/null 2>&1 || true

for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
  log "test attempt $attempt/$MAX_ATTEMPTS"
  if ./gradlew connectedDebugAndroidTest \
      -Dorg.gradle.jvmargs="-Xmx2g -Dfile.encoding=UTF-8" --stacktrace; then
    log "TESTS PASSED on attempt $attempt"
    exit 0
  fi
  log "attempt $attempt failed"
  if [[ "$attempt" -lt "$MAX_ATTEMPTS" ]]; then
    log "rebooting emulator before retry"
    "$ADB" reboot > /dev/null 2>&1 || true
    sleep 10
    wait_for_boot || { log "emulator did not come back after reboot"; exit 1; }
    log "emulator rebooted"
  fi
done

log "ALL $MAX_ATTEMPTS ATTEMPTS FAILED"
exit 1
