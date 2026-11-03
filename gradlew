#!/bin/sh
# Gradle wrapper starter. CI uses the installed Gradle from setup-gradle,
# so this project does not ship a binary wrapper jar.
# For local builds, run: gradle assembleDebug
# or download the Gradle 8.7 distribution from gradle.org and use its bin/gradle.
echo "Snoah Plus: use 'gradle assembleDebug' (Gradle 8.7) or Android Studio. See README.md."
exit 1
