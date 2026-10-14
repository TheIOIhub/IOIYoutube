package com.ioi.youtube.domain.usecase

import com.ioi.youtube.domain.model.Comment
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.repository.CommentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCommentsUseCase @Inject constructor(
    private val commentRepository: CommentRepository,
) {
    operator fun invoke(videoId: String): Flow<UiState<List<Comment>>> =
        commentRepository.getComments(videoId)
}
