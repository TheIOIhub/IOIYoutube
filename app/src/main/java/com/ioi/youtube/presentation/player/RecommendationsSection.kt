package com.ioi.youtube.presentation.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ioi.youtube.domain.model.VideoSummary

@Composable
fun RecommendationsSection(
    videos: List<VideoSummary>,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (videos.isEmpty()) return
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(
            text = "Рекомендації",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        LazyRow {
            items(items = videos, key = { it.id }) { video ->
                RecommendationCard(
                    video = video,
                    onClick = onVideoClick,
                    modifier = Modifier
                        .width(168.dp)
                        .padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    video: VideoSummary,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable { onClick(video.id) }) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.small),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = video.title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = video.uploaderName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
