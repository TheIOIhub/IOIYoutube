package com.ioi.youtube.presentation.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.domain.model.AudioTrackOption
import com.ioi.youtube.domain.model.VideoStream
import com.ioi.youtube.domain.model.VideoVariant
import com.ioi.youtube.testutil.TestData
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackViewModelTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: ViewModelStore
    private lateinit var viewModel: PlaybackViewModel

    private val details = TestData.details("dQw4w9WgXcQ")
    private val video = VideoVariant("https://v/1080.mp4", 1080, 2_000_000)
    private val audio = AudioTrackOption(
        "uk.1",
        "Українська · дубляж",
        "uk",
        "https://a/uk",
        128_000,
        false,
    )
    private val stream = VideoStream(
        url = "https://v/360.mp4",
        resolution = "360p",
        format = "MPEG-4",
        videoOnly = listOf(video),
        audioTracks = listOf(audio),
    )

    @Before
    fun setUp() {
        store = ViewModelStore()
        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        store.clear()
    }

    private fun createViewModel(): PlaybackViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PlaybackViewModel(context) as T
        }
        return ViewModelProvider(store, factory)[PlaybackViewModel::class.java]
    }

    @Test
    fun `begin with a new id clears cache and selection`() {
        viewModel.begin("a")
        viewModel.cache("a", details, stream)
        viewModel.updateSelection(video, audio)

        viewModel.begin("b")

        assertThat(viewModel.videoId).isEqualTo("b")
        assertThat(viewModel.cachedDetails).isNull()
        assertThat(viewModel.cachedStream).isNull()
        assertThat(viewModel.selectedVideo).isNull()
        assertThat(viewModel.selectedAudio).isNull()
        assertThat(viewModel.selectionInitialized).isFalse()
    }

    @Test
    fun `begin with the same id keeps cache and selection`() {
        viewModel.begin("a")
        viewModel.cache("a", details, stream)
        viewModel.updateSelection(video, audio)
        val holder = viewModel.holder

        viewModel.begin("a")

        assertThat(viewModel.cachedDetails).isSameInstanceAs(details)
        assertThat(viewModel.selectedVideo).isEqualTo(video)
        assertThat(viewModel.selectedAudio).isEqualTo(audio)
        assertThat(viewModel.selectionInitialized).isTrue()
        assertThat(viewModel.holder).isSameInstanceAs(holder)
    }

    @Test
    fun `cache ignores a different video id`() {
        viewModel.begin("a")

        viewModel.cache("b", details, stream)

        assertThat(viewModel.cachedDetails).isNull()
        assertThat(viewModel.cachedStream).isNull()
    }

    @Test
    fun `minimize requires ready playback`() {
        viewModel.begin("a")
        assertThat(viewModel.minimize()).isFalse()
        assertThat(viewModel.minimized).isFalse()

        viewModel.holder.update(stream, video, audio, auto = false)
        assertThat(viewModel.minimize()).isTrue()
        assertThat(viewModel.minimized).isTrue()

        viewModel.expand()
        assertThat(viewModel.minimized).isFalse()
    }

    @Test
    fun `stop clears the session and readiness`() {
        viewModel.begin("a")
        viewModel.cache("a", details, stream)
        viewModel.updateSelection(video, audio)
        viewModel.holder.update(stream, video, audio, auto = false)
        assertThat(viewModel.minimize()).isTrue()

        viewModel.stop()

        assertThat(viewModel.videoId).isNull()
        assertThat(viewModel.minimized).isFalse()
        assertThat(viewModel.cachedDetails).isNull()
        assertThat(viewModel.selectedVideo).isNull()
        assertThat(viewModel.selectionInitialized).isFalse()
        assertThat(viewModel.canMinimize).isFalse()
    }

    @Test
    fun `clearing the view model releases the holder`() {
        viewModel.begin("a")
        val holder = viewModel.holder

        store.clear()

        assertThat(holder.isReleased).isTrue()
    }
}
