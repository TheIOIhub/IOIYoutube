package com.ioi.youtube.data.remote

import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.io.IOException

/**
 * Контракт роботи з NewPipe без мережi.
 *
 * Найважливiше: NewPipe-екстрактор не завантажує сторiнку сам — `fetchPage()` мусить бути
 * викликаний ДО `initialPage`, iнакше останнiй падає з NPE на null-вiдповiдi (перевiрено
 * на реальнiй мережi: без `fetchPage()` — NPE i жодного HTTP-запиту).
 */
class NewPipeRemoteDataSourceTest {

    @After
    fun tearDown() {
        unmockkStatic(NewPipe::class, StreamInfo::class, CommentsInfo::class)
    }

    private fun stubYouTubeService(service: StreamingService) {
        mockkStatic(NewPipe::class)
        every { NewPipe.getService(any<Int>()) } returns service
        every { NewPipe.getService(any<String>()) } returns service
    }

    private fun item(id: String, name: String): StreamInfoItem =
        StreamInfoItem(
            ServiceList.YouTube.serviceId,
            "https://www.youtube.com/watch?v=$id",
            name,
            StreamType.VIDEO_STREAM,
        ).apply { thumbnails = emptyList() }

    private fun searchExtractor(
        items: List<StreamInfoItem> = emptyList(),
        failWith: Throwable? = null,
    ): SearchExtractor {
        val extractor = mockk<SearchExtractor>(relaxed = true)
        if (failWith != null) {
            every { extractor.fetchPage() } throws failWith
        } else {
            every { extractor.initialPage } returns emptyPage(items)
        }
        return extractor
    }

    private fun <T : InfoItem> emptyPage(items: List<T>): ListExtractor.InfoItemsPage<T> =
        ListExtractor.InfoItemsPage(items, null, emptyList())

    @Test
    fun `home feed searches every category, fetches pages first and interleaves results`() = runTest {
        val music = searchExtractor(listOf(item("aaaaaaaaaaa", "Music"), item("bbbbbbbbbbb", "Shared")))
        val games = searchExtractor(listOf(item("ccccccccccc", "Game"), item("bbbbbbbbbbb", "Shared")))
        val empty = searchExtractor()
        val service = mockk<StreamingService>(relaxed = true)
        every { service.getSearchExtractor(any<String>()) } returns empty
        every { service.getSearchExtractor("музика") } returns music
        every { service.getSearchExtractor("ігри") } returns games
        stubYouTubeService(service)

        val feed = NewPipeRemoteDataSource().fetchHomeFeed()

        // Чергування категорiй + прибранi дублiкати.
        assertThat(feed.map { it.id }).containsExactly("aaaaaaaaaaa", "ccccccccccc", "bbbbbbbbbbb").inOrder()
        assertThat(feed.map { it.title }).containsExactly("Music", "Game", "Shared").inOrder()
        for (category in NewPipeRemoteDataSource.FEED_CATEGORIES) {
            verify(exactly = 1) { service.getSearchExtractor(category) }
        }
        verifyOrder { music.fetchPage(); music.initialPage }
        verifyOrder { games.fetchPage(); games.initialPage }
        verifyOrder { empty.fetchPage(); empty.initialPage }
    }

    @Test
    fun `home feed keeps working when some categories fail`() = runTest {
        val music = searchExtractor(listOf(item("aaaaaaaaaaa", "Music")))
        val service = mockk<StreamingService>(relaxed = true)
        every { service.getSearchExtractor(any<String>()) } returns
            searchExtractor(failWith = IOException("offline"))
        every { service.getSearchExtractor("музика") } returns music
        stubYouTubeService(service)

        val feed = NewPipeRemoteDataSource().fetchHomeFeed()

        assertThat(feed.map { it.id }).containsExactly("aaaaaaaaaaa")
    }

    @Test
    fun `home feed reports failure when every category fails`() = runTest {
        val service = mockk<StreamingService>(relaxed = true)
        every { service.getSearchExtractor(any<String>()) } returns
            searchExtractor(failWith = IOException("offline"))
        stubYouTubeService(service)

        val thrown = runCatching { NewPipeRemoteDataSource().fetchHomeFeed() }.exceptionOrNull()

        assertThat(thrown).isInstanceOf(IOException::class.java)
        assertThat(thrown).hasMessageThat().isEqualTo("offline")
    }

    @Test
    fun `search fetches the page before reading its items`() = runTest {
        val search = searchExtractor(listOf(item("ddddddddddd", "Result")))
        val service = mockk<StreamingService>(relaxed = true)
        every { service.getSearchExtractor("kotlin") } returns search
        stubYouTubeService(service)

        val results = NewPipeRemoteDataSource().search("kotlin")

        assertThat(results.map { it.id }).containsExactly("ddddddddddd")
        verifyOrder { search.fetchPage(); search.initialPage }
    }

    @Test
    fun `video details convert html description to plain text`() = runTest {
        val info = mockk<StreamInfo>(relaxed = true)
        every { info.name } returns "Title"
        every { info.uploaderName } returns "Uploader"
        every { info.uploaderAvatars } returns emptyList()
        every { info.thumbnails } returns emptyList()
        every { info.relatedItems } returns emptyList()
        every { info.description } returns Description(
            "Hi<br>Join <a href=\"https://x\">https://x</a> &amp; bye",
            Description.HTML,
        )
        mockkStatic(StreamInfo::class)
        every { StreamInfo.getInfo(any<String>()) } returns info
        stubYouTubeService(mockk<StreamingService>(relaxed = true))

        val details = NewPipeRemoteDataSource().fetchVideoDetails("dQw4w9WgXcQ")

        assertThat(details.description).isEqualTo("Hi\nJoin https://x & bye")
    }

    @Test
    fun `comments convert html text to plain text`() = runTest {
        val comment = mockk<CommentsInfoItem>(relaxed = true)
        every { comment.commentText } returns Description(
            "If you&apos;re <a href=\"https://x\">in</a>",
            Description.HTML,
        )
        every { comment.uploaderAvatars } returns emptyList()
        val info = mockk<CommentsInfo>(relaxed = true)
        every { info.isCommentsDisabled } returns false
        every { info.relatedItems } returns listOf(comment)
        mockkStatic(CommentsInfo::class)
        every { CommentsInfo.getInfo(any<String>()) } returns info
        stubYouTubeService(mockk<StreamingService>(relaxed = true))

        val comments = NewPipeRemoteDataSource().fetchComments("dQw4w9WgXcQ")

        assertThat(comments.map { it.text }).containsExactly("If you're in")
    }

    @Test
    fun `plain text description is not decoded as html`() = runTest {
        val info = mockk<StreamInfo>(relaxed = true)
        every { info.uploaderAvatars } returns emptyList()
        every { info.thumbnails } returns emptyList()
        every { info.relatedItems } returns emptyList()
        every { info.description } returns Description("a < b & c", Description.PLAIN_TEXT)
        mockkStatic(StreamInfo::class)
        every { StreamInfo.getInfo(any<String>()) } returns info
        stubYouTubeService(mockk<StreamingService>(relaxed = true))

        val details = NewPipeRemoteDataSource().fetchVideoDetails("dQw4w9WgXcQ")

        assertThat(details.description).isEqualTo("a < b & c")
    }
}
