#!/usr/bin/env bash
# Creates the android/ and ios/ folders around the Dart code in lib/. Run once, from this folder.
# Needs Flutter installed (https://docs.flutter.dev/get-started/install).
set -euo pipefail

ORG="${1:-com.example}"          # your reverse domain, for example ke.co.yourcompany
flutter create --platforms=android,ios --org "$ORG" --project-name mpesa_companion .
flutter pub get

echo
echo "Next: apply the snippets in docs/PLATFORM_SETUP.md (permissions, deep links, Face ID text)."
echo "The Android package and iOS bundle id are now: ${ORG}.mpesa_companion"
echo "Put the same Android package in COMPANION_ANDROID_PACKAGE on companion-api."
