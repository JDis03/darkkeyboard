#!/bin/bash
# Watch DarkKeyboard logs in real-time
# Run this in Termux while typing with DarkKeyboard

echo "=== DarkKeyboard Log Watcher ==="
echo "Filtering for: autocorrect, isEnabled, composing"
echo "Press Ctrl+C to stop"
echo ""

logcat -c  # Clear old logs
logcat DarkIME:V *:S | grep -E "autocorrect|isEnabled|composing|Autocorrect|direct commit"
