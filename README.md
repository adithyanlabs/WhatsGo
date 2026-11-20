# WhatsGo

WhatsGo is an experimental Android messaging client built with Kotlin, Jetpack Compose, and a Go messaging engine powered by [whatsmeow](https://github.com/tulir/whatsmeow).

> **Status: Alpha.** The app is under active development. Features may be incomplete, and updates may include breaking changes.

## Features

- Link an account using a QR code
- View chats and messages
- Send messages and media
- View status updates
- Sync chat history in the background
- Browse group information

## Technology

- **Android app:** Kotlin, Jetpack Compose, Material 3
- **Messaging engine:** Go and whatsmeow
- **Go-to-Android bridge:** gomobile
- **Local storage:** SQLite and Room
- **Build system:** Gradle

## Download

For stable builds, see [GitHub Releases](https://github.com/adithyanlabs/WhatsGo/releases).

The [WhatsGo Nightly release](https://github.com/adithyanlabs/WhatsGo/releases/tag/nightly) is rebuilt on every push to `main`. It keeps a single release and replaces the APK asset after each successful build. You can also run the workflow manually from [GitHub Actions](https://github.com/adithyanlabs/WhatsGo/actions).

Nightly builds are for testing and may contain unfinished changes.

## Build from source

### Requirements

- JDK 17
- Android SDK and NDK
- Go 1.26.5
- gomobile

Clone the repository:

```bash
git clone https://github.com/adithyanlabs/WhatsGo.git
cd WhatsGo
```

Build the Go Android library:

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
```

Build a debug APK:

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

Build a release APK:

```bash
./gradlew assembleRelease
```

The APK will be at `app/build/outputs/apk/release/app-release.apk`.

## Project layout

```text
app/    Android application
go/     Go messaging engine
gradle/ Gradle wrapper and version catalog
```

## Disclaimer

WhatsGo is an independent, unofficial project. It is not affiliated with, endorsed by, or associated with WhatsApp or Meta. It uses [whatsmeow](https://github.com/tulir/whatsmeow), an unofficial WhatsApp Web API implementation.

## License

See [LICENSE](LICENSE).
