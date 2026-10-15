package com.ioi.youtube

import android.app.Application
import com.ioi.youtube.data.extractor.ExtractorInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class IOIYoutubeApplication : Application() {

    @Inject
    lateinit var extractorInitializer: ExtractorInitializer

    override fun onCreate() {
        super.onCreate()
        extractorInitializer.init()
    }
}
