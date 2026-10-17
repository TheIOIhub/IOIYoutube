package com.ioi.youtube.presentation.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ioi.youtube.domain.model.Comment
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoDetails
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.usecase.GetCommentsUseCase
import com.ioi.youtube.domain.usecase.GetVideoDetailsUseCase
import com.ioi.youtube.domain.usecase.GetVideoStreamUseCase
import com.ioi.youtube.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class PlayerUiState(
    val video: UiState<VideoDetails> = UiState.Loading,
    val stream: UiState<VideoStream> = UiState.Loading,
    val comments: UiState<List<Comment>> = UiState.Loading,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getVideoDetails: GetVideoDetailsUseCase,
    private val getVideoStream: GetVideoStreamUseCase,
    private val getComments: GetCommentsUseCase,
) : ViewModel() {

    val videoId: String = savedStateHandle.get<String>(Routes.ARG_VIDEO_ID).orEmpty()

    private val refreshTrigger = MutableStateFlow(0)

    val uiState: StateFlow<PlayerUiState> = refreshTrigger
        .flatMapLatest {
            combine(
                getVideoDetails(videoId),
                getVideoStream(videoId),
                getComments(videoId),
            ) { video, stream, comments ->
                PlayerUiState(video = video, stream = stream, comments = comments)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlayerUiState(),
        )

    fun retry() {
        refreshTrigger.update { it + 1 }
    }
}
