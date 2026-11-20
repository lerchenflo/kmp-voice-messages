package io.github.lerchenflo.voicemessages

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.concurrent.Volatile

/**
 * The voice message a [VoicePlaybackController] has selected.
 *
 * @property id the selected message (your message id, or [VoiceRecordingController.DRAFT_PLAYBACK_ID]
 * for the unsent recording), null when none is selected. A selected message is playing or paused.
 * @property isPlaying whether it plays right now. False while paused, by the user or because
 * another app took the audio (a phone call, a video, another music app).
 * @property positionMs playback position; kept while paused.
 * @property durationMs length of the message, 0 until it was loaded once.
 */
data class VoicePlaybackState(
    val id: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/**
 * Plays one voice message at a time, for example on a chat screen, keyed by an id. Works without
 * Compose: create it in a ViewModel and call [release] in `onCleared`.
 *
 * - [play] of another id stops the current message. [play] of the paused id resumes it.
 * - [state] publishes the selected id, playing, position and duration, updated every
 *   [pollIntervalMs] while playing.
 * - When another app takes the audio, the message pauses and keeps its position; it doesn't
 *   resume by itself. At the end of the message the selection is cleared ([VoicePlaybackState.id] null).
 *
 * Failures come back as `Result.failure` with a [VoiceMessageException]: the app decides whether
 * to show an error. Call the functions from the main thread.
 */
class VoicePlaybackController(private val pollIntervalMs: Long = 100L) {

    private val player = VoicePlayer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null

    // The id whose file is loaded in the player; null when the selected message is not loaded
    // yet (selected by seekTo) or the message reached its end
    @Volatile
    private var loadedId: String? = null

    // Raised by every call, so a progress poll that started before can't overwrite its state
    @Volatile
    private var generation: Int = 0

    private val _state = MutableStateFlow(VoicePlaybackState())

    /** The selected message and its progress. */
    val state: StateFlow<VoicePlaybackState> = _state.asStateFlow()

    /**
     * Plays the voice message [id] stored at [filePath]: resumes it when it is the paused message,
     * starts it at the selected position when it was selected by [seekTo], or from the beginning
     * otherwise (stopping the current message).
     *
     * Failures: [VoiceMessageException.FileMissing], [VoiceMessageException.AudioBusy] (another
     * app holds the audio, e.g. a phone call; a paused message then stays paused), or
     * [VoiceMessageException.Failed]. After a failed start nothing is selected.
     */
    fun play(id: String, filePath: String): Result<Unit> {
        val current = _state.value
        if (current.id == id && loadedId == id) {
            if (player.isPlaying) return Result.success(Unit)
            if (player.isPaused) return resumeLoaded(id)
        }
        val startPositionMs = if (current.id == id && loadedId != id) current.positionMs else 0L

        stopPlayer()
        generation++
        if (SystemFileSystem.metadataOrNull(Path(filePath))?.isRegularFile != true) {
            _state.value = VoicePlaybackState()
            return Result.failure(VoiceMessageException.FileMissing(filePath))
        }
        return try {
            player.play(filePath, startPositionMs)
            loadedId = id
            _state.value = VoicePlaybackState(
                id = id,
                isPlaying = true,
                positionMs = startPositionMs,
                durationMs = player.durationMs,
            )
            startPolling()
            Result.success(Unit)
        } catch (e: Exception) {
            stopPlayer()
            _state.value = VoicePlaybackState()
            Result.failure(e.toVoiceMessageException("Could not play $filePath"))
        }
    }

    /** Pauses the selected message and keeps its position. */
    fun pause() {
        generation++
        pollingJob?.cancel()
        val loaded = loadedId != null
        if (loaded) player.pause()
        _state.update {
            if (it.id == null) it else it.copy(isPlaying = false, positionMs = if (loaded) player.positionMs else it.positionMs)
        }
    }

    /**
     * Moves the message [id] to [positionMs]. For the selected message it seeks (playing or paused
     * as before). For any other message it stops the current one and selects [id] paused at
     * [positionMs]; the next [play] of [id] starts there.
     */
    fun seekTo(id: String, positionMs: Long) {
        val position = positionMs.coerceAtLeast(0L)
        val current = _state.value
        when {
            current.id == id && loadedId == id -> {
                player.seekTo(position)
                _state.update { it.copy(positionMs = position) }
            }
            current.id == id -> _state.update { it.copy(positionMs = position) }
            else -> {
                stopPlayer()
                generation++
                _state.value = VoicePlaybackState(id = id, isPlaying = false, positionMs = position)
            }
        }
    }

    /** Stops playback and clears the selection. */
    fun stop() {
        generation++
        stopPlayer()
        _state.value = VoicePlaybackState()
    }

    /** Stops playback and ends the controller, for example in `ViewModel.onCleared`. */
    fun release() {
        stop()
        scope.cancel()
    }

    private fun resumeLoaded(id: String): Result<Unit> = try {
        player.resume()
        generation++
        _state.update { it.copy(id = id, isPlaying = true, positionMs = player.positionMs) }
        startPolling()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e.toVoiceMessageException("Could not resume"))
    }

    private fun stopPlayer() {
        pollingJob?.cancel()
        pollingJob = null
        player.stop()
        loadedId = null
    }

    private fun startPolling() {
        pollingJob?.cancel()
        val pollGeneration = generation
        pollingJob = scope.launch {
            while (isActive) {
                delay(pollIntervalMs)
                val playing = player.isPlaying
                val paused = player.isPaused
                val position = player.positionMs
                val duration = player.durationMs
                when {
                    playing -> publish(pollGeneration) {
                        it.copy(isPlaying = true, positionMs = position, durationMs = duration)
                    }
                    paused -> {
                        // Another app took the audio: keep the position, the user resumes
                        publish(pollGeneration) { it.copy(isPlaying = false, positionMs = position) }
                        break
                    }
                    else -> {
                        // Reached the end
                        if (publish(pollGeneration) { VoicePlaybackState() }) loadedId = null
                        break
                    }
                }
            }
        }
    }

    // Applies [transform] only while no call happened since the poll started
    private inline fun publish(pollGeneration: Int, transform: (VoicePlaybackState) -> VoicePlaybackState): Boolean {
        var applied = false
        _state.update {
            applied = generation == pollGeneration
            if (applied) transform(it) else it
        }
        return applied
    }
}
