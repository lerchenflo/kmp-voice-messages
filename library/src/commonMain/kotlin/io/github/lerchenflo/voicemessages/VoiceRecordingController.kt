package io.github.lerchenflo.voicemessages

import io.github.lerchenflo.voicemessages.internal.probeDurationMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** What a [VoiceRecordingController] holds. */
sealed interface VoiceRecordingState {

    /** No recording and no unsent draft. */
    data object Idle : VoiceRecordingState

    /** A recording runs into [fileName] (absolute path [filePath]) since [elapsedMs]. */
    data class Recording(val fileName: String, val filePath: String, val elapsedMs: Long) : VoiceRecordingState

    /** The finished, unsent recording [fileName] of [durationMs] (measured from the file). */
    data class Recorded(val fileName: String, val filePath: String, val durationMs: Long) : VoiceRecordingState
}

/**
 * Records one voice message at a time into [files], for example in a chat ViewModel; works without
 * Compose. Call [release] in `onCleared`.
 *
 * - [start] stops [playback] (if given) and starts recording into a new file
 *   ([VoiceMessageFiles.newFileName]). The microphone permission is the app's job: ask before [start].
 * - [state] counts the elapsed time while recording. At [maxDurationMs] the recording stops by itself.
 * - [stop] measures the real length from the file and keeps the recording as an unsent draft
 *   ([VoiceRecordingState.Recorded]). Play it with [DRAFT_PLAYBACK_ID]. After sending it, call
 *   [clear]; [discard] deletes it.
 * - [levels] holds the recent microphone levels for live bars (newest last, 0..1).
 *
 * Failures come back as `Result.failure` with a [VoiceMessageException].
 */
