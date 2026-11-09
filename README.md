# Minecraft BXM-8-256 — MB Loader WaterFX

This repository is now a clean native build workspace for extending the MB Loader 2.12 native entry point.

## Repository cleanup

The active `main` tree contains source/build files only. APK/APKS payloads are intentionally excluded. The uploaded MB Loader package was used for local inspection and is not copied into this repository or build artifacts.

## Current build target

GitHub Actions compiles an Android `arm64-v8a` shared library named `libmods.so`. The wrapper forwards MB Loader's original `JNI_OnLoad` and its current exported `NativeMods` calls through `libmods_original.so`, then initializes the WaterFX module.

## Important implementation status

The native wrapper/build pipeline is the integration scaffold. The current `waterfx::install()` only performs a safe EGL entry-point probe. It does **not yet render screen-space reflections or refraction**. No one should install this build as a replacement launcher until the renderer pass, packaging, signing, and on-device tests are complete.

## Build

GitHub Actions installs Android NDK r28c and builds `arm64-v8a`. It verifies the ELF architecture and JNI exports, then uploads the library and SHA-256 checksum as a workflow artifact.

The resulting library is an intermediate engineering artifact, not a signed APK/APKS. Modifying an installed APK split invalidates its signature; a launcher replacement requires a signing path compatible with the currently installed MB Loader package.
