package com.ioi.youtube.presentation.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.ioi.youtube.domain.model.AudioTrackOption
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoVariant

/**
 * Володіє єдиним ExoPlayer на екран відео.
 *
 * Створюється в [PlayerScreen] **до** розгалуження портрет/ландшафт, тому поворот
 * не перестворює плеєр і не скидає позицію. Перемикання якості/мови перебудовує
 * джерело з `setMediaSource(source, currentPosition)`.
 */
class PlayerHolder(
    context: Context,
    private val sourceFactory: (Context, PlaybackPlan) -> MediaSource = ::mediaSourceFor,
) {

    private val appContext = context.applicationContext
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val errorListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            recover()
        }
    }

    private var stream: VideoStream? = null
    private var video: VideoVariant? = null
    private var audio: AudioTrackOption? = null
    private var auto = false
    private var currentPlan: PlaybackPlan? = null
    private var loaded = false
    private var released = false

    var error: String? by mutableStateOf(null)
        private set

    init {
        player.addListener(errorListener)
    }

    /** Перше завантаження або зміна вибору; позиція зберігається. */
    fun update(
        stream: VideoStream,
        video: VideoVariant?,
        audio: AudioTrackOption?,
        auto: Boolean,
    ) {
        if (released) return
        this.stream = stream
        this.video = video
        this.audio = audio
        this.auto = auto

        val plan = buildPlaybackPlan(stream, video, audio, auto)
        if (plan == null) {
            error = ERROR_PLAYBACK
            return
        }
        if (loaded && plan == currentPlan) return
        apply(plan, if (loaded) player.currentPosition else 0L)
    }

    /**
     * Слухач з [init] знімається, а стан скидається: [release] має бути безпечним
     * і після відключення екрана, інакше ExoPlayer дозвінимо `onPlayerError` на
     * вільному плеєрі, який більше ніхто не оновлює.
     */
    fun release() {
        if (released) return
        released = true
        player.removeListener(errorListener)
        currentPlan = null
        loaded = false
        player.release()
    }

    private fun apply(plan: PlaybackPlan, positionMs: Long) {
        error = null
        currentPlan = plan
        val playWhenReady = if (loaded) player.playWhenReady else true
        player.setMediaSource(sourceFactory(appContext, plan), positionMs)
        player.prepare()
        player.playWhenReady = playWhenReady
        loaded = true
    }

    /**
     * Merged → HLS → progressive. Якщо впав останній — показуємо помилку в UI.
     * Позиція зберігається на кожному кроці.
     */
    private fun recover() {
        val source = stream ?: return
        val fallback = when (currentPlan) {
            is PlaybackPlan.Merged -> source.hlsUrl?.takeIf { it.isNotBlank() }
                ?.let(PlaybackPlan::Hls)
                ?: source.url.takeIf { it.isNotBlank() }?.let(PlaybackPlan::Progressive)

            is PlaybackPlan.Hls -> source.url.takeIf { it.isNotBlank() }
                ?.let(PlaybackPlan::Progressive)

            else -> null
        }
        if (fallback == null) {
            error = ERROR_PLAYBACK
            return
        }
        apply(fallback, player.currentPosition)
    }

    companion object {
        const val ERROR_PLAYBACK = "Не вдалося відтворити відео"
    }
}

internal fun mediaSourceFor(context: Context, plan: PlaybackPlan): MediaSource = when (plan) {
    is PlaybackPlan.Merged -> MergingMediaSource(
        progressiveSource(context, plan.videoUrl),
        progressiveSource(context, plan.audioUrl),
    )

    is PlaybackPlan.Progressive -> progressiveSource(context, plan.url)

    is PlaybackPlan.Hls -> HlsMediaSource.Factory(DefaultDataSource.Factory(context))
        .createMediaSource(MediaItem.fromUri(plan.url))
}

private fun progressiveSource(context: Context, url: String): MediaSource =
    ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))
        .createMediaSource(MediaItem.fromUri(url))
