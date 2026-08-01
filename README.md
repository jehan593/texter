# Texter

[![Build APK](https://github.com/jehan593/texter/actions/workflows/build-apk.yml/badge.svg)](https://github.com/jehan593/texter/actions/workflows/build-apk.yml)
[![Latest release](https://img.shields.io/github/v/release/jehan593/texter)](https://github.com/jehan593/texter/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Texter is a simple Android text editor — open and edit any text-based file, with lightweight
syntax highlighting and in-document search.

## Features

- **Open anything** — pick a file via the built-in picker, or share/open one into Texter from any
  other app (file managers, etc.), regardless of how that app tags the file's type.
- **Saved documents** — opening a file copies it into an app-owned saved list rather than editing
  the original in place, since many "Open with" grants from other apps are read-only or don't
  survive past that one intent. From the saved list: reopen, edit, delete.
- **Update original / save to local storage / share** — write changes straight back to the
  original file when it was opened with write access, save a copy to any location on the device,
  or share the text directly with another app.
- **Search** — always case-insensitive, matches whole words or parts, with up/down navigation
  between multiple results.
- **Syntax highlighting** — lightweight, regex-based highlighting for common code/config files.
- **Nord theme** — dark/light color schemes built on the [Nord](https://www.nordtheme.com/)
  palette, with Martian Mono Nerd Font for the app's UI (the text you're editing uses the
  system monospace font instead, for full Unicode coverage).

## Install

Grab the latest APK from [Releases](https://github.com/jehan593/texter/releases/latest).

## Building from source

```sh
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk (minified, resource-shrunk)
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
```

Requires an Android SDK referenced via `local.properties` (`sdk.dir=...`). `compileSdk` 35,
`minSdk` 26.

## Tech stack

Kotlin, Jetpack Compose, Material 3, Room. Manual dependency injection (no DI framework) — same
conventions as this repo's sibling apps ([linker](https://github.com/jehan593/linker),
[noter](https://github.com/jehan593/noter)).

## License

MIT — see [`LICENSE`](LICENSE). The bundled Martian Mono Nerd Font is licensed separately under
the SIL Open Font License 1.1 — see
[`app/licenses/MARTIAN_MONO_LICENSE.txt`](app/licenses/MARTIAN_MONO_LICENSE.txt).
