package io.github.lerchenflo.voicemessages.internal

import kotlin.math.log10

// Quietest level that still moves the meter. Room noise sits around -60 dBFS, speech peaks
// between about -30 and 0 dBFS, so a dB scale (not a linear one) makes normal speech fill the range
private const val INPUT_LEVEL_FLOOR_DB = -60f

/** Maps a level in dBFS (0 = loudest) to 0..1, linear in dB from [INPUT_LEVEL_FLOOR_DB] to 0. */
internal fun inputLevelFromDb(db: Float): Float {
    if (db.isNaN()) return 0f
    return ((db - INPUT_LEVEL_FLOOR_DB) / -INPUT_LEVEL_FLOOR_DB).coerceIn(0f, 1f)
}

/** Maps a linear peak amplitude (0..1 of full scale) to 0..1 on the same dB scale. */
internal fun inputLevelFromPeak(peak: Float): Float {
    if (peak <= 0f) return 0f
    return inputLevelFromDb(20f * log10(peak))
}
