# Minecraft-BXM-8-256

**Honor X70i / PowerVR BXM-8-256 — Minecraft Bedrock native Vibrant Visuals compatibility research.**

This repository contains the Android Studio project for **X70i VV Compatibility Assistant 0.2.2-alpha.5**. It is a diagnostic and per-app ANGLE preference helper, not a modified Minecraft APK or a GPU driver.

## Device under investigation

- Device: HONOR X70i (ABR-AN00)
- SoC: MediaTek Dimensity 7025 / MT6855
- GPU: Imagination PowerVR BXM-8-256
- Android: 16 / MagicOS 10
- Reported GLES: OpenGL ES 3.2
- Driver shown by AIDA64: 25.1@6715691

## What the assistant does

- Reads device, Vulkan feature declarations, and its own GLES renderer string.
- Checks likely ANGLE APK package paths individually (avoids package-manager all-user enumeration that caused a user-100 SecurityException on this ROM).
- Reads Android's per-app ANGLE settings.
- With Shizuku shell permission, can set **only the selected game's** ANGLE preference and restore the previous value.
- Runs GLES 3 shader compile/link plus RGBA8, RGBA16F, sRGB and Depth24Stencil8 framebuffer checks in the helper process; reports EGL/GLES version, extensions, texture/renderbuffer limits and sample count.
- Uses the MB Loader host package for Android driver preferences, PID inspection, startup capture and launch. Minecraft's installed APK is treated as the source of DEX/native libraries rather than the running Android process. Continuous Shizuku logcat capture tracks the host PID and starts a second unfiltered `logcat --pid=<PID>` stream to retain all messages emitted by the MB Loader/MinecraftActivity process, alongside system graphics events.
- Shows Minecraft PID/process rows and collects filtered local ANGLE / EGL / Vulkan / PowerVR / RenderDragon / shader / framebuffer logs.
- Adds a per-app clear operation that removes the Minecraft-only forced driver row and returns the package to the Android/MagicOS default driver rule; other apps' rows are preserved.\n- Scans package names containing ANGLE / OpenGL / GPU driver / graphics driver for the current Android user only, explicitly passing the current user ID to avoid cross-user enumeration errors.
- Does not enable global ANGLE-for-all, modify Minecraft files, touch worlds, or upload logs.

## Important limitations

This helper cannot add GPU features or repair a vendor driver. Seeing an ANGLE package is only a preliminary check; it does not prove the ROM can load ANGLE for Minecraft. ANGLE only intercepts OpenGL ES paths. If the Minecraft build uses Vulkan directly for its relevant RenderDragon path, changing the OpenGL driver preference will not affect that path. The new GLES framebuffer/shader tests run inside the helper app, not Minecraft; a passing result does not guarantee the game's deferred renderer will work.

The ANGLE route is optional and should only be tested when the ROM exposes a working ANGLE provider. The app saves the game's previous setting and offers a per-package restore. **Do not enable the global `angle_gl_driver_all_angle` setting.**

The original Minecraft 26.60 APK splits are not included here. No custom RenderDragon pass or modified Minecraft APK is claimed.

## Source project

Download the Android Studio project archive:

[**下载完整 Android Studio 源码 ZIP**](https://github.com/fzdff114/Minecraft-BXM-8-256/archive/refs/heads/main.zip)

Open the `X70iVVCompat` folder in Android Studio.

## Build

GitHub Actions builds a debug-signed APK for testing on pushes to `main` and on manual workflow dispatch. The APK is uploaded as the `X70i-VV-Compat-Debug-APK` workflow artifact.

A successful build proves that the app compiles; it does **not** prove Vibrant Visuals works on the X70i. Device validation is still required.

## Setup

1. Install and start Shizuku using its Wireless debugging flow.
2. Install the debug APK.
3. Grant the app Shizuku permission.
4. Set the package name of the process that actually renders the Minecraft 3D world (default: `com.mojang.minecraftpe`). If MB Loader launches an isolated/clone instance, use that instance's package name instead.
5. Keep the MB Loader launcher package separate (default: `io.github.bambosan.mbloader`).
6. Run detection first. If an old `angle` override exists but no provider is installed, use **Clear Minecraft ANGLE override** to remove only the Minecraft row and return the package to the system default. Do not select ANGLE without a provider.
7. For Minecraft 26.62 startup transitions, use the MB Loader host package (`io.github.bambosan.mbloader`) as the target for driver settings and process capture. Tap **开始记录启动过程并打开 MB Loader** before launching the game, reproduce the fallback, then stop and copy the log. Reproduce the black-screen-to-Fancy fallback, return to this assistant, then tap **停止监视并读取启动日志**. Share the captured output.

Shizuku: https://shizuku.rikka.app/download/

## Privacy

The Android app has no Internet permission. Device details and collected logs stay local unless the user manually copies and shares them.
