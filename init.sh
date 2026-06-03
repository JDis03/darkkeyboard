#!/bin/bash
set -e

echo "=== Harness Initialization ==="

echo "=== ./gradlew test ==="
./gradlew test

echo "=== Verification Complete ==="
echo ""
echo "Next steps:"
echo "1. Read feature_list.json to see current feature state"
echo "2. Pick ONE unfinished feature to work on"
echo "3. Implement only that feature"
echo "4. Re-run verification before claiming done"
