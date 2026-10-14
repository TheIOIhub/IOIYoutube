package com.ioi.youtube.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CachedVideoDaoTest {

    private lateinit var database: IOIDatabase
    private lateinit var dao: CachedVideoDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, IOIDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.cachedVideoDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `inserts and reads by source key`() = runTest {
        val now = 1_000L
        dao.insertAll(
            listOf(
                videoEntity("bbb", "home", now),
                videoEntity("aaa", "home", now),
            ),
        )

        val result = dao.getBySource("home")

        assertThat(result.map { it.id }).containsExactly("bbb", "aaa").inOrder()
    }

    @Test
    fun `replaceSource replaces list atomically`() = runTest {
        dao.insertAll(listOf(videoEntity("old", "home", 1L)))

        dao.replaceSource("home", listOf(videoEntity("new1", "home", 2L), videoEntity("new2", "home", 2L)))

        val result = dao.getBySource("home")
        assertThat(result.map { it.id }).containsExactly("new1", "new2").inOrder()
    }

    @Test
    fun `deleteOlderThan removes stale rows only`() = runTest {
        dao.insertAll(
            listOf(
                videoEntity("stale", "home", 100L),
                videoEntity("fresh", "home", 500L),
            ),
        )

        dao.deleteOlderThan(300L)

        assertThat(dao.getBySource("home").map { it.id }).containsExactly("fresh")
    }

    @Test
    fun `getBySource returns empty list for unknown key`() = runTest {
        assertThat(dao.getBySource("search:nothing")).isEmpty()
    }

    private fun videoEntity(id: String, sourceKey: String, cachedAt: Long) = CachedVideoEntity(
        id = id,
        sourceKey = sourceKey,
        title = "Title $id",
        uploaderName = "Channel",
        thumbnailUrl = "https://example.com/$id.jpg",
        durationSeconds = 61,
        viewCount = 100,
        uploadDate = null,
        cachedAt = cachedAt,
    )
}
