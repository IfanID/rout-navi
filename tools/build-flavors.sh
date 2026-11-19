#!/usr/bin/env bash
#
# Build dua varian aplikasi lalu PINDAHKAN APK-nya ke storage HP.
#   media3 -> File.Explorer-<mode>.apk          (Media3 saja, nama lama)
#   vlc    -> File.Explorer-vlc-<mode>.apk      (Media3 + libVLC)
#
# Kedua varian memakai applicationId yang sama, jadi hanya satu yang bisa
# terpasang di perangkat; memasang yang lain menggantikannya.
#
# Dijalankan otomatis oleh android-build bila proyek punya varian "media3" dan
# "vlc". Bisa juga dijalankan langsung dari direktori proyek.

set -euo pipefail

mode="${1:-debug}"
case "$mode" in
    debug|release) ;;
    *) echo "Mode tidak dikenal: $mode" >&2; exit 1 ;;
esac

HOME_DIR="$HOME"
SDCARD="$HOME_DIR/sdcard"
proyek="$(cd "${2:-$PWD}" && pwd)"

export ANDROID_HOME="$HOME_DIR/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

jdk_bawaan="$HOME_DIR/opt/jdk"
jdk_kompatibel="$HOME_DIR/opt/jdk-21.0.13+9"
path_asli="$PATH"

versi_gradle="$(sed -n 's|^distributionUrl=.*/gradle-\([0-9][0-9.]*\)-[a-z]*\.zip.*|\1|p' \
    "$proyek/gradle/wrapper/gradle-wrapper.properties" 2>/dev/null | head -1)"
mayor_gradle="${versi_gradle%%.*}"
jdk_terpilih="$jdk_kompatibel"
if [ -n "$versi_gradle" ] && [ "${mayor_gradle:-0}" -ge 9 ] && [ -x "$jdk_bawaan/bin/java" ]; then
    jdk_terpilih="$jdk_bawaan"
fi
export JAVA_HOME="$jdk_terpilih"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$path_asli"

keystore="$HOME_DIR/.android/keystore/release.jks"
kredensial="$HOME_DIR/.android/keystore/release.properties"
apksigner="$ANDROID_HOME/build-tools/36.0.0/apksigner"

if [ -n "${ANDROID_BUILD_TUJUAN:-}" ]; then
    tujuan="$ANDROID_BUILD_TUJUAN"
elif [ "$mode" = "debug" ]; then
    tujuan="$SDCARD/Documents/app/debug"
else
    tujuan="$SDCARD/Documents/app"
fi

if [ "$mode" = "release" ] && { [ ! -f "$keystore" ] || [ ! -f "$kredensial" ]; }; then
    echo "Keystore release belum ada: $keystore" >&2
    exit 1
fi

cd "$proyek"
echo "Proyek  : $proyek"
echo "Mode    : $mode (dua varian)"
echo "Tujuan  : $tujuan"
echo "JDK     : $JAVA_HOME (Gradle ${versi_gradle:-tidak diketahui})"

./gradlew ":app:assembleMedia3${mode^}" ":app:assembleVlc${mode^}" --no-daemon

if [ "$mode" = "release" ]; then
    simpan_pass="$(sed -n 's/^storePassword=//p' "$kredensial")"
    kunci_pass="$(sed -n 's/^keyPassword=//p' "$kredensial")"
    kunci_alias="$(sed -n 's/^keyAlias=//p' "$kredensial")"
fi

mkdir -p "$tujuan"
shopt -s nullglob
for flavor in media3 vlc; do
    apk_list=("$proyek"/app/build/outputs/apk/"$flavor"/"$mode"/*.apk)
    if [ ${#apk_list[@]} -eq 0 ]; then
        echo "APK $flavor tidak ditemukan." >&2
        exit 1
    fi
    for apk in "${apk_list[@]}"; do
        if [ "$mode" = "release" ] && [[ "$apk" == *-unsigned.apk ]]; then
            apk_isi="${apk%-unsigned.apk}.apk"
            "$apksigner" sign \
                --ks "$keystore" \
                --ks-key-alias "$kunci_alias" \
                --ks-pass "pass:$simpan_pass" \
                --key-pass "pass:$kunci_pass" \
                --v1-signing-enabled false \
                --v2-signing-enabled true \
                --v3-signing-enabled true \
                --v4-signing-enabled false \
                --out "$apk_isi" "$apk"
            rm -f "$apk"
            apk="$apk_isi"
            echo "APK $flavor ditandatangani."
        fi
        # Varian utama memakai nama berkas lama agar tautan unduhan yang sudah
        # beredar tetap berfungsi; varian vlc diberi akhiran sendiri.
        if [ "$flavor" = "media3" ]; then
            nama_berkas="File.Explorer-$mode.apk"
        else
            nama_berkas="File.Explorer-$flavor-$mode.apk"
        fi
        cp -f "$apk" "$tujuan/$nama_berkas"
        rm -f "$apk"
        echo "APK dipindah ke: $tujuan/$nama_berkas"
    done
done

rm -rf "$proyek"/*/build "$proyek"/build "$proyek"/.gradle
echo "Folder build proyek sudah dibersihkan."
