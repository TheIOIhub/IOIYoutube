package com.ioi.youtube.data.remote

import com.ioi.youtube.data.model.RawComment
import com.ioi.youtube.data.model.RawVideoDetails
import com.ioi.youtube.data.model.RawVideoStream
import com.ioi.youtube.data.model.RawVideoSummary
import com.ioi.youtube.util.HtmlText
import com.ioi.youtube.util.YouTubeUrlParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.stream.Description
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

    /**
     * Стрiчка «Головна».
     *
     * У NewPipeExtractor 0.26.5 (i в master) кiоск `Trending` зламаний: YouTube вiдповiдає
     * HTTP 400 на browse-запит `FEtrending`, а анонiмна домашня стрiчка YouTube
     * (`FEwhat_to_watch`) порожня за задумом. Тому стрiчку збираємо самi — паралельними
     * пошуковими запитами за категорiями, чергуючи результати (щоб категорiї перемiшувались).
     */
    override suspend fun fetchHomeFeed(): List<RawVideoSummary> = withContext(Dispatchers.IO) {
        val pages = coroutineScope {
            FEED_CATEGORIES.map { category ->
                async { runCatching { searchPage(category) } }
            }.awaitAll()
        }
        val loaded = pages.mapNotNull { it.getOrNull() }
        if (loaded.isEmpty()) {
            // Жодна категорiя не завантажилась — віддаємо помилку вгору (репозиторiй покаже її/кеш).
            throw pages.firstNotNullOf { it.exceptionOrNull() }
        }
        loaded.interleave().distinctBy { it.id }.take(FEED_SIZE)
    }

    override suspend fun search(query: String): List<RawVideoSummary> = withContext(Dispatchers.IO) {
        searchPage(query)
    }

    private fun searchPage(query: String): List<RawVideoSummary> {
        val extractor = youtube.getSearchExtractor(query)
        // NewPipe НЕ завантажує сторiнку сам: без fetchPage() initialPage падає з NPE.
        extractor.fetchPage()
        return extractor.initialPage.items
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
                description = info.description.asPlainText(),
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
                // HLS дає всі якості (144p…1080p) і адаптивне перемикання;
                // progressive лишається запасним варіантом.
                hlsUrl = info.hlsUrl?.takeIf { it.isNotBlank() },
            )
        }

    override suspend fun fetchShorts(): List<RawVideoSummary> = withContext(Dispatchers.IO) {
        shortsFromChannels(channelsFrom(fetchHomeFeed()))
    }

    override suspend fun fetchMoreShorts(): List<RawVideoSummary> = withContext(Dispatchers.IO) {
        // Iнша тема → iншi канали → iншi шортси: стрiчка не закiнчується.
        shortsFromChannels(channelsFrom(search(SHORTS_QUERIES.random())))
    }

    private fun channelsFrom(videos: List<RawVideoSummary>): List<String> =
        videos.mapNotNull { it.uploaderUrl.takeIf(String::isNotBlank) }
            .distinct()
            .take(SHORTS_CHANNEL_LIMIT)

    private suspend fun shortsFromChannels(channels: List<String>): List<RawVideoSummary> {
        if (channels.isEmpty()) return emptyList()
        val pages = coroutineScope {
            channels.map { channel -> async { runCatching { shortsOfChannel(channel) } } }.awaitAll()
        }
        val loaded = pages.mapNotNull { it.getOrNull() }
        if (loaded.isEmpty()) {
            val failure = pages.firstNotNullOfOrNull { it.exceptionOrNull() } ?: return emptyList()
            throw failure
        }
        return loaded.flatten().distinctBy { it.id }.shuffled()
    }

    /** Вкладка Shorts каналу; канал без неї дає порожнiй список. */
    private fun shortsOfChannel(channelUrl: String): List<RawVideoSummary> {
        val channel = youtube.getChannelExtractor(channelUrl)
        channel.fetchPage()
        val shortsTab = channel.tabs.firstOrNull { it.url.orEmpty().contains(SHORTS_TAB_MARKER) }
            ?: return emptyList()
        val tabExtractor = youtube.getChannelTabExtractor(shortsTab)
        tabExtractor.fetchPage()
        return tabExtractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .filter { it.isShortFormContent }
            .map { it.toRawSummary() }
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
        uploaderUrl = uploaderUrl.orEmpty(),
    )

    private fun CommentsInfoItem.toRawComment() = RawComment(
        id = commentId.orEmpty(),
        authorName = uploaderName.orEmpty(),
        authorAvatarUrl = uploaderAvatars.lastOrNull()?.url,
        text = commentText.asPlainText(),
        likeCount = if (likeCount >= 0) likeCount.toLong() else 0L,
        publishedTime = textualUploadDate,
        heartedByUploader = isHeartedByUploader,
        pinned = isPinned,
    )

    /** NewPipe вiддає опис/коментар як HTML — для UI потрiбен звичайнiй текст. */
    private fun Description?.asPlainText(): String = when {
        this == null -> ""
        type == Description.HTML -> HtmlText.toPlainText(content.orEmpty())
        else -> content.orEmpty()
    }

    /** Чергує сторiнки категорiй, щоб у стрiчцi сусiднi вiдео були з рiзних категорiй. */
    private fun List<List<RawVideoSummary>>.interleave(): List<RawVideoSummary> {
        val merged = mutableListOf<RawVideoSummary>()
        val longest = maxOfOrNull { it.size } ?: 0
        for (index in 0 until longest) {
            forEach { page -> page.getOrNull(index)?.let(merged::add) }
        }
        return merged
    }

    companion object {
        /** Скільки вiдео показуємо на «Головнiй». */
        internal const val FEED_SIZE = 30

        /**
         * Категорiї стрiчки (аналог YouTube Explore): кiоск Trending недоступний,
         * тому кожна категорiя — окремий пошуковий запит.
         */
        internal val FEED_CATEGORIES = listOf("музика", "ігри", "спорт", "фільми", "технології")

        /** Скiльки каналiв тягнемо для стрiчки Shorts. */
        internal const val SHORTS_CHANNEL_LIMIT = 6

        /** Ознака вкладки Shorts у списку вкладок каналу. */
        internal const val SHORTS_TAB_MARKER = "/shorts"

        /** Теми для «гортати далi»: нова тема → новi канали → новi шортси. */
        internal val SHORTS_QUERIES = listOf(
            "музика", "гумор", "тварини", "спорт", "ігри", "кулінарія", "авто", "подорожі",
        )
    }
}
