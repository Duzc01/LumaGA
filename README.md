# LumaGA

Android port of [MNGA](https://github.com/BugenZhao/MNGA), an NGA (bbs.nga.cn)
client. Built with Jetpack Compose; the Rust `logic` library from MNGA is
shared via JNI as a prebuilt `liblogic.so` in `logic/src/main/jniLibs`.

## Layout

- `app/` — Compose UI, ported from the MNGA SwiftUI app.
- `logic/` — Android library module wrapping the prebuilt Rust `liblogic.so`
  plus generated protobuf Java sources.

## Build

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. JDK 17 required.

The Rust library is not rebuilt by Gradle. To regenerate it after changing
`logic/` in the MNGA checkout, cross-compile with cargo-ndk for
`arm64-v8a` / `x86_64` / `x86` and copy the artifacts into
`logic/src/main/jniLibs/`.

## CI

GitHub Actions builds the debug APK on every push/PR to `main` and uploads it
as an artifact — see [ci.yml](.github/workflows/ci.yml).
