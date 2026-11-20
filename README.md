# kmp-voice-messages

[![JitPack](https://jitpack.io/v/lerchenflo/kmp-voice-messages.svg)](https://jitpack.io/#lerchenflo/kmp-voice-messages)

A Kotlin Multiplatform library for recording and playing back **voice messages** with two Compose Multiplatform UI components — no manual file paths to juggle for recording, no per-platform player setup.

## What is it?

`kmp-voice-messages` lets a KMP app:
- **Record** a voice message with the bundled `VoiceMessageRecorder` composable, getting the finished recording back as a `ByteArray` in a callback.
- **Play** a voice message back with the bundled `VoiceMessagePlayer` composable, given either a file path or the raw bytes.
- Build its own UI on top of the lower-level `VoiceRecorder` / `VoicePlayer` classes instead.
- Decide for itself how (and whether) to persist recordings — the library has no opinion on storage, naming, or a message list.

## Supported platforms

| Platform | Recording | Playback | Notes |
|---|---|---|---|
| Android | `MediaRecorder` | `MediaPlayer` | Requires `RECORD_AUDIO` permission |
| iOS | `AVAudioRecorder` | `AVAudioPlayer` | Requires `NSMicrophoneUsageDescription` in `Info.plist` |
| Desktop (JVM) | `javax.sound.sampled` | `javax.sound.sampled`, AAC through [JAAD](https://github.com/sfuhrm/JAADec) | Uses the default system audio input/output |

Each platform records into its own container:

| Platform | Format | Size |
|---|---|---|
| Android | AAC in MP4 (`.m4a`), 16 kHz mono, 64 kbps | about 8 kB per second |
| iOS | AAC in MP4 (`.m4a`) | depends on the device settings |
| Desktop | WAV, 8 kHz mono, 16-bit | 16 kB per second (2 minutes are about 1.9 MB) |

**Every platform plays every format.** The desktop decodes the AAC files from Android and iOS
with the pure-Java decoder JAAD (public domain, desktop only); Android and iOS play the desktop's
WAV files with their system players. The player reads the format from the file header, not from
the file name.

`VoiceMessageRecorder` writes to, and the `bytes`-based `VoiceMessagePlayer` overload writes a
temporary copy to, an OS-appropriate scratch/cache directory by default — there's nothing to
configure unless you want to (see [Public API](#public-api) below).

## Requirements

| Item                  | Version / value                                                     |
|-----------------------|---------------------------------------------------------------------|
| Kotlin                | 2.4.20 or newer                                                     |
| Compose Multiplatform | 1.12.1 or newer                                                     |
| material3             | `org.jetbrains.compose.material3:material3` 1.12.0-alpha03 or newer |
| Android               | minSdk 24, compileSdk 37                                            |
| iOS                   | `iosArm64`, `iosSimulatorArm64` (no `iosX64`)                       |
| Desktop               | JVM 25 or newer                                                     |

The library is built against the lowest versions above on purpose. Gradle always picks the highest
version of a dependency in your app, so the library never upgrades Compose or material3 in your
app behind your back.

## Installation

The library is published on [JitPack](https://jitpack.io/#lerchenflo/kmp-voice-messages). JitPack
builds it straight from a git tag of this repository, so there is no Maven Central account or signing.

**1. Add the JitPack repository** in `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

**2. Add the version** to `gradle/libs.versions.toml`. The latest version is `1.0.2`, see the
[changelog](#changelog) and the [tags page](https://github.com/lerchenflo/kmp-voice-messages/tags):

```toml
[versions]
kmpVoiceMessages = "1.0.2"

[libraries]
kmp-voice-messages = { module = "com.github.lerchenflo.kmp-voice-messages:kmp-voice-messages", version.ref = "kmpVoiceMessages" }
```

Use exactly this coordinate. JitPack's short form `com.github.lerchenflo:kmp-voice-messages` pulls
the Android, iOS and JVM artifacts all at once and breaks Kotlin Multiplatform dependency
resolution ("No matching variant ... kmp-voice-messages-android").

**3. Add the dependency** to the module that holds your UI (for example `composeApp` or `shared`):

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kmp.voice.messages)
        }
    }
}
```

All public API lives in the package `io.github.lerchenflo.voicemessages` (the composables in
`io.github.lerchenflo.voicemessages.ui`).

## Public API

```kotlin
// Records a voice message; delivers the finished recording's bytes to onRecorded.
@Composable
fun VoiceMessageRecorder(
    modifier: Modifier = Modifier,
    onRecorded: (bytes: ByteArray) -> Unit,
)

// Plays back a voice message already saved at filePath.
@Composable
fun VoiceMessagePlayer(filePath: String, modifier: Modifier = Modifier)

// Plays back a voice message held in memory, writing it to filePath first
// (a random scratch file by default — pass your own to control where it lands).
@Composable
fun VoiceMessagePlayer(bytes: ByteArray, filePath: String? = null, modifier: Modifier = Modifier)
```

Both composables come with their own self-contained UI: `VoiceMessageRecorder` shows a
record/stop button with elapsed time while recording; `VoiceMessagePlayer` shows a play/pause
button, a progress bar, and the current position alongside the full length (e.g. `0:05 / 1:23`).

**`VoiceMessagePlayer` only reads metadata up front.** As soon as it's composed, it reads just
the audio file's duration from its header — not the audio itself — so the full length shows
immediately even in a long list of messages. The audio is only loaded and decoded once the user
taps play.

### Building your own UI

The composables are built on two plain classes and one function, which you can use directly:

```kotlin
// Records into a file. On Android request RECORD_AUDIO before start().
class VoiceRecorder() {
    val isRecording: Boolean
    suspend fun start(filePath: String)
    fun stop()
}

// Plays a file. Poll positionMs / isPlaying / isPaused for a progress bar.
class VoicePlayer() {
    val positionMs: Long
    val durationMs: Long
    val isPlaying: Boolean
    // Loaded and paused (by pause() or by another app taking the audio): resume() continues.
    // False while playing, after the end, after stop() and before the first play().
    val isPaused: Boolean
    fun play(filePath: String)   // throws IllegalStateException if another app holds the audio (phone call)
    fun pause()
    fun resume()                 // same exception; the message then stays paused
    fun seekTo(positionMs: Long)
    fun stop()
}

// Length of a voice message file in milliseconds, 0 if it can't be read. Blocking file I/O.
fun voiceMessageDurationMs(filePath: String): Long
```

**Other apps' audio.** While a voice message plays, music and podcasts of other apps pause, and
they continue once the voice message is paused, stopped, finished or fails to play. When another
app takes the audio (a phone call, a video, a navigation prompt, another music app), the voice
message pauses: `isPlaying` turns false and `isPaused` turns true, and it stays paused until
`resume()`. So when you poll `isPlaying` to detect the end of a message, check `isPaused` too:
`!isPlaying && !isPaused` means finished or stopped.

- Android: transient audio focus (other apps pause, no ducking), audio marked as speech on the
  media stream. The library's manifest registers a small `ContentProvider`
  (`VoiceMessagesInitProvider`) that hands the application context to `VoicePlayer`, so
  `VoicePlayer()` needs no `Context`. Nothing to set up in the app.
- iOS: the shared `AVAudioSession` with category Playback.
- Desktop: no audio focus; `isPaused` only turns true through `pause()`.

## Usage

### Record, then play it straight back from memory

The simplest case: keep the recorded bytes around and hand them straight to
`VoiceMessagePlayer` — no file management of your own required.

```kotlin
@Composable
fun MyScreen() {
    var lastRecording by remember { mutableStateOf<ByteArray?>(null) }

    VoiceMessageRecorder(onRecorded = { bytes -> lastRecording = bytes })

    lastRecording?.let { bytes ->
        VoiceMessagePlayer(bytes = bytes)
    }
}
```

### A scrollable list of recordings

The pattern the `:example` module uses: collect recordings as they come in and show each one
in a `LazyColumn`. Each `VoiceMessagePlayer` manages its own play/pause state independently.

```kotlin
@Composable
fun VoiceMessagesScreen() {
    val messages = remember { mutableStateListOf<ByteArray>() }

    VoiceMessageRecorder(onRecorded = { bytes -> messages.add(bytes) })

    LazyColumn {
        items(messages) { bytes ->
            VoiceMessagePlayer(bytes = bytes, modifier = Modifier.fillMaxWidth())
        }
    }
}
```

### Playing back a file you already saved yourself

If you persist recordings yourself (e.g. in a database row, or a file your app manages), use
the `filePath` overload directly — no need to round-trip through bytes at all.

```kotlin
@Composable
fun SavedMessageRow(savedFilePath: String) {
    VoiceMessagePlayer(filePath = savedFilePath)
}
```

### Choosing exactly where a recording's bytes get written

By default, the `bytes` overload writes to a randomly named scratch file that's deleted once
the composable leaves composition. Pass your own `filePath` to control where it's written and
to keep the file around afterwards — handy if you want to persist the recording permanently
once the user is happy with it.

```kotlin
@Composable
fun MyScreen(voiceMessagesDir: String) {
    var recording by remember { mutableStateOf<Pair<ByteArray, String>?>(null) }

    VoiceMessageRecorder(onRecorded = { bytes ->
        recording = bytes to "$voiceMessagesDir/greeting.tmp"
    })

    recording?.let { (bytes, path) ->
        VoiceMessagePlayer(bytes = bytes, filePath = path)
    }
}
```

## Platform setup

- **Android**: add `<uses-permission android:name="android.permission.RECORD_AUDIO" />` to your app's manifest, and request the runtime permission before showing `VoiceMessageRecorder`.
- **iOS**: add `NSMicrophoneUsageDescription` to your app's `Info.plist`.
- **Desktop**: uses the default system audio devices. A packaged macOS app needs
  `NSMicrophoneUsageDescription` in its bundle, otherwise macOS blocks the microphone. With
  Compose Desktop:

  ```kotlin
  compose.desktop {
      application {
          nativeDistributions {
              macOS {
                  infoPlist {
                      extraKeysRawXml = """
                          <key>NSMicrophoneUsageDescription</key>
                          <string>Records voice messages.</string>
                      """.trimIndent()
                  }
              }
          }
      }
  }
  ```

## Example app

The `:example` and `:example-android` modules show the library end to end: a screen with a
recorder button and a scrollable list of recorded messages you can play back, built on
`VoiceMessageRecorder` and `VoiceMessagePlayer`. Both always use the library source of this
repository, so changes can be tried before a release.

- `:example` is a Kotlin Multiplatform module with the shared screen (`VoiceMessagesExampleScreen`)
  plus a desktop entry point. Run it with:

  ```
  ./gradlew :example:run
  ```

- `:example-android` is a plain Android app module that hosts the same shared screen in an
  `Activity`. Build and install it with:

  ```
  ./gradlew :example-android:installDebug
  ```

  (`:example-android` is a separate module because AGP 9 no longer allows the
  `org.jetbrains.kotlin.multiplatform` plugin and the Android Application plugin in the same
  Gradle module.)

## Testing library changes in your app before a release

Use a Gradle composite build. It replaces the JitPack dependency with the local library source, so
no release is needed. In the **app's** `settings.gradle.kts` (with this repository checked out next
to the app):

```kotlin
includeBuild("../kmp-voice-messages") {
    dependencySubstitution {
        substitute(module("com.github.lerchenflo.kmp-voice-messages:kmp-voice-messages")).using(project(":library"))
    }
}
```

Sync Gradle, and the app builds against your local changes. Remove the block before you commit.

## Releasing a new version

1. Try the change in the example apps (and, if needed, in an app with the composite build above).
2. Commit and push to `main`.
3. Create a git tag with the new version and push it, for example:
   ```bash
   git tag 1.0.2
   git push origin 1.0.2
   ```
4. Open `https://jitpack.io/#lerchenflo/kmp-voice-messages`, click **Get it** next to the tag, and
   wait for the build. JitPack also builds a tag the first time an app asks for it, but then that
   first Gradle sync waits for the build.
5. Add the version to the [changelog](#changelog) and update the version in the
   [installation](#installation) section.
6. Raise the version in the apps' `libs.versions.toml`.

JitPack rewrites the Gradle module metadata and drops the classifier from every file name
(`x-1.0.0-metadata.jar` becomes `x-1.0.0.jar`). `library/build.gradle.kts` therefore publishes the
iOS metadata jar and the iOS resources zip a second time without classifier. Without this, the
Android Studio sync of apps fails with `Could not find kmp-voice-messages-iosarm64-<version>.jar`.
For the same reason the sources jars are not variants in the module metadata (the rewrite would
point them to the classes jar). They are published as plain Maven `-sources.jar` files, which the
IDE finds by classifier, so it shows the KDoc on hover. Keep both when changing the build.

How the JitPack build works: `jitpack.yml` tells JitPack to run
`./gradlew :library:publishToMavenLocal -Pversion=<tag>` with JDK 25 on Linux. The example apps are
not built. Kotlin cross-compiles the iOS libraries on Linux, so iOS works without a Mac. A tag that
was built cannot be rebuilt with different code: fix problems with a new tag. Any commit can be
tried without a tag by using its short hash as version, for example
`com.github.lerchenflo.kmp-voice-messages:kmp-voice-messages:a1b2c3d`.

## Changelog

### 1.0.2

- Android takes transient audio focus while a voice message plays, so other apps' music pauses
  (no ducking) and continues once the voice message is paused, stopped, finished or fails. The
  audio is marked as speech on the media stream.
- Android: when another app takes the audio (call, video, navigation prompt, other music app),
  the voice message pauses and stays paused until `resume()`.
- New `VoicePlayer.isPaused` on all platforms: true while a voice message is loaded and paused (by
  `pause()`, or on Android and iOS by another app taking the audio), false while playing, after the
  end and after `stop()`. Tells a pause apart from the end, since `isPlaying` is false for both.
- Android `play()` / `resume()` throw `IllegalStateException` when the audio focus is denied (for
  example during a phone call), like iOS does when the audio session can't be activated.
- Android `play()` releases the `MediaPlayer` when the file can't be prepared.
- `VoiceMessagePlayer` continues a paused voice message from where it stopped instead of starting
  it again from the beginning.

### 1.0.1

- Built for Java 25 (the desktop part needs JVM 25 or newer). Same code as 1.0.0.

### 1.0.0

- Desktop plays the AAC (`.m4a`) voice messages recorded on Android and iOS (pure-Java decoder JAAD,
  desktop only), and reads their length.
- Desktop records 8 kHz mono WAV, so a 2-minute message is about 1.9 MB.
- Desktop player: `isPlaying` is true right after `play()` (Java Sound starts the line a few
  milliseconds late) and turns false at the end of the audio.
- `VoicePlayer` and `VoiceRecorder` are public, `VoicePlayer` got `resume()` and `seekTo()`.
- New `voiceMessageDurationMs(filePath)`.
- Android records with `VOICE_COMMUNICATION` (echo cancellation, noise suppression), 16 kHz mono,
  64 kbps.
- iOS player and recorder fixes.
- Published on JitPack as `com.github.lerchenflo.kmp-voice-messages:kmp-voice-messages`, built for
  JVM 11, against Kotlin 2.4.20 / Compose Multiplatform 1.12.1 / material3 1.12.0-alpha03.

### 1.0.0-alpha01

- First version.
