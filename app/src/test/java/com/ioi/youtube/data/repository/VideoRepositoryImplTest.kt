package com.ioi.youtube.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.ioi.youtube.data.local.CachedVideoDao
import com.ioi.youtube.data.local.CachedVideoDetailsDao
import com.ioi.youtube.data.local.CachedVideoEntity
import com.ioi.youtube.data.local.CachedVideoDetailsEntity
import com.ioi.youtube.data.local.IOIDatabase
import com.ioi.youtube.data.mapper.toDetailsEntity
import com.ioi.youtube.data.model.RawVideoDetails
import com.ioi.youtube.data.model.RawVideoStream
import com.ioi.youtube.data.model.RawVideoSummary
import com.ioi.youtube.domain.model.UiState
import com.ioi.youtube.testutil.FakeRemoteDataSource
import com.ioi.youtube.testutil.TestData
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoRepositoryImplTest {

    private lateinit var database: IOIDatabase
    private lateinit var videoDao: CachedVideoDao
    private lateinit var detailsDao: CachedVideoDetailsDao
    private lateinit var remote: FakeRemoteDataSource
    private lateinit var repository: VideoRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, IOIDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        videoDao = database.cachedVideoDao()
        detailsDao = database.cachedVideoDetailsDao()
        remote = FakeRemoteDataSource()
        repository = VideoRepositoryImpl(remote, videoDao, detailsDao)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `home feed emits cached then fresh`() = runTest {
        remote.feed = listOf(rawVideo("fresh1"))
        videoDao.replaceSource(VideoRepositoryImpl.SOURCE_HOME, listOf(videoEntity("cached1")))

        repository.getHomeFeed().test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)

            val cached = awaitItem() as UiState.Success
            assertThat(cached.data.map { it.id }).containsExactly("cached1")
            assertThat(cached.fromCache).isTrue()

            val fresh = awaitItem() as UiState.Success
            assertThat(fresh.data.map { it.id }).containsExactly("fresh1")
            assertThat(fresh.fromCache).isFalse()

            awaitComplete()
        }
    }

    @Test
    fun `home feed persists fresh data to cache`() = runTest {
        remote.feed = listOf(rawVideo("fresh1"), rawVideo("fresh2"))

        repository.getHomeFeed().test {
            awaitItem()
            awaitItem()
            awaitComplete()
        }

        assertThat(videoDao.getBySource(VideoRepositoryImpl.SOURCE_HOME).map { it.id })
            .containsExactly("fresh1", "fresh2")
    }

    @Test
    fun `home feed emits error when network fails and cache is empty`() = runTest {
        remote.feedError = IOException("offline")

        repository.getHomeFeed().test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val error = awaitItem() as UiState.Error
            assertThat(error.message).isEqualTo(VideoRepositoryImpl.ERROR_LOAD_FEED)
            awaitComplete()
        }
    }

    @Test
    fun `home feed shows cache without error when network fails`() = runTest {
        remote.feedError = IOException("offline")
        videoDao.replaceSource(VideoRepositoryImpl.SOURCE_HOME, listOf(videoEntity("cached1")))

        repository.getHomeFeed().test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val cached = awaitItem() as UiState.Success
            assertThat(cached.data.map { it.id }).containsExactly("cached1")
            assertThat(cached.fromCache).isTrue()
            awaitComplete()
        }
    }

    @Test
    fun `video details caches fresh details`() = runTest {
        remote.details = rawDetails("vid1")

        repository.getVideoDetails("vid1").test {
            awaitItem()
            awaitItem()
            awaitComplete()
        }

        val cached = detailsDao.getById("vid1")
        assertThat(cached).isNotNull()
        assertThat(cached!!.title).isEqualTo("Video vid1")
    }

    @Test
    fun `video details emits cached details when network fails`() = runTest {
        remote.detailsError = IOException("offline")
        detailsDao.upsert(TestData.details("vid1").toDetailsEntity(cachedAt = 10L))

        repository.getVideoDetails("vid1").test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val cached = awaitItem() as UiState.Success
            assertThat(cached.fromCache).isTrue()
            assertThat(cached.data.id).isEqualTo("vid1")
            awaitComplete()
        }
    }

    @Test
    fun `video stream emits success without cache`() = runTest {
        remote.stream = RawVideoStream("https://v/stream", "720p", "MPEG-4")

        repository.getVideoStream("vid1").test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val success = awaitItem() as UiState.Success
            assertThat(success.data.url).isEqualTo("https://v/stream")
            awaitComplete()
        }
    }

    @Test
    fun `video stream emits error when unavailable`() = runTest {
        remote.streamError = NoSuchElementException("no progressive stream")

        repository.getVideoStream("vid1").test {
            assertThat(awaitItem()).isEqualTo(UiState.Loading)
            val error = awaitItem() as UiState.Error
            assertThat(error.message).isEqualTo(VideoRepositoryImpl.ERROR_LOAD_STREAM)
            awaitComplete()
        }
    }

    private fun rawVideo(id: String) = RawVideoSummary(
        id = id,
        title = "Video $id",
        uploaderName = "Channel",
        thumbnailUrl = "https://example.com/$id.jpg",
        durationSeconds = 60,
        viewCount = 100,
        uploadDate = null,
    )

    private fun videoEntity(id: String) = CachedVideoEntity(
        id = id,
        sourceKey = VideoRepositoryImpl.SOURCE_HOME,
        title = "Cached $id",
        uploaderName = "Channel",
        thumbnailUrl = "https://example.com/$id.jpg",
        durationSeconds = 60,
        viewCount = 100,
        uploadDate = null,
        cachedAt = 10L,
    )

    private fun rawDetails(id: String) = RawVideoDetails(
        id = id,
        title = "Video $id",
        uploaderName = "Channel",
        uploaderAvatarUrl = null,
        description = "Desc",
        viewCount = 100,
        likeCount = 5,
        uploadDate = null,
        thumbnailUrl = "https://example.com/$id.jpg",
        durationSeconds = 60,
        recommended = listOf(rawVideo("rec1")),
    )
}
