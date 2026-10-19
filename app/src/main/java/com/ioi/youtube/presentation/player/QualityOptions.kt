package com.ioi.youtube.presentation.player

import androidx.media3.common.C
import androidx.media3.common.Format
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

/** Відеодоріжки зі стану плеєра (порядок груп зберігаємо — індекси йдуть у меню). */
fun Tracks.videoTrackInfos(): List<VideoTrackInfo> =
    groups.flatMapIndexed { groupIndex, group ->
        (0 until group.length).mapNotNull { trackIndex ->
            val format = group.getTrackFormat(trackIndex)
            if (!group.isVideoGroup(format)) {
                return@mapNotNull null
            }
            VideoTrackInfo(groupIndex, trackIndex, format.height, format.bitrate)
        }
    }

/**
 * Тип групи Media3 виводить з mime-типу формату; коли mime невідомий
 * (у HLS буває без `CODECS`), лишається [C.TRACK_TYPE_UNKNOWN] — тоді
 * орієнтуємось на висоту кадру, якої в аудіо/текстових доріжок не буває.
 * Без цієї гілки такий манифест дав би порожнє меню якості.
 */
private fun Tracks.Group.isVideoGroup(format: Format): Boolean = when (type) {
    C.TRACK_TYPE_VIDEO -> true
    C.TRACK_TYPE_UNKNOWN -> format.height > 0
    else -> false
}

/** Новi параметри вибору дорiжок; `option == null` — «Авто» (адаптивно). */
fun qualityParameters(player: Player, option: QualityOption?): TrackSelectionParameters {
    val builder = player.trackSelectionParameters.buildUpon()
    if (option == null) {
        return builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
    }
    val group = player.currentTracks.groups.getOrNull(option.groupIndex)?.mediaTrackGroup
        ?: return player.trackSelectionParameters
    return builder.setOverrideForType(TrackSelectionOverride(group, option.trackIndex)).build()
}
