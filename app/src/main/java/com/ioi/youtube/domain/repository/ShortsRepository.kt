package com.ioi.youtube.domain.repository

import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.model.VideoSummary
import kotlinx.coroutines.flow.Flow

interface ShortsRepository {
    fun getShorts(): Flow<UiState<List<VideoSummary>>>

    /** Ще шортси для «гортати далi» — окремий запит, не наступна сторiнка каналу. */
    fun getMoreShorts(): Flow<UiState<List<VideoSummary>>>
}
