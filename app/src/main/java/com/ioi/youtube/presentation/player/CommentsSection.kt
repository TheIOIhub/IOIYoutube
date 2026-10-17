package com.ioi.youtube.presentation.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ioi.youtube.domain.model.Comment
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.domain.util.Formatters

@Composable
fun CommentsSection(
    state: UiState<List<Comment>>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(12.dp)) {
        Text(text = "Коментарі", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        when (state) {
            is UiState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Завантаження…", style = MaterialTheme.typography.bodySmall)
            }

            is UiState.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.message,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onRetry) {
                    Text("Спробувати знову")
                }
            }

            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    Text(
                        text = "Коментарів немає",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.data.forEach { comment ->
                        CommentItem(comment = comment)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentItem(
    comment: Comment,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        AsyncImage(
            model = comment.authorAvatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = comment.authorName, style = MaterialTheme.typography.labelLarge)
                if (comment.pinned) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Закріплено",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(text = comment.text, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (comment.publishedTime != null) {
                    Text(
                        text = comment.publishedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = Formatters.formatCount(comment.likeCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (comment.heartedByUploader) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "♥ автором",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
