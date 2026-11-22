#!/usr/bin/env bash
set -euo pipefail

package=com.ghostnexora.vpn
adb install -g app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell cmd deviceidle whitelist +"$package"
adb shell am instrument -w -r \
  -e class "$package.VpnLaunchTest" \
  "$package.test/androidx.test.runner.AndroidJUnitRunner" | tee emulator-vpn-launch.log
grep -Fq 'OK (1 test)' emulator-vpn-launch.log
