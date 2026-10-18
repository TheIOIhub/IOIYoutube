package com.ioi.youtube.data.remote

import com.ioi.youtube.data.model.RawComment
import com.ioi.youtube.data.model.RawVideoDetails
import com.ioi.youtube.data.model.RawVideoStream
import com.ioi.youtube.data.model.RawVideoSummary
import com.ioi.youtube.util.YouTubeUrlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реалізація YouTubeRemoteDataSource на NewPipe Extractor 0.26.5.
 * NewPipe повертає посилання на відеопотік без рекламних сегментiв (спека §1).
 */
@Singleton
class NewPipeRemoteDataSource @Inject constructor() : YouTubeRemoteDataSource {

    private val youtube: StreamingService
        get() = NewPipe.getService(ServiceList.YouTube.serviceId)

    override suspend fun fetchHomeFeed(): List<RawVideoSummary> = withContext(Dispatchers.IO) {
        val extractor = youtube.kioskList.getDefaultKioskExtractor()
        // NewPipe НЕ завантажує сторiнку сам: без fetchPage() initialPage падає з NPE.
        extractor.fetchPage()
        extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .map { it.toRawSummary() }
    }

    override suspend fun search(query: String): List<RawVideoSummary> = withContext(Dispatchers.IO) {
        val extractor = youtube.getSearchExtractor(query)
        extractor.fetchPage()
        extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .map { it.toRawSummary() }
    }

    override suspend fun fetchVideoDetails(videoId: String): RawVideoDetails =
        withContext(Dispatchers.IO) {
            val info = StreamInfo.getInfo(YouTubeUrlParser.buildWatchUrl(videoId))
            RawVideoDetails(
                id = videoId,
                title = info.name.orEmpty(),
                uploaderName = info.uploaderName.orEmpty(),
                uploaderAvatarUrl = info.uploaderAvatars.lastOrNull()?.url,
                description = info.description?.content.orEmpty(),
                viewCount = info.viewCount.orZero(),
                likeCount = info.likeCount.orZero(),
                uploadDate = info.textualUploadDate,
                thumbnailUrl = info.thumbnails.lastOrNull()?.url.orEmpty(),
                durationSeconds = info.duration.orZero(),
                recommended = info.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toRawSummary() },
            )
        }

    override suspend fun fetchComments(videoId: String): List<RawComment> =
        withContext(Dispatchers.IO) {
            val info = CommentsInfo.getInfo(YouTubeUrlParser.buildWatchUrl(videoId))
                ?: return@withContext emptyList()
            if (info.isCommentsDisabled) return@withContext emptyList()
            info.relatedItems
                .filterIsInstance<CommentsInfoItem>()
                .map { it.toRawComment() }
        }

    override suspend fun fetchVideoStream(videoId: String): RawVideoStream =
        withContext(Dispatchers.IO) {
            val info = StreamInfo.getInfo(YouTubeUrlParser.buildWatchUrl(videoId))
            val stream = pickBestVideoStream(info)
            RawVideoStream(
                url = stream.url.orEmpty(),
                resolution = stream.resolution.orEmpty(),
                format = stream.format?.name.orEmpty(),
            )
        }

    private fun pickBestVideoStream(info: StreamInfo): VideoStream {
        // Progressive-потоки (відео + аудіо в одному файлi) — ExoPlayer грає їх напряму.
        val candidates = info.videoStreams.filterNot { it.isVideoOnly }
        return candidates.maxWithOrNull(
            compareBy<VideoStream> { it.resolutionPixels() }.thenBy { it.bitrate },
        ) ?: throw NoSuchElementException("No progressive video stream for video ${info.id}")
    }

    private fun VideoStream.resolutionPixels(): Int =
        resolution?.substringBefore('p')?.substringBefore('P')?.toIntOrNull() ?: 0

    /** NewPipe позначає вiдсутнi значення як -1. */
    private fun Long.orZero(): Long = if (this < 0) 0 else this

    private fun StreamInfoItem.toRawSummary() = RawVideoSummary(
        id = YouTubeUrlParser.extractVideoId(url).orEmpty(),
        title = name.orEmpty(),
        uploaderName = uploaderName.orEmpty(),
        thumbnailUrl = thumbnails.lastOrNull()?.url.orEmpty(),
        durationSeconds = duration.orZero(),
        viewCount = viewCount.orZero(),
        uploadDate = textualUploadDate,
    )

    private fun CommentsInfoItem.toRawComment() = RawComment(
        id = commentId.orEmpty(),
        authorName = uploaderName.orEmpty(),
        authorAvatarUrl = uploaderAvatars.lastOrNull()?.url,
        text = commentText.content,
        likeCount = if (likeCount >= 0) likeCount.toLong() else 0L,
        publishedTime = textualUploadDate,
        heartedByUploader = isHeartedByUploader,
        pinned = isPinned,
    )
}
