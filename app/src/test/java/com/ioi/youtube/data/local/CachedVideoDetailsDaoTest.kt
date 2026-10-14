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
class CachedVideoDetailsDaoTest {

    private lateinit var database: IOIDatabase
    private lateinit var dao: CachedVideoDetailsDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, IOIDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.cachedVideoDetailsDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `upsert then getById returns details`() = runTest {
        dao.upsert(details("vid1", 10L))

        val result = dao.getById("vid1")

        assertThat(result).isNotNull()
        assertThat(result!!.title).isEqualTo("Video vid1")
        assertThat(result.viewCount).isEqualTo(999L)
    }

    @Test
    fun `upsert overwrites existing row`() = runTest {
        dao.upsert(details("vid1", 10L))
        val updated = details("vid1", 20L).copy(title = "Updated title")
        dao.upsert(updated)

        assertThat(dao.getById("vid1")!!.title).isEqualTo("Updated title")
    }

    @Test
    fun `getById returns null for unknown id`() = runTest {
        assertThat(dao.getById("missing")).isNull()
    }

    private fun details(id: String, cachedAt: Long) = CachedVideoDetailsEntity(
        id = id,
        title = "Video $id",
        uploaderName = "Channel",
        description = "Desc",
        viewCount = 999,
        likeCount = 42,
        uploadDate = null,
        thumbnailUrl = "https://example.com/$id.jpg",
        durationSeconds = 212,
        cachedAt = cachedAt,
    )
}
