package com.ioi.youtube.testutil

import com.ioi.youtube.domain.model.Comment
import com.ioi.youtube.domain.model.VideoDetails
import com.ioi.youtube.domain.model.VideoSummary

object TestData {

    fun video(id: String = "dQw4w9WgXcQ", title: String = "Video $id"): VideoSummary =
        VideoSummary(
            id = id,
            title = title,
            uploaderName = "Test Channel",
            thumbnailUrl = "https://example.com/$id.jpg",
            durationSeconds = 212,
            viewCount = 1_500_000,
            uploadDate = "2 years ago",
        )

    fun details(id: String = "dQw4w9WgXcQ"): VideoDetails =
        VideoDetails(
            id = id,
            title = "Video $id",
            uploaderName = "Test Channel",
            uploaderAvatarUrl = "https://example.com/avatar.jpg",
            description = "Description of video $id",
            viewCount = 1_500_000,
            likeCount = 42_000,
            uploadDate = "2 years ago",
            thumbnailUrl = "https://example.com/$id.jpg",
            durationSeconds = 212,
            recommendedVideos = listOf(video("aaaaaaaaaaa"), video("bbbbbbbbbbb")),
        )

    fun comments(): List<Comment> = listOf(
        Comment(
            id = "c1",
            authorName = "Alice",
            authorAvatarUrl = null,
            text = "First!",
            likeCount = 150,
            publishedTime = "1 day ago",
            heartedByUploader = true,
            pinned = true,
        ),
        Comment(
            id = "c2",
            authorName = "Bob",
            authorAvatarUrl = null,
            text = "Nice video",
            likeCount = 42,
            publishedTime = "2 days ago",
            heartedByUploader = false,
            pinned = false,
        ),
    )
}
