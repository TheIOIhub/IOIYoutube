package com.ioi.youtube.data.extractor

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

class ExtractorInitializerTest {

    @Test
    fun `init registers downloader with NewPipe exactly once and performs no requests`() {
        val downloader = TestDownloader()
        val initializer = ExtractorInitializer(downloader)

        initializer.init()
        initializer.init() // second call must be a no-op

        assertThat(NewPipe.getDownloader()).isSameInstanceAs(downloader)
        assertThat(NewPipe.getService(ServiceList.YouTube.serviceId)).isNotNull()
        assertThat(downloader.executeCalls).isEqualTo(0)
    }

    private class TestDownloader : Downloader() {
        var executeCalls = 0
            private set

        override fun execute(request: Request): Response {
            executeCalls++
            return Response(200, "OK", emptyMap(), "{}", request.url())
        }
    }
}
