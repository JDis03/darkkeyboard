#!/bin/bash
# sync_logs.sh - Pull logs from device to local logs/ folder
ADB="${ANDROID_HOME:-/home/dark/Android/Sdk/platform-tools}/adb"
PKG="org.dark.keyboard"
LOCAL_DIR="$(dirname "$0")/logs"

mkdir -p "$LOCAL_DIR"

echo "📥 Pulling logs from device..."
$ADB shell "run-as $PKG ls /data/data/$PKG/files/logs/ 2>/dev/null || ls /storage/emulated/0/Android/data/$PKG/files/logs/ 2>/dev/null" | while read -r file; do
    if [ -n "$file" ]; then
        echo "  → $file"
        $ADB shell "run-as $PKG cat /data/data/$PKG/files/logs/$file" > "$LOCAL_DIR/$file" 2>/dev/null || \
        $ADB pull "/storage/emulated/0/Android/data/$PKG/files/logs/$file" "$LOCAL_DIR/$file" 2>/dev/null
    fi
done

echo "✅ Logs synced to $LOCAL_DIR/"
ls -lh "$LOCAL_DIR/"
