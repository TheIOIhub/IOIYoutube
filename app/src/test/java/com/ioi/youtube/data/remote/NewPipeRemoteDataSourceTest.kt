package com.ioi.youtube.data.remote

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
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.kiosk.KioskExtractor
import org.schabi.newpipe.extractor.kiosk.KioskList
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Контракт: NewPipe-екстрактор не завантажує сторінку сам — `fetchPage()` мусить бути
 * викликаний ДО `getInitialPage()`, iнакше `initialPage` падає з NPE на null-відповiдi
 * (перевiрено на реальнiй мережi: без `fetchPage()` летить NPE, з ним — 15/20 елементiв).
 */
class NewPipeRemoteDataSourceTest {

    @After
    fun tearDown() {
        unmockkStatic(NewPipe::class)
    }

    private fun stubService(
        kiosk: KioskExtractor<StreamInfoItem>? = null,
        search: SearchExtractor? = null,
    ): StreamingService {
        val service = mockk<StreamingService>(relaxed = true)
        if (kiosk != null) {
            val kioskList = mockk<KioskList>(relaxed = true)
            every { kioskList.defaultKioskExtractor } returns kiosk
            every { service.kioskList } returns kioskList
        }
        if (search != null) {
            every { service.getSearchExtractor(any<String>()) } returns search
        }
        mockkStatic(NewPipe::class)
        every { NewPipe.getService(any<Int>()) } returns service
        every { NewPipe.getService(any<String>()) } returns service
        return service
    }

    private fun <T : InfoItem> emptyPage(): ListExtractor.InfoItemsPage<T> =
        ListExtractor.InfoItemsPage(emptyList<T>(), null, emptyList())

    @Test
    fun `home feed fetches the kiosk page before reading its items`() = runTest {
        val kiosk = mockk<KioskExtractor<StreamInfoItem>>(relaxed = true)
        every { kiosk.initialPage } returns emptyPage<StreamInfoItem>()
        stubService(kiosk = kiosk)

        val feed = NewPipeRemoteDataSource().fetchHomeFeed()

        verifyOrder {
            kiosk.fetchPage()
            kiosk.initialPage
        }
        verify(exactly = 1) { kiosk.initialPage }
        check(feed.isEmpty())
    }

    @Test
    fun `search fetches the page before reading its items`() = runTest {
        val search = mockk<SearchExtractor>(relaxed = true)
        every { search.initialPage } returns emptyPage<InfoItem>()
        stubService(search = search)

        val results = NewPipeRemoteDataSource().search("kotlin")

        verifyOrder {
            search.fetchPage()
            search.initialPage
        }
        verify(exactly = 1) { search.initialPage }
        check(results.isEmpty())
    }
}
