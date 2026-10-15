package com.ioi.youtube.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ioi.youtube.domain.model.VideoSummary
import com.ioi.youtube.domain.util.Formatters

@Composable
fun VideoCard(
    video: VideoSummary,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(video.id) },
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        )
        Row(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${video.uploaderName} · ${Formatters.formatViews(video.viewCount)} · ${Formatters.formatDuration(video.durationSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!video.uploadDate.isNullOrBlank()) {
                Text(
                    text = video.uploadDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
