#!/usr/bin/env bash
set -euo pipefail

package=com.ghostnexora.vpn
apk="$(find app/build/outputs/apk/release -type f -name '*.apk' -print -quit)"
test -s "$apk"

# The emulator ran the Debug instrumentation first. A Release signed with the
# persistent certificate needs a fresh install on this disposable emulator.
adb uninstall "$package"
adb install -g "$apk"
adb shell cmd deviceidle whitelist +"$package"
adb logcat -b crash -c
trap 'adb logcat -b crash -d > emulator-release-logcat.log || true' EXIT

for launch in 1 2; do
    adb shell am force-stop "$package"
    adb shell am start -W -n "$package/.ui.MainActivity"
    dashboard_ready=false
    for attempt in {1..10}; do
        adb shell rm -f /sdcard/ghost-release-window.xml
        if adb shell uiautomator dump /sdcard/ghost-release-window.xml >/dev/null &&
            adb exec-out cat /sdcard/ghost-release-window.xml > emulator-release-window.xml &&
            grep -Eq 'text="(Connect|Conectar)"' emulator-release-window.xml &&
            grep -Fq "package=\"$package\"" emulator-release-window.xml; then
            dashboard_ready=true
            break
        fi
        if adb logcat -b crash -d | grep -F "Process: $package," >/dev/null; then
            echo "Release crashed during launch $launch"
            exit 1
        fi
        sleep 1
    done
    if [[ "$dashboard_ready" != true ]]; then
        echo "Release did not show the dashboard during launch $launch"
        exit 1
    fi
    services="$(adb shell dumpsys activity services "$package")"
    if grep -Fq '.service.GhostVpnService' <<< "$services"; then
        echo "Release started the VPN without the Connect action"
        exit 1
    fi
    echo "Release dashboard opened without starting the VPN ($launch/2)."
done
