package com.ioi.youtube.domain.repository

import com.ioi.youtube.domain.model.Comment
import com.ioi.youtube.domain.model.UiState
import kotlinx.coroutines.flow.Flow

interface CommentRepository {
    fun getComments(videoId: String): Flow<UiState<List<Comment>>>
}
