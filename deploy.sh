#!/bin/zsh
# Build percinel and install+launch it on the connected Android device.
# Usage: ./deploy.sh            (build release, install, launch)
#        ./deploy.sh --no-build (reuse last APK)
set -e

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
APK="app/build/outputs/apk/release/app-release.apk"

cd "$(dirname "$0")"

DEVICES=$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
if [ -z "$DEVICES" ]; then
  echo "✗ No device connected."
  echo "  USB:      plug in the phone (USB debugging on) and accept the prompt."
  echo "  Wireless: adb pair <ip:port> <code>   then   adb connect <ip:port>"
  exit 1
fi
COUNT=$(echo "$DEVICES" | wc -l | tr -d ' ')
echo "→ device(s): $(echo $DEVICES | tr '\n' ' ')"

if [ "$1" != "--no-build" ]; then
  echo "→ building release…"
  ./gradlew :app:assembleRelease --no-daemon -q
fi

for D in $DEVICES; do
  echo "→ installing on $D…"
  "$ADB" -s "$D" install -r "$APK"
  "$ADB" -s "$D" shell am start -n gopesh.percinel/.MainActivity >/dev/null
done
echo "✓ deployed $(du -h "$APK" | cut -f1 | tr -d ' ') APK"
