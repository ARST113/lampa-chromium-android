# Upstream build plugin

`src/main/groovy/com/cefrium/CefriumPlugin.groovy` is unmodified Cefrium source
from tag v0.9.2, commit ac3f25368bc09113c5d46f46089a58e6de6550bb.

Source: https://codeberg.org/cefrium/cef-android/src/tag/v0.9.2/cefrium_sdk/gradle-plugin

The published 0.9.2 plugin contains Java class version 69 (Java 25), while the
upstream quickstart recommends JDK 21 and Gradle 8.9. Run 36119841887 reproduced
`Unsupported class file major version 69` during Gradle plugin instrumentation.
We compile the same small Groovy resource-generation plugin in an included build
for JVM 11 using JDK 21. The native Cefrium/Chromium AAR is still the unmodified,
checksum-verified prebuilt SDK. This does not rebuild Chromium.

Upstream licenses are included alongside this file.
