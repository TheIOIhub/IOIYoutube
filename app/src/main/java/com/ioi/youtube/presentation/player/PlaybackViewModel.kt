package com.ioi.youtube.presentation.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.ioi.youtube.domain.model.AudioTrackOption
import com.ioi.youtube.domain.model.VideoDetails
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoVariant
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Activity-scoped сесія відтворення: переживає навігацію між Home/Search/Player.
 * Володіє єдиним [PlayerHolder]; звільняє його лише в [onCleared].
 */
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private var holderRef: PlayerHolder? = null

    val holder: PlayerHolder
        get() = holderRef ?: PlayerHolder(context).also { holderRef = it }

    var videoId: String? by mutableStateOf(null)
        private set

    var minimized: Boolean by mutableStateOf(false)
        private set

    var cachedDetails: VideoDetails? by mutableStateOf(null)
        private set

    var cachedStream: VideoStream? by mutableStateOf(null)
        private set

    var selectedVideo: VideoVariant? by mutableStateOf(null)
        private set

    var selectedAudio: AudioTrackOption? by mutableStateOf(null)
        private set

    var selectionInitialized: Boolean by mutableStateOf(false)
        private set

    val canMinimize: Boolean
        get() = videoId != null && holderRef?.isReady == true

    /** Перше відкриття відео або перехід на інше; те саме відео — шлях розгортання. */
    fun begin(videoId: String) {
        if (this.videoId != videoId) {
            holder.stop()
            this.videoId = videoId
            cachedDetails = null
            cachedStream = null
            selectedVideo = null
            selectedAudio = null
            selectionInitialized = false
        }
        minimized = false
    }

    fun cache(videoId: String, details: VideoDetails, stream: VideoStream) {
        if (this.videoId != videoId) return
        cachedDetails = details
        cachedStream = stream
    }

    fun updateSelection(video: VideoVariant?, audio: AudioTrackOption?) {
        selectedVideo = video
        selectedAudio = audio
        selectionInitialized = true
    }

    fun minimize(): Boolean {
        if (!canMinimize) return false
        minimized = true
        return true
    }

    fun expand() {
        minimized = false
    }

    fun stop() {
        holderRef?.stop()
        videoId = null
        minimized = false
        cachedDetails = null
        cachedStream = null
        selectedVideo = null
        selectedAudio = null
        selectionInitialized = false
    }

    override fun onCleared() {
        holderRef?.release()
        holderRef = null
        super.onCleared()
    }
}
