package com.ioi.youtube.presentation.player

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import java.util.Locale

/** Одна відеодоріжка манифеста. Без Media3 — легше тестувати. */
data class VideoTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val height: Int,
    val bitrate: Int,
)

/** Пункт меню якості. */
data class QualityOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val height: Int,
    val bitrate: Int,
)

/**
 * Одна якість на висоту (найвищий бітрейт: у YouTube 1080p буває і avc1, і vp9),
 * за спаданням висоти.
 */
fun chooseQualityOptions(tracks: List<VideoTrackInfo>): List<QualityOption> =
    tracks
        .filter { it.height > 0 }
        .groupBy { it.height }
        .map { (_, sameHeight) -> sameHeight.maxBy { it.bitrate } }
        .sortedWith(compareByDescending<VideoTrackInfo> { it.height }.thenByDescending { it.bitrate })
        .map { QualityOption(it.groupIndex, it.trackIndex, it.height, it.bitrate) }

fun formatQualityLabel(height: Int, bitrate: Int): String = "${height}p · ${formatBitrate(bitrate)}"

private fun formatBitrate(bitrate: Int): String = when {
    bitrate <= 0 -> "—"
    bitrate < 1_000_000 -> "${bitrate / 1000} Кбіт/с"
    else -> "%.1f Мбіт/с".format(Locale.US, bitrate / 1_000_000.0)
}

/**
 * Відеодоріжки зі стану плеєра (порядок груп зберігаємо — індекси йдуть у меню).
 *
 * Лише групи з типом [C.TRACK_TYPE_VIDEO]: Media3 зберiгає override за типом
 * групи, тож override для групи з типом UNKNOWN (mime невiдомий) нiколи не
 * застосується — краще не показувати такий пункт, нiж показати й зробити
 * його тихим no-op. Для HLS mime завжди є (з `CODECS`), тож меню не страждає.
 */
fun Tracks.videoTrackInfos(): List<VideoTrackInfo> =
    groups.flatMapIndexed { groupIndex, group ->
        if (group.type != C.TRACK_TYPE_VIDEO) {
            emptyList()
        } else {
            (0 until group.length).map { trackIndex ->
                val format = group.getTrackFormat(trackIndex)
                VideoTrackInfo(groupIndex, trackIndex, format.height, format.bitrate)
            }
        }
    }

/** Новi параметри вибору дорiжок; `option == null` — «Авто» (адаптивно). */
fun qualityParameters(player: Player, option: QualityOption?): TrackSelectionParameters {
    val builder = player.trackSelectionParameters.buildUpon()
    if (option == null) {
        return builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
    }
    val group = player.currentTracks.groups.getOrNull(option.groupIndex)
    // Меню могло пережити змiну дорiжок: iндекс групи/дорiжки мiг застарiти,
    // а на цьому мiсцi тепер може бути не вiдео. Тодi лишаємо параметри як є —
    // краще грати як ранiше, нiж упасти чи перемкнути аудiо.
    if (group == null || option.trackIndex !in 0 until group.length) {
        return player.trackSelectionParameters
    }
    if (group.type != C.TRACK_TYPE_VIDEO) {
        return player.trackSelectionParameters
    }
    return builder
        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        .build()
}
