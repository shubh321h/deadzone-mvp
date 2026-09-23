#!/bin/bash
# One-shot Android SDK bootstrap (no Gradle, no sdkmanager needed).
set -e
SDK=/home/user/android-sdk
mkdir -p "$SDK/platforms" "$SDK/build-tools"

if [ -n "$(find $SDK -name aapt2 2>/dev/null | head -1)" ]; then
  echo "SDK already present"
  echo DONE
  exit 0
fi

cd /tmp

# --- platform android-34 ---
P=""
for url in \
  https://dl.google.com/android/repository/platform-34-ext12_r01.zip \
  https://dl.google.com/android/repository/platform-34-ext7_r03.zip \
  https://dl.google.com/android/repository/platform-34-ext11_r01.zip ; do
  echo "trying $url"
  if curl -sSLo plat.zip "$url" && [ -s plat.zip ] && unzip -t plat.zip >/dev/null 2>&1; then
    P=plat.zip; break
  fi
done
[ -n "$P" ] || { echo "PLATFORM_DOWNLOAD_FAILED"; exit 1; }
rm -rf /tmp/platx && mkdir -p /tmp/platx
unzip -q "$P" -d /tmp/platx
D=$(ls /tmp/platx | head -1)
rm -rf "$SDK/platforms/android-34"
mv "/tmp/platx/$D" "$SDK/platforms/android-34"
[ -f "$SDK/platforms/android-34/android.jar" ] || { echo "PLATFORM_BAD"; ls /tmp/platx; exit 1; }
echo PLATFORM_OK

# --- build-tools 34.0.0 ---
B=""
for url in \
  https://dl.google.com/android/repository/build-tools_r34-linux.zip \
  https://dl.google.com/android/repository/build-tools_r33.0.1-linux.zip ; do
  echo "trying $url"
  if curl -sSLo bt.zip "$url" && [ -s bt.zip ] && unzip -t bt.zip >/dev/null 2>&1; then
    B=bt.zip; break
  fi
done
[ -n "$B" ] || { echo "BT_DOWNLOAD_FAILED"; exit 1; }
rm -rf /tmp/btx && mkdir -p /tmp/btx
unzip -q "$B" -d /tmp/btx
D=$(ls /tmp/btx | head -1)
rm -rf "$SDK/build-tools/34.0.0"
mv "/tmp/btx/$D" "$SDK/build-tools/34.0.0"
[ -f "$SDK/build-tools/34.0.0/aapt2" ] || { echo "BT_BAD"; ls /tmp/btx; exit 1; }
chmod +x "$SDK/build-tools/34.0.0/aapt2" 2>/dev/null || true
echo BT_OK

# --- license acceptance marker (some tools check it) ---
mkdir -p "$SDK/licenses"
printf "\n24333f8a63b6825ea9c5514f83c2829b004d1fee\n" > "$SDK/licenses/android-sdk-license"
printf "\n84831b9409646a918e30573bab4c9c91346d8abd\n" >> "$SDK/licenses/android-sdk-license"
printf "\nd56f5187479451eabf01fb78af6dfcb131a6481e\n" >> "$SDK/licenses/android-sdk-license"

find $SDK -name aapt2
echo DONE
