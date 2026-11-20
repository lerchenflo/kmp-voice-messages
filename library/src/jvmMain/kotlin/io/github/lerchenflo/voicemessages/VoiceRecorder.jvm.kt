package io.github.lerchenflo.voicemessages

import io.github.lerchenflo.voicemessages.internal.inputLevelFromPeak
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread

actual class VoiceRecorder actual constructor() {

    private var line: TargetDataLine? = null
    private var recordingThread: Thread? = null
    private var meter: LevelMeteringInputStream? = null

    @Volatile
    private var recording: Boolean = false

    actual val isRecording: Boolean
        get() = recording

    actual val inputLevel: Float
        get() = if (recording) inputLevelFromPeak(meter?.latestPeak ?: 0f) else 0f

    actual suspend fun start(filePath: String) {
        // Tuned for voice rather than music: mono 8 kHz (phone call quality) keeps the uncompressed
        // WAV at 16 kB/s, so a 2 minute message (~1.9 MB) stays under the server's 2 MiB upload limit.
        val format = AudioFormat(8_000f, 16, 1, true, false)
        val targetLine = AudioSystem.getTargetDataLine(format)
        targetLine.open(format)
        targetLine.start()
        line = targetLine

        // The samples pass through the meter on their way into the file, so inputLevel can read them
        val levelMeter = LevelMeteringInputStream(AudioInputStream(targetLine))
        meter = levelMeter
        recording = true

        val outFile = File(filePath)
        recordingThread = thread(name = "VoiceRecorder") {
            val stream = AudioInputStream(levelMeter, format, AudioSystem.NOT_SPECIFIED.toLong())
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, outFile)
        }
    }

    actual fun stop() {
        val targetLine = line ?: return
        targetLine.stop()
        targetLine.close()
        recordingThread?.join()
        line = null
        recordingThread = null
        meter = null
        recording = false
    }
}

/**
 * Passes 16-bit little-endian mono samples through and keeps the peak of the latest chunk.
 * Reads at most [CHUNK_BYTES] at a time: AudioSystem.write asks for 4 kB (a quarter second at
 * 8 kHz), which would update the level only four times a second.
 */
private class LevelMeteringInputStream(input: InputStream) : FilterInputStream(input) {

    /** Peak of the latest chunk, 0..1 of full scale. */
    @Volatile
    var latestPeak: Float = 0f
        private set

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = super.read(b, off, minOf(len, CHUNK_BYTES))
        if (count > 0) latestPeak = peakOf(b, off, count)
        return count
    }

    private fun peakOf(b: ByteArray, off: Int, count: Int): Float {
        var peak = 0
        var i = off
        val end = off + count - 1
        while (i < end) {
            val sample = (b[i].toInt() and 0xFF) or (b[i + 1].toInt() shl 8)
            val magnitude = kotlin.math.abs(sample.toShort().toInt())
            if (magnitude > peak) peak = magnitude
            i += 2
        }
        return peak / 32_768f
    }

    private companion object {
        // 50 ms of 8 kHz 16-bit mono audio; even, so a chunk never splits a sample
        const val CHUNK_BYTES = 800
    }
}