class VoiceRecordingController(
    private val files: VoiceMessageFiles,
    private val playback: VoicePlaybackController? = null,
    val maxDurationMs: Long = DEFAULT_MAX_DURATION_MS,
    private val levelCount: Int = DEFAULT_LEVEL_COUNT,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private var recorder: VoiceRecorder? = null
    private var tickerJob: Job? = null
    private var startMark: TimeMark? = null

    private val _state = MutableStateFlow<VoiceRecordingState>(VoiceRecordingState.Idle)

    /** The running recording or the unsent draft. */
    val state: StateFlow<VoiceRecordingState> = _state.asStateFlow()

    private val _levels = MutableStateFlow<List<Float>>(emptyList())

    /**
     * Microphone levels of the running recording, 0 (silence) to 1 (loud), oldest first, one about
     * every 50 ms, at most [levelCount]. Empty when no recording runs. The controller is the only
     * reader of [VoiceRecorder.inputLevel].
     */
    val levels: StateFlow<List<Float>> = _levels.asStateFlow()

    /**
     * Starts a recording. A previous unsent draft is left on disk: [discard] or [clear] it first.
     *
     * Failures: [VoiceMessageException.AudioBusy] (another app holds the audio, e.g. a phone call)
     * or [VoiceMessageException.Failed]. Nothing records then and the state stays as it was.
     */
    suspend fun start(): Result<VoiceRecordingState.Recording> = mutex.withLock {
        (_state.value as? VoiceRecordingState.Recording)?.let { return@withLock Result.success(it) }

        playback?.let {
            withContext(Dispatchers.Main) { it.stop() }
            // Gives the platform audio session a moment to switch from playback to recording
            delay(100)
        }

        val fileName = files.newFileName()
        val filePath = files.path(fileName)
        val newRecorder = VoiceRecorder()
        try {
            withContext(Dispatchers.IO) { files.ensureDirectory() }
            newRecorder.start(filePath)
        } catch (e: Exception) {
            runCatching { newRecorder.stop() }
            runCatching { files.delete(fileName) }
            return@withLock Result.failure(e.toVoiceMessageException("Could not start recording"))
        }

        recorder = newRecorder
        startMark = TimeSource.Monotonic.markNow()
        _levels.value = emptyList()
        val recording = VoiceRecordingState.Recording(fileName, filePath, elapsedMs = 0L)
        _state.value = recording
        startTicker(newRecorder, fileName)
        Result.success(recording)
    }

    /**
     * Stops the running recording and keeps it as the unsent draft, with its length measured from
     * the file. Returns the current draft if no recording runs, and fails if there is none.
     */
    suspend fun stop(): Result<VoiceRecordingState.Recorded> = mutex.withLock {
        val current = _state.value
        if (current is VoiceRecordingState.Recorded) return@withLock Result.success(current)
        if (current !is VoiceRecordingState.Recording) {
            return@withLock Result.failure(VoiceMessageException.Failed("No recording runs"))
        }

        val elapsedMs = startMark?.elapsedNow()?.inWholeMilliseconds ?: current.elapsedMs
        stopRecorder()
        _levels.value = emptyList()
        val measuredMs = withContext(Dispatchers.IO) { probeDurationMs(current.filePath) }
        val recorded = VoiceRecordingState.Recorded(
            fileName = current.fileName,
            filePath = current.filePath,
            durationMs = if (measuredMs > 0) measuredMs else elapsedMs,
        )
        _state.value = recorded
        Result.success(recorded)
    }

    /** Stops a running recording or drops the unsent draft, and deletes its file. */
    suspend fun discard(): Result<Unit> = mutex.withLock {
        val fileName = when (val current = _state.value) {
            is VoiceRecordingState.Recording -> current.fileName
            is VoiceRecordingState.Recorded -> current.fileName
            VoiceRecordingState.Idle -> return@withLock Result.success(Unit)
        }
        stopRecorder()
        stopDraftPlayback()
        _levels.value = emptyList()
        _state.value = VoiceRecordingState.Idle
        try {
            files.delete(fileName)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e.toVoiceMessageException("Could not delete $fileName"))
        }
    }

    /** Forgets the unsent draft without deleting its file, for example after it was sent. */
    suspend fun clear() = mutex.withLock {
        if (_state.value !is VoiceRecordingState.Recorded) return@withLock
        stopDraftPlayback()
        _state.value = VoiceRecordingState.Idle
    }

    /** Stops a running recording (its file stays) and ends the controller, for example in `ViewModel.onCleared`. */
    fun release() {
        tickerJob?.cancel()
        runCatching { recorder?.stop() }
        recorder = null
        scope.cancel()
    }

    private fun stopRecorder() {
        tickerJob?.cancel()
        tickerJob = null
        runCatching { recorder?.stop() }
        recorder = null
        startMark = null
    }

    private suspend fun stopDraftPlayback() {
        val player = playback ?: return
        withContext(Dispatchers.Main) {
            if (player.state.value.id == DRAFT_PLAYBACK_ID) player.stop()
        }
    }

    private fun startTicker(activeRecorder: VoiceRecorder, fileName: String) {
        val mark = startMark ?: return
        tickerJob = scope.launch {
            var bar = 0f
            var tick = 0
            while (isActive) {
                delay(LEVEL_INTERVAL_MS)
                // A bar jumps up at once and sinks within a few readings, so short syllables stay visible
                bar = max(activeRecorder.inputLevel, bar * 0.5f)
                _levels.update { (it + bar).takeLast(levelCount) }

                val elapsedMs = mark.elapsedNow().inWholeMilliseconds
                if (++tick % ELAPSED_EVERY_TICKS == 0) {
                    _state.update {
                        if (it is VoiceRecordingState.Recording && it.fileName == fileName) it.copy(elapsedMs = elapsedMs) else it
                    }
                }
                if (elapsedMs >= maxDurationMs) {
                    // Outside this job: stop() cancels the ticker
                    scope.launch { stop() }
                    break
                }
            }
        }
    }

    companion object {
        /** Playback id of the unsent draft, for [VoicePlaybackController.play]. */
        const val DRAFT_PLAYBACK_ID: String = "voice-message-draft"

        /** Two minutes. */
        const val DEFAULT_MAX_DURATION_MS: Long = 120_000L

        /** Live levels kept for the bars, enough for a wide input bar (6 seconds). */
        const val DEFAULT_LEVEL_COUNT: Int = 120

        private const val LEVEL_INTERVAL_MS = 50L

        // The elapsed time is published every 200 ms, the levels every 50 ms
        private const val ELAPSED_EVERY_TICKS = 4
    }
}
