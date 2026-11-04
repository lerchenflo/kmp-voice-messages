package io.github.lerchenflo.voicemessages.internal

import net.sourceforge.jaad.aac.Decoder
import net.sourceforge.jaad.aac.SampleBuffer
import net.sourceforge.jaad.mp4.MP4Container
import net.sourceforge.jaad.mp4.api.AudioTrack
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import javax.sound.sampled.AudioFormat

// Java Sound only reads WAV / AIFF / AU, but Android and iOS record voice messages as AAC in an
// MP4 container (.m4a). These helpers read such files with JAAD, so the desktop can play them.

/** PCM audio decoded from a file, ready for `Clip.open(format, data, 0, data.size)`. */
internal class DecodedAudio(val format: AudioFormat, val data: ByteArray)

/** True if [file] is an MP4 container (bytes 4-7 are "ftyp"), whatever its file name says. */
internal fun isMp4File(file: File): Boolean {
    val header = ByteArray(8)
    val read = file.inputStream().use { it.readNBytes(header, 0, header.size) }
    return read == header.size && String(header, 4, 4, Charsets.US_ASCII) == "ftyp"
}

/** Decodes the first AAC track of the MP4 [file] to 16-bit little-endian PCM. */
internal fun decodeMp4Aac(file: File): DecodedAudio {
    RandomAccessFile(file, "r").use { raf ->
        val track = MP4Container(raf).movie
            .getTracks(AudioTrack.AudioCodec.AAC)
            .firstOrNull() as? AudioTrack
            ?: throw IOException("No AAC audio track in ${file.name}")

        val decoder = Decoder(track.decoderSpecificInfo)
        val buffer = SampleBuffer().apply { setBigEndian(false) }
        val pcm = ByteArrayOutputStream()
        while (track.hasMoreFrames()) {
            decoder.decodeFrame(track.readNextFrame().data, buffer)
            pcm.write(buffer.data)
        }
        if (pcm.size() == 0) throw IOException("No audio frames in ${file.name}")

        val format = AudioFormat(
            buffer.sampleRate.toFloat(),
            buffer.bitsPerSample,
            buffer.channels,
            true,
            false
        )
        return DecodedAudio(format, pcm.toByteArray())
    }
}

/** Length of the MP4 [file] in milliseconds, read from its header without decoding. */
internal fun mp4DurationMs(file: File): Long =
    RandomAccessFile(file, "r").use { raf -> (MP4Container(raf).movie.duration * 1_000).toLong() }
