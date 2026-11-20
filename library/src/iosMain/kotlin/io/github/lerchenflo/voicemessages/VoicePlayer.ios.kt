package io.github.lerchenflo.voicemessages

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class VoicePlayer actual constructor() {

    private var player: AVAudioPlayer? = null

    // Set when the loaded voice message reached its end (or failed to decode), so isPaused can
    // tell that apart from a pause. AVAudioPlayer also pauses by itself when another app takes the
    // audio (phone call, other app's audio session); that pause counts as paused, not as finished.
    private var finished: Boolean = false

    // AVAudioPlayer holds its delegate weakly, so the player keeps it here
    private val playbackEndDelegate = PlaybackEndDelegate { endedPlayer ->
        if (endedPlayer === player) {
            finished = true
            releaseSession()
        }
    }

    // Whether this player activated the shared session for play() or resume() and has not given
    // it back yet through pause(), stop(), the end or a failed play()
    private var holdsSession: Boolean = false

    actual val positionMs: Long
        get() = player?.let { (it.currentTime * 1_000).toLong() } ?: 0L

    actual val durationMs: Long
        get() = player?.let { (it.duration * 1_000).toLong() } ?: 0L

    actual val isPlaying: Boolean
        get() = player?.isPlaying() ?: false

    actual val isPaused: Boolean
        get() = player?.let { !it.isPlaying() && !finished } ?: false

    actual fun play(filePath: String) {
        releasePlayer()
        try {
            startPlayer(filePath)
        } catch (e: Throwable) {
            // Nothing plays: let the app that played before continue
            releasePlayer()
            releaseSession()
            throw e
        }
    }

    private fun startPlayer(filePath: String) {
        activatePlaybackSession()

        val url = NSURL.fileURLWithPath(filePath)

        memScoped {
            val playerErrorVar = alloc<ObjCObjectVar<NSError?>>()
            val avPlayer = AVAudioPlayer(contentsOfURL = url, error = playerErrorVar.ptr)
            val playerError = playerErrorVar.value
            if (playerError != null) {
                throw IllegalStateException(
                    "Failed to create AVAudioPlayer for $filePath: ${playerError.localizedDescription}"
                )
            }

            if (!avPlayer.prepareToPlay()) {
                throw IllegalStateException("AVAudioPlayer.prepareToPlay() failed")
            }

            avPlayer.delegate = playbackEndDelegate
            finished = false
            player = avPlayer
            if (!avPlayer.play()) {
                throw IllegalStateException("AVAudioPlayer.play() failed")
            }
        }
    }

    actual fun pause() {
        val current = player ?: return
        if (current.isPlaying()) current.pause()
        // Paused by the user or by a system interruption: other apps may continue
        releaseSession()
    }

    actual fun resume() {
        val current = player?.takeIf { !it.isPlaying() } ?: return

        // The category is process-wide: a recording started since the last play() left the
        // session on Record, which would make this resume silent.
        activatePlaybackSession()

        finished = false
        current.play()
    }

    actual fun seekTo(positionMs: Long) {
        player?.currentTime = positionMs / 1_000.0
    }

    actual fun stop() {
        releasePlayer()
        releaseSession()
    }

    private fun releasePlayer() {
        player?.stop()
        player = null
        finished = false
    }

    private fun activatePlaybackSession() {
        memScoped {
            val session = AVAudioSession.sharedInstance()

            val categoryErrorVar = alloc<ObjCObjectVar<NSError?>>()
            // Playback-only category so the session follows the user's selected output route
            // (Bluetooth, AirPlay, wired, CarPlay) instead of forcing the built-in speaker.
            session.setCategory(AVAudioSessionCategoryPlayback, error = categoryErrorVar.ptr)
            val categoryError = categoryErrorVar.value
            if (categoryError != null) {
                throw IllegalStateException(
                    "Failed to set AVAudioSession category for playback: ${categoryError.localizedDescription}"
                )
            }

            val activeErrorVar = alloc<ObjCObjectVar<NSError?>>()
            session.setActive(true, error = activeErrorVar.ptr)
            val activeError = activeErrorVar.value
            if (activeError != null) {
                throw IllegalStateException(
                    "Failed to activate AVAudioSession for playback: ${activeError.localizedDescription}"
                )
            }
        }
        holdsSession = true
        PlaybackSessionUsers.players.add(this)
    }

    // Deactivates the shared session (notifying other apps, so their music continues) once no
    // VoicePlayer plays any more. Pausing one voice message must not cut off another one that
    // still plays, for example in a list of messages
    private fun releaseSession() {
        if (!holdsSession) return
        holdsSession = false
        // Players paused by a system interruption are dropped too: they no longer play
        PlaybackSessionUsers.players.removeAll { it === this || !it.isPlaying }
        if (PlaybackSessionUsers.players.isEmpty()) deactivateSession()
    }

    private fun deactivateSession() {
        memScoped {
            val session = AVAudioSession.sharedInstance()
            val errorVar = alloc<ObjCObjectVar<NSError?>>()
            session.setActive(
                false,
                withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation,
                error = errorVar.ptr
            )
            // Ignore errors - session might not be active
        }
    }
}

/** Reports the end of a voice message: played to the end, or stopped by a decode error. */
@OptIn(ExperimentalForeignApi::class)
private class PlaybackEndDelegate(
    private val onEnded: (AVAudioPlayer) -> Unit,
) : NSObject(), AVAudioPlayerDelegateProtocol {

    override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
        onEnded(player)
    }

    override fun audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer, error: NSError?) {
        onEnded(player)
    }
}

/** VoicePlayers that activated the shared AVAudioSession and may still play. Main thread only. */
private object PlaybackSessionUsers {
    val players: MutableSet<VoicePlayer> = mutableSetOf()
}
