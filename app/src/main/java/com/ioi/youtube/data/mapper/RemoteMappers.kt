package com.ioi.youtube.data.mapper

import com.ioi.youtube.data.model.RawAudioTrack
import com.ioi.youtube.data.model.RawComment
import com.ioi.youtube.data.model.RawVideoDetails
import com.ioi.youtube.data.model.RawVideoStream
import com.ioi.youtube.data.model.RawVideoSummary
import com.ioi.youtube.data.model.RawVideoVariant
import com.ioi.youtube.domain.model.AudioTrackOption
import com.ioi.youtube.domain.model.Comment
import com.ioi.youtube.domain.model.defaultAudio
import com.ioi.youtube.domain.model.defaultVideo
import com.ioi.youtube.domain.model.chooseAudioTracks
import com.ioi.youtube.domain.model.chooseVideoVariants
import com.ioi.youtube.domain.model.audioLabel
import com.ioi.youtube.domain.model.VideoDetails
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoVariant
import com.ioi.youtube.domain.model.VideoSummary

fun RawVideoSummary.toDomain(): VideoSummary = VideoSummary(
    id = id,
    title = title,
    uploaderName = uploaderName,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    viewCount = viewCount,
    uploadDate = uploadDate,
    uploadedAt = uploadedAt,
)

fun RawVideoDetails.toDomain(): VideoDetails = VideoDetails(
    id = id,
    title = title,
    uploaderName = uploaderName,
    uploaderAvatarUrl = uploaderAvatarUrl,
    description = description,
    viewCount = viewCount,
    likeCount = likeCount,
    uploadDate = uploadDate,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    recommendedVideos = recommended.map { it: RawVideoSummary -> it.toDomain() },
)

fun RawComment.toDomain(): Comment = Comment(
    id = id,
    authorName = authorName,
    authorAvatarUrl = authorAvatarUrl,
    text = text,
    likeCount = likeCount,
    publishedTime = publishedTime,
    heartedByUploader = heartedByUploader,
    pinned = pinned,
)

fun RawVideoStream.toDomain(): VideoStream = VideoStream(
    url = url,
    resolution = resolution,
    format = format,
    hlsUrl = hlsUrl,
    videoOnly = chooseVideoVariants(videoOnly.map { it: RawVideoVariant -> it.toDomain() }),
    audioTracks = chooseAudioTracks(
        audioTracks.mapIndexed { index, track -> track.toDomain(index + 1) },
    ),
)

fun RawVideoVariant.toDomain(): VideoVariant = VideoVariant(
    url = url,
    height = height,
    bitrate = bitrate,
)

fun RawAudioTrack.toDomain(index: Int = 1): AudioTrackOption = AudioTrackOption(
    id = id,
    label = audioLabel(language, name, original, index),
    language = language,
    url = url,
    bitrate = bitrate,
    original = original,
)
