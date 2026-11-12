#!/usr/bin/env bash
# Run as one script: android-emulator-runner executes separate input lines in separate shells.
set -euo pipefail
stage=setup
failed() {
  result=$?
  if (( result != 0 )); then
    printf '::error::Android 6 smoke test failed at stage: %s\n' "$stage"
    details=$(adb logcat -d -s AndroidRuntime:E ActivityManager:W | tail -c 9000 || true)
    details=${details//'%'/'%25'}
    details=${details//$'\n'/'%0A'}
    details=${details//$'\r'/'%0D'}
    printf '::error::Android log: %s\n' "$details"
  fi
}
trap failed EXIT
stage=find-apk
mapfile -t apks < <(find android6-apks -name '*_x86_64.apk')
test "${#apks[@]}" -eq 1
stage=install
adb install "${apks[0]}"
stage=start
output=$(adb shell am start -W -n com.v2ray.ang.newface/com.v2ray.ang.ui.main.MainActivity)
printf '%s\n' "$output"
printf '%s\n' "$output" | grep -F 'Status: ok'
stage=process
adb shell ps | grep -F 'com.v2ray.ang.newface'

stage=core-asset-instrumentation
mapfile -t tests < <(find android6-test-apk -name '*.apk')
test "${#tests[@]}" -eq 1
adb install "${tests[0]}"
output=$(adb shell am instrument -w -r -e class com.v2ray.ang.GeoAssetsCoreTest com.v2ray.ang.newface.test/androidx.test.runner.AndroidJUnitRunner)
printf '%s\n' "$output"
printf '%s\n' "$output" | grep -E 'OK \(1 test\)'
