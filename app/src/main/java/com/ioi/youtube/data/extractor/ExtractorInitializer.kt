package com.ioi.youtube.data.extractor

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Викликає NewPipe.init(downloader) рівно один раз за життя процесу.
 * Має бути викликаний до першого звернення до extractor (див. Task 2.12).
 */
@Singleton
class ExtractorInitializer @Inject constructor(
    private val downloader: Downloader,
) {
    @Volatile
    private var initialized = false

    fun init() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(downloader)
            initialized = true
        }
    }
}
