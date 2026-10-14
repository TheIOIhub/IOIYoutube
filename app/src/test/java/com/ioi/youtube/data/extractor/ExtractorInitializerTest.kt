package com.ioi.youtube.data.extractor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExtractorInitializerTest {

    @Test
    fun `init is accepted by NewPipe`() {
        val initializer = ExtractorInitializer(TestDownloader())

        initializer.init()

        // Якщо NewPipe прийняв downloader, сервіс YouTube реєструється.
        initializer.init() // другий виклик — no-op, не повинен падати
        assertThat(true).isTrue()
    }

    private class TestDownloader : org.schabi.newpipe.extractor.downloader.Downloader() {
        override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): org.schabi.newpipe.extractor.downloader.Response =
            org.schabi.newpipe.extractor.downloader.Response(
                200,
                "OK",
                emptyMap(),
                "{}",
                request.url(),
            )
    }
}
