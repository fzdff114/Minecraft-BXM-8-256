# Minecraft BXM-8-256 — MB Loader Water Reflection Renderer

Clean rebuild of the previous compatibility project.

## Goal
Add a standalone water-rendering module to the existing MB Loader native-mod loading path, alongside DynamicShadows and CustomBuffers.

Target:
- Fancy graphics mode
- screen-space water reflections
- water refraction/distortion
- animated wave distortion
- resource-pack independent detection
- automatic fallback when the renderer/API is unsupported

The module is implemented as a native wrapper around the existing `libmods.so` entry points. It forwards the existing DynamicShadows/CustomBuffers/DOF JNI calls and installs the water post-process hook.

## Build
GitHub Actions builds the Android arm64-v8a native module with the Android NDK.

The final APK/APKS patching step must be run against the exact MB Loader 2.12 package being used on the device. No Minecraft assets are bundled into this repository.
