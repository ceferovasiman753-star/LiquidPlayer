package com.liquid.player.data

import android.net.Uri
import androidx.compose.ui.graphics.Color

data class Track(
    val key: String,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val addedAt: Long,
    val artUri: Uri?,
    val downloaded: Boolean = false,
) {
    /** Stable accent colour derived from the track, used for glow / cards. */
    val accent: Color
        get() = accentFor(artist.ifBlank { title })
}

fun accentFor(seed: String): Color {
    val hue = ((seed.hashCode().toLong() and 0x7fffffff) % 360).toFloat()
    return Color.hsv(hue, 0.62f, 0.92f)
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
