#!/bin/bash
# Test script for DarkKeyboard - run in Termux on device
# This simulates typing using input command

echo "=== DarkKeyboard Test Script ==="
echo "Make sure DarkKeyboard is the active IME"
echo ""

# Open a text field (Google Keep or similar)
am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "" 2>/dev/null

sleep 2

echo "Sending test input..."

# Type test words one by one
input text "test"
sleep 0.5
input keyevent 62  # space

input text "hola"
sleep 0.5
input keyevent 62

input text "mundo"
sleep 0.5
input keyevent 62

input text "prueba"
sleep 0.5
input keyevent 62

echo ""
echo "Done! Check logs at /sdcard/Android/data/org.dark.keyboard/files/logs/"
echo "Or share from Settings > Debug > Share Log"
