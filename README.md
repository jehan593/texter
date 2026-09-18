# Texter

[![Build APK](https://github.com/jehan593/texter/actions/workflows/build-apk.yml/badge.svg)](https://github.com/jehan593/texter/actions/workflows/build-apk.yml)
[![Latest release](https://img.shields.io/github/v/release/jehan593/texter)](https://github.com/jehan593/texter/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Texter is an Android text editor with syntax highlighting, search, and a read-only Reader mode.

> FYI: This project is fully vibe coded.

## Features

- Open text files from the file picker or another app, or create a new file.
- Choose **Save in app** to keep a file in Texter's saved list. Opening a file alone doesn't save it.
- Choose **Update original** to write back to the original file when write access is available.
- Choose **Save a copy** to save through the file picker, or **Share** to send a file to another app.
- Find text without matching letter case, and jump between results.
- Read and copy text without editing it using **Reader mode** in the three-dot menu.
- Highlight syntax in common code and configuration files.
- Use light and dark [Nord](https://www.nordtheme.com/) themes.

## Install

Download the latest APK from [Releases](https://github.com/jehan593/texter/releases/latest).
Requires Android 8.0 or newer.

## Build from source

Install JDK 17 and Android SDK 35. Set `ANDROID_HOME` to your SDK folder, or set
`sdk.dir` in `local.properties`.

```sh
./gradlew assembleDebug
./gradlew assembleRelease
```

On Windows, use `.\gradlew.bat` instead of `./gradlew`.

APKs are written to `app/build/outputs/apk/debug/` and `app/build/outputs/apk/release/`.

## Tech stack

Kotlin, Jetpack Compose, Material 3, and Room.

## License

MIT — see [`LICENSE`](LICENSE). The bundled Martian Mono Nerd Font is licensed separately under
the SIL Open Font License 1.1 — see
[`app/licenses/MARTIAN_MONO_LICENSE.txt`](app/licenses/MARTIAN_MONO_LICENSE.txt).
