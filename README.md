# kmp-voice-messages

A Kotlin Multiplatform library for recording and playing back **voice messages** with two Compose Multiplatform UI components — no manual file paths to juggle for recording, no per-platform player setup.

## What is it?

`kmp-voice-messages` lets a KMP app:
- **Record** a voice message with the bundled `VoiceMessageRecorder` composable, getting the finished recording back as a `ByteArray` in a callback.
- **Play** a voice message back with the bundled `VoiceMessagePlayer` composable, given either a file path or the raw bytes.
- Decide for itself how (and whether) to persist recordings — the library has no opinion on storage, naming, or a message list.

## Supported platforms

| Platform | Recording | Playback | Notes |
|---|---|---|---|
| Android | `MediaRecorder` | `MediaPlayer` | Requires `RECORD_AUDIO` permission |
| iOS | `AVAudioRecorder` | `AVAudioPlayer` | Requires `NSMicrophoneUsageDescription` in `Info.plist` |
| Desktop (JVM) | `javax.sound.sampled` | `javax.sound.sampled` | Uses the default system audio input/output |

> Audio is recorded in each platform's native container (e.g. AAC/m4a on Android & iOS, WAV on desktop). Files are easiest to read back on the platform that recorded them; cross-platform decoding of the raw bytes is not guaranteed.

`VoiceMessageRecorder` writes to, and the `bytes`-based `VoiceMessagePlayer` overload writes a
temporary copy to, an OS-appropriate scratch/cache directory by default — there's nothing to
configure unless you want to (see [Public API](#public-api) below).

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

## Install

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.lerchenflo:kmp-voice-messages:1.0.0")
}
```

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

## Example app

The `:example` and `:example-android` modules show the library end to end: a screen with a
recorder button and a scrollable list of recorded messages you can play back, built on
`VoiceMessageRecorder` and `VoiceMessagePlayer`.

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

## Platform setup

- **Android**: add `<uses-permission android:name="android.permission.RECORD_AUDIO" />` to your app's manifest, and request the runtime permission before showing `VoiceMessageRecorder`.
- **iOS**: add `NSMicrophoneUsageDescription` to your app's `Info.plist`.
- **Desktop**: no extra setup; uses the default system audio devices.

---

This library was bootstrapped from the [JetBrains Kotlin Multiplatform library template](https://github.com/Kotlin/multiplatform-library-template), which demonstrates publishing a KMP library to [Maven Central](https://central.sonatype.com/). Note that tooling such as [backwards-compatibility tracking](https://kotlinlang.org/docs/jvm-api-guidelines-backward-compatibility.html#tools-designed-to-enforce-backward-compatibility), explicit API mode, licensing, and contribution guidelines are not yet set up — see the [Kotlin library design guidelines](https://kotlinlang.org/docs/api-guidelines-introduction.html) for best practices.

## Guide

Please find the detailed publishing guide [here](https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-publish-libraries.html).

## Other resources
* [Publishing via the Central Portal](https://central.sonatype.org/publish-ea/publish-ea-guide/)
* [Gradle Maven Publish Plugin - Publishing to Maven Central](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)
