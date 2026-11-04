package io.github.lerchenflo.voicemessages

import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread

actual class VoiceRecorder actual constructor() {

    private var line: TargetDataLine? = null
    private var recordingThread: Thread? = null
    private var recording: Boolean = false

    actual val isRecording: Boolean
        get() = recording

    actual suspend fun start(filePath: String) {
        // Tuned for voice rather than music: mono 8 kHz (phone call quality) keeps the uncompressed
        // WAV at 16 kB/s, so a 2 minute message (~1.9 MB) stays under the server's 2 MiB upload limit.
        val format = AudioFormat(8_000f, 16, 1, true, false)
        val targetLine = AudioSystem.getTargetDataLine(format)
        targetLine.open(format)
        targetLine.start()
        line = targetLine
        recording = true

        val outFile = File(filePath)
        recordingThread = thread(name = "VoiceRecorder") {
            AudioSystem.write(AudioInputStream(targetLine), AudioFileFormat.Type.WAVE, outFile)
        }
    }

    actual fun stop() {
        val targetLine = line ?: return
        targetLine.stop()
        targetLine.close()
        recordingThread?.join()
        line = null
        recordingThread = null
        recording = false
    }
}
