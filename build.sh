#!/bin/bash
# DEAD ZONE — manual Android build pipeline (no Gradle).
# aapt2 compile/link -> javac -> d8 -> dex into apk -> zipalign -> apksigner
set -e
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-/home/user/android-sdk}
BT=$SDK/build-tools/34.0.0
PLAT=$SDK/platforms/android-34/android.jar
[ -f "$PLAT" ] || { echo "android.jar missing: $PLAT"; exit 1; }
[ -x "$BT/aapt2" ] || { echo "aapt2 missing: $BT/aapt2"; exit 1; }

rm -rf build/gen build/obj build/apk
mkdir -p build/gen build/obj build/apk

echo "== aapt2 compile =="
"$BT/aapt2" compile --dir res -o build/res.zip

echo "== aapt2 link =="
"$BT/aapt2" link -o build/apk/base.apk -I "$PLAT" \
  --manifest AndroidManifest.xml --java build/gen build/res.zip

echo "== javac =="
find src -name '*.java' > build/sources.txt
javac --release 8 -encoding UTF-8 -classpath "build/gen:$PLAT" -d build/obj @build/sources.txt

echo "== d8 =="
find build/obj -name '*.class' > build/classes.txt
"$BT/d8" --release --min-api 24 --lib "$PLAT" --output build/apk $(cat build/classes.txt | tr '\n' ' ')

echo "== package =="
( cd build/apk && zip -q base.apk classes.dex )
( cd . && zip -qr build/apk/base.apk assets )
"$BT/zipalign" -f 4 build/apk/base.apk build/apk/aligned.apk

echo "== sign =="
JH=$(dirname $(dirname $(readlink -f $(which javac))))
mkdir -p signing
if [ ! -f signing/keystore.jks ]; then
  echo "== keystore: generating signing/keystore.jks (persisted) =="
  "$JH/bin/keytool" -genkeypair -keystore signing/keystore.jks -alias deadzone -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass deadzone123 -keypass deadzone123 \
    -dname "CN=Dead Zone, OU=Dev, O=DeadZone, L=Lucknow, ST=UP, C=IN" >/dev/null 2>&1
fi
"$BT/apksigner" sign \
  --ks signing/keystore.jks --ks-pass pass:deadzone123 --key-pass pass:deadzone123 \
  --ks-key-alias deadzone --v1-signing-enabled true --v2-signing-enabled true \
  --out DEADZONE_0.5.0.apk build/apk/aligned.apk

"$BT/apksigner" verify DEADZONE_0.5.0.apk
echo "BUILD_OK -> $(pwd)/DEADZONE_0.5.0.apk ($(du -h DEADZONE_0.5.0.apk | cut -f1))"
