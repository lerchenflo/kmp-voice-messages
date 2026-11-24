# kmp-voice-messages

[![JitPack](https://jitpack.io/v/lerchenflo/kmp-voice-messages.svg)](https://jitpack.io/#lerchenflo/kmp-voice-messages)

A Kotlin Multiplatform library for recording and playing back **voice messages** with two Compose Multiplatform UI components — no manual file paths to juggle for recording, no per-platform player setup.

## What is it?

`kmp-voice-messages` gives a KMP chat app everything for voice messages:
- **Files**: `VoiceMessageFiles` stores, finds and deletes the voice files by bare file name.
- **Playback**: `VoicePlaybackController` plays one voice message at a time (keyed by message id)
  and publishes its progress as a `StateFlow`; it lives in a ViewModel.
- **Recording**: `VoiceRecordingController` records into a new file, counts the time, stops at a
  maximum length, keeps the unsent draft and provides live microphone levels.
- **UI**: `VoiceMessagePlayer` (play/pause, slider, time) for a message bubble and
  `VoiceMessageRecordingBar` (live bars while recording, draft player afterwards) for the input row.
  Colours from `MaterialTheme.colorScheme`, every text a parameter.
- The plain `VoiceRecorder` / `VoicePlayer` classes underneath, for your own UI.

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

## Requirements

| Item                  | Version / value                                                     |
|-----------------------|---------------------------------------------------------------------|
| Kotlin                | 2.4.20 or newer                                                     |
| Compose Multiplatform | 1.12.2 or newer                                                     |
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

**2. Add the version** to `gradle/libs.versions.toml`. The latest version is `1.1.1`, see the
[changelog](#changelog) and the [tags page](https://github.com/lerchenflo/kmp-voice-messages/tags):

```toml
[versions]
kmpVoiceMessages = "1.1.1"

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

Everything lives in `io.github.lerchenflo.voicemessages`, the composables in
`io.github.lerchenflo.voicemessages.ui`. A chat screen needs three pieces: `VoiceMessageFiles`
for the files, `VoicePlaybackController` and `VoiceRecordingController` in the ViewModel, and the
composables `VoiceMessagePlayer` (message bubble) and `VoiceMessageRecordingBar` (input row).

```kotlin
// Voice files of the app in one folder, addressed by bare file name (an old absolute path also works)
class VoiceMessageFiles(val directory: String, val fileNameSuffix: String = "_voicemsg.m4a") {
    companion object {
        // Android: Context.filesDir, iOS: Documents, desktop: app data folder <desktopAppName>
        fun appStorage(desktopAppName: String, fileNameSuffix: String = "_voicemsg.m4a"): VoiceMessageFiles
    }
    fun path(fileName: String): String
    fun exists(fileName: String): Boolean
    fun newFileName(): String                                   // "<epoch ms>_voicemsg.m4a"
    suspend fun save(fileName: String, bytes: ByteArray): String // returns the absolute path
    suspend fun readBytes(fileName: String): ByteArray           // FileMissing if absent
    suspend fun delete(fileName: String): Boolean
    suspend fun deleteAll(): Int                                 // files ending with fileNameSuffix
    suspend fun durationMs(fileName: String): Long               // 0 if unreadable
}

// One voice message at a time, keyed by id. Lives in a ViewModel, no Compose needed.
class VoicePlaybackController(pollIntervalMs: Long = 100) {
    val state: StateFlow<VoicePlaybackState>
    fun play(id: String, filePath: String): Result<Unit> // resumes the paused id, else starts it
    fun pause()
    fun seekTo(id: String, positionMs: Long)              // another id: stops the current one, selects id paused there
    fun stop()
    fun release()                                         // ViewModel.onCleared
}
data class VoicePlaybackState(val id: String?, val isPlaying: Boolean, val positionMs: Long, val durationMs: Long)

// One recording at a time into VoiceMessageFiles; stops by itself at maxDurationMs.
class VoiceRecordingController(
    files: VoiceMessageFiles,
    playback: VoicePlaybackController? = null,  // stopped before a recording starts
    val maxDurationMs: Long = 120_000,
    levelCount: Int = 120,
) {
    val state: StateFlow<VoiceRecordingState>   // Idle, Recording(fileName, filePath, elapsedMs), Recorded(fileName, filePath, durationMs)
    val levels: StateFlow<List<Float>>          // live mic levels 0..1, oldest first, every ~50 ms
    suspend fun start(): Result<VoiceRecordingState.Recording> // ask for the mic permission first
    suspend fun stop(): Result<VoiceRecordingState.Recorded>   // length measured from the file
    suspend fun discard(): Result<Unit>                        // stops and deletes the file
    suspend fun clear()                                        // forgets the draft after sending (file stays)
    fun release()
    companion object { const val DRAFT_PLAYBACK_ID: String }   // playback id of the unsent draft
}

// Failures, as Result.failure or thrown by VoicePlayer / VoiceRecorder. All are IllegalStateExceptions.
sealed class VoiceMessageException {
    class AudioBusy   // another app holds the audio, e.g. a phone call: nothing plays or records
    class FileMissing(val filePath: String)
    class Failed      // anything else, see cause
}

// Message bubble: play/pause, slider (drag pauses, release seeks), time "m:ss/\nm:ss"
@Composable
fun VoiceMessagePlayer(
    id: String, filePath: String,
    playback: StateFlow<VoicePlaybackState>,
    texts: VoiceMessagePlayerTexts,                 // play, pause, position (screen reader texts)
    onPlay: () -> Unit, onPause: () -> Unit, onSeek: (positionMs: Long) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant, // unplayed track: same at 30 %
    durationMs: Long = 0,                          // 0 reads the length from the file
    formatTime: (Long) -> String = ::formatVoiceMessageTime,
)

// Input row: dot, elapsed time, live bars, countdown after 80 %; the draft player once stopped;
// delete button. Shows nothing while Idle. The record/stop/send button stays in the app.
@Composable
fun VoiceMessageRecordingBar(
    state: VoiceRecordingState,
    levels: StateFlow<List<Float>>,
    playback: StateFlow<VoicePlaybackState>,
    maxDurationMs: Long,
    texts: VoiceMessageRecordingBarTexts,          // recording, discard, player texts
    onPlay: () -> Unit, onPause: () -> Unit, onSeek: (positionMs: Long) -> Unit, onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
    formatTime: (Long) -> String = ::formatVoiceMessageTime,
)

// The live bars alone
@Composable
fun VoiceMessageLevelBars(levels: StateFlow<List<Float>>, color: Color, modifier: Modifier = Modifier)

fun formatVoiceMessageTime(ms: Long): String   // "m:ss"
```

Colours come only from `MaterialTheme.colorScheme` (`primaryContainer`, `primary`, `error`,
`onSurface`, and the `color` you pass), so the app's themes apply. The library has no
translations: every screen reader text is a parameter.

### Low-level classes

The controllers are built on two plain classes, which you can use directly:

```kotlin
// Records into a file. On Android request RECORD_AUDIO before start().
class VoiceRecorder() {
    val isRecording: Boolean
    // Mic level while recording, 0 (silence) to 1 (loud), 0 when not recording. Poll every 50-100 ms.
    val inputLevel: Float
    suspend fun start(filePath: String)   // throws VoiceMessageException.AudioBusy if another app holds the audio
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
    fun play(filePath: String, startPositionMs: Long = 0) // throws VoiceMessageException.AudioBusy
    fun pause()
    fun resume()                 // same exception; the message then stays paused
    fun seekTo(positionMs: Long)
    fun stop()
}

// Length of a voice message file in milliseconds, 0 if it can't be read. Blocking file I/O.
fun voiceMessageDurationMs(filePath: String): Long
```

## Usage

A chat screen with an MVI ViewModel. The ViewModel owns the controllers and reports failures:

```kotlin
class ChatViewModel : ViewModel() {
    val files = VoiceMessageFiles.appStorage(desktopAppName = "MyApp")
    val playback = VoicePlaybackController()
    val recording = VoiceRecordingController(files, playback)

    fun play(id: String, fileName: String) {
        playback.play(id, files.path(fileName)).onFailure { showError(it) }
    }
    fun pause() = playback.pause()
    fun seek(id: String, positionMs: Long) = playback.seekTo(id, positionMs)

    fun startRecording() = viewModelScope.launch {
        // ask for the microphone permission first
        recording.start().onFailure { showError(it) } // AudioBusy during a phone call
    }
    fun stopRecording() = viewModelScope.launch { recording.stop().onFailure { showError(it) } }
    fun discardRecording() = viewModelScope.launch { recording.discard() }
    fun playDraft() {
        val draft = recording.state.value as? VoiceRecordingState.Recorded ?: return
        playback.play(VoiceRecordingController.DRAFT_PLAYBACK_ID, draft.filePath).onFailure { showError(it) }
    }
    fun sendRecording() = viewModelScope.launch {
        val draft = recording.state.value as? VoiceRecordingState.Recorded ?: return@launch
        upload(draft.fileName)
        recording.clear()
    }

    override fun onCleared() {
        recording.release()
        playback.release()
    }
}
```

In the message bubble:

```kotlin
VoiceMessagePlayer(
    id = message.id,
    filePath = viewModel.files.path(message.fileName),
    playback = viewModel.playback.state,
    texts = VoiceMessagePlayerTexts(play = stringResource(Res.string.play), pause = ..., position = ...),
    onPlay = { viewModel.play(message.id, message.fileName) },
    onPause = viewModel::pause,
    onSeek = { viewModel.seek(message.id, it) },
    color = if (isOwnMessage) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
)
```

In the input row, next to the app's own record/stop/send button:

```kotlin
val recordingState by viewModel.recording.state.collectAsState()
VoiceMessageRecordingBar(
    state = recordingState,
    levels = viewModel.recording.levels,
    playback = viewModel.playback.state,
    maxDurationMs = viewModel.recording.maxDurationMs,
    texts = VoiceMessageRecordingBarTexts(recording = ..., discard = ..., player = playerTexts),
    onPlay = viewModel::playDraft,
    onPause = viewModel::pause,
    onSeek = { viewModel.seek(VoiceRecordingController.DRAFT_PLAYBACK_ID, it) },
    onDiscard = viewModel::discardRecording,
    modifier = Modifier.weight(1f),
)
```

**Other apps' audio.** While a voice message plays, music and podcasts of other apps pause, and
they continue once the voice message is paused, stopped, finished or fails to play. When another
app takes the audio (a phone call, a video, a navigation prompt, another music app), the voice
message pauses and stays paused (`VoicePlaybackState.isPlaying` false, same `id` and position)
until it is played again.

- Android: transient audio focus (other apps pause, no ducking), audio marked as speech on the
  media stream. The library's manifest registers a small `ContentProvider`
  (`VoiceMessagesInitProvider`) that hands the application context to the library, so nothing
  needs a `Context`. Nothing to set up in the app.
- iOS: the shared `AVAudioSession` with category Playback. It is deactivated (other apps notified)
  once no `VoicePlayer` plays any more: on pause, stop, at the end and when playing fails.
- Desktop: no audio focus.

While a recording runs, other apps' audio pauses too, so it doesn't end up in the recording, and
it may continue once the recording is stopped or fails to start. Android uses transient exclusive
audio focus (a focus loss while recording, such as an incoming call, keeps the recording running);
iOS uses the `AVAudioSession` category Record.

**Live bars.** `VoiceRecordingController.levels` reads `VoiceRecorder.inputLevel` every 50 ms: 0
(silence) to 1 (loud), linear in decibels from -60 dBFS to full scale. On Android every read of
`inputLevel` returns the peak since the previous read, so only the controller reads it.

## Platform setup

- **Android**: add `<uses-permission android:name="android.permission.RECORD_AUDIO" />` to your app's manifest, and request the runtime permission before `VoiceRecordingController.start()`.
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

The `:example` and `:example-android` modules show the library end to end: a chat-like screen
with a record/stop/send button, the `VoiceMessageRecordingBar` with live bars and the draft
player, and a list of "sent" messages played with `VoiceMessagePlayer`. Both always use the library
source of this repository, so changes can be tried before a release.

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

### 1.1.1

- iOS: `VoiceMessagePlayer` no longer crashes the app for a blank or missing `filePath` (for
  example a just-sent message whose file isn't downloaded yet). Reading the length returns 0
  there, as on Android and desktop.

### 1.1.0

Breaking: the voice message code of the Schneaggchat app moved into the library.

- New `VoiceMessageFiles`: voice files in one folder by bare file name (path, exists, new file
  name, save, read, delete, delete all, duration); `appStorage(desktopAppName)` uses the app's
  persistent storage (Android `filesDir`, iOS Documents, desktop app data folder).
- New `VoicePlaybackController` and `VoicePlaybackState`: one voice message at a time keyed by
  id, progress as `StateFlow`, pause, seek, paused by other apps without losing the position.
  Seeking a message that is not selected stops the current one and selects it paused there.
- New `VoiceRecordingController` and `VoiceRecordingState`: records into a new file, elapsed time,
  stop at a maximum length (2 minutes by default), real length measured from the file, unsent
  draft, live microphone `levels`.
- New composables `VoiceMessagePlayer` (play/pause, slider, time; replaces the old
  `VoiceMessagePlayer(filePath)` / `VoiceMessagePlayer(bytes)`), `VoiceMessageRecordingBar` and
  `VoiceMessageLevelBars`. `VoiceMessageRecorder` is removed.
- New `VoiceMessageException` (`AudioBusy`, `FileMissing`, `Failed`). `VoicePlayer` and
  `VoiceRecorder` throw `AudioBusy` when another app holds the audio (still an
  `IllegalStateException`). The controllers return failures as `Result`.
- `VoicePlayer.play(filePath, startPositionMs)` starts at a position.
- New `formatVoiceMessageTime(ms)` ("m:ss").
- Built against Compose Multiplatform 1.12.2.

### 1.0.3

- iOS: the audio session is deactivated with "notify others" as soon as no voice message plays any
  more (`pause()`, end of the message, failed `play()`, `stop()`), so the interrupted music app
  continues, like on Android. `resume()` activates it again. Pausing one `VoicePlayer` doesn't cut
  off another one that still plays. `play()` throws when `AVAudioPlayer.play()` fails.
- Android: `VoiceRecorder` takes transient exclusive audio focus, so other apps' music pauses while
  recording and isn't recorded. Given back on `stop()` and when `start()` fails. `start()` throws
  `IllegalStateException` when the focus is denied (for example during a phone call). A focus loss
  while recording keeps the recording running.
- New `VoiceRecorder.inputLevel` on all platforms: the microphone level while recording, 0..1
  (dB scale from -60 dBFS), 0 when not recording, for a moving recording visualizer.
- Android `start()` releases the `MediaRecorder` when it can't be prepared or started.

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
