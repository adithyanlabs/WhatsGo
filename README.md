# WhatsGo

**A native Android WhatsApp client powered by Go and whatsmeow.**

WhatsGo is an Android application built with Kotlin and Jetpack Compose, powered by a Go-based messaging engine using [whatsmeow](https://github.com/tulir/whatsmeow).

The project combines Go's performance and messaging capabilities with a modern Android interface built using Material 3.

> **Status:** Alpha — actively under development. Expect bugs, incomplete features, and breaking changes.

## Features

- **WhatsApp Integration** — Connect using the whatsmeow library.
- **QR Code & Pairing** — Link your WhatsApp account.
- **History Synchronization** — Synchronize chats and messages after pairing.
- **Background Sync** — Handle synchronization through an Android foreground service.
- **Native Android UI** — Built with Kotlin and Jetpack Compose.
- **Material 3 Design** — Modern Android interface.
- **Go Engine** — Messaging functionality powered by Go and gomobile.

## Tech Stack

| Component | Technology |
|---|---|
| Android | Kotlin |
| UI | Jetpack Compose |
| Design | Material 3 |
| Messaging Engine | Go |
| WhatsApp Library | whatsmeow |
| Android-Go Integration | gomobile |
| Database | SQLite |
| Build System | Gradle |

## Project Structure

```text
WhatsGo/
├── app/                  # Android application
│   ├── src/
│   └── libs/             # Go Android library (AAR)
├── native/               # Go messaging engine
├── gradle/
├── build.gradle.kts
└── settings.gradle.kts
```

## Getting Started

### Requirements

- Android Studio
- Android SDK
- JDK compatible with the project Gradle configuration
- Go
- gomobile

### Build

Clone the repository:

```bash
git clone https://github.com/adithyanlabs/WhatsGo.git
cd WhatsGo
```

Build the debug APK:

```bash
./gradlew assembleDebug
```

Build the Go Android library and signed release APK locally:

```bash
cd go
GOTOOLCHAIN=local go build ./...
GOTOOLCHAIN=local gomobile bind \
  -v \
  -target=android/arm64 \
  -androidapi 24 \
  -ldflags="-s -w" \
  -o ../app/libs/engine.aar \
  .
cd ..
./gradlew assembleRelease
```

Release signing uses the local `keystore.properties` and `app/release-key.jks` files. Keep both out of Git.

The release APK will be generated at:

```text
app/build/outputs/apk/release/app-release.apk
```

## Releases

You can find available builds on the [GitHub Releases](https://github.com/adithyanlabs/WhatsGo/releases) page.

WhatsGo is currently in alpha, so releases are intended primarily for testing.

### Automated builds and manual releases

GitHub Actions builds a debug APK and Go Android library for pushes and pull requests to `main`. Each build is available as a temporary workflow artifact for 14 days. These builds do not use the release signing key or publish releases.

To publish a release after grouping several commits, update `versionName` and increment `versionCode` in `app/build.gradle.kts`. Build the signed APK locally, push the source commit and matching version tag, then create the GitHub Release manually and attach `app/build/outputs/apk/release/app-release.apk`.


## Disclaimer

WhatsGo is an independent, unofficial project and is not affiliated with, endorsed by, or associated with WhatsApp or Meta.

This project uses [whatsmeow](https://github.com/tulir/whatsmeow), an unofficial WhatsApp Web API implementation.

## License

See the [LICENSE](LICENSE) file for licensing information.

---

**Built with Go and Kotlin by [Adithyan](https://github.com/adithyanlabs).**
