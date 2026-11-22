#!/usr/bin/env bash
set -euo pipefail

package=com.ghostnexora.vpn
adb install -g app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell cmd deviceidle whitelist +"$package"
trap 'adb logcat -d -t 1000 > emulator-vpn-logcat.log || true' EXIT
adb shell am instrument -w -r \
  -e class "$package.VpnLaunchTest,$package.ProfileProtectionTest" \
  "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee emulator-vpn-launch.log
grep -Fq 'OK (2 tests)' emulator-vpn-launch.log
