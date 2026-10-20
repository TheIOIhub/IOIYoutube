package com.ioi.youtube.domain.model

import java.util.Locale

/**
 * Одна якість на висоту (у YouTube 1080p буває avc1 і vp9 — лишаємо жирніший бітрейт),
 * за спаданням висоти.
 */
fun chooseVideoVariants(variants: List<VideoVariant>): List<VideoVariant> =
    variants.filter { it.url.isNotBlank() }
        .groupBy { it.height }
        .map { (_, sameHeight) -> sameHeight.maxBy { it.bitrate } }
        .sortedWith(compareByDescending<VideoVariant> { it.height }.thenByDescending { it.bitrate })

/** Найвища якість не вище 1080p; якщо такої немає — найвища наявна. */
fun defaultVideo(variants: List<VideoVariant>): VideoVariant? =
    variants.filter { it.height in 1..1080 }.maxByOrNull { it.height }
        ?: variants.maxByOrNull { it.height }

/** Одна доріжка на audioTrackId (без id — на URL), оригінал першим. */
fun chooseAudioTracks(tracks: List<AudioTrackOption>): List<AudioTrackOption> =
    tracks.filter { it.url.isNotBlank() }
        .groupBy { it.id.ifBlank { it.url } }
        .map { (_, sameTrack) -> sameTrack.maxBy { it.bitrate } }
        .sortedWith(compareByDescending<AudioTrackOption> { it.original }.thenBy { it.label })

fun defaultAudio(tracks: List<AudioTrackOption>): AudioTrackOption? =
    tracks.firstOrNull { it.original } ?: tracks.firstOrNull()

/**
 * Підпис доріжки: мова з великої літери → назва від YouTube → «Аудіо N».
 * Суфікс показує, чи це оригінал, чи дубляж.
 */
fun audioLabel(
    language: String?,
    name: String?,
    original: Boolean,
    index: Int = 1,
): String {
    val languageName = language
        ?.takeIf { it.isNotBlank() }
        ?.let { Locale.forLanguageTag(it.replace('_', '-')).getDisplayName(Locale("uk")) }
        ?.takeIf { it.isNotBlank() }
    val base = languageName ?: name?.takeIf { it.isNotBlank() } ?: "Аудіо $index"
    val suffix = if (original) "оригінал" else "дубляж"
    return "${base.replaceFirstChar { it.uppercase() }} · $suffix"
}

fun formatVideoLabel(height: Int, bitrate: Int): String = "${height}p · ${formatBitrate(bitrate)}"

private fun formatBitrate(bitrate: Int): String = when {
    bitrate <= 0 -> "—"
    bitrate < 1_000_000 -> "${bitrate / 1000} Кбіт/с"
    else -> "%.1f Мбіт/с".format(Locale.US, bitrate / 1_000_000.0)
}
