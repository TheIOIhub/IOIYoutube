package com.ioi.youtube.data.remote

import com.ioi.youtube.data.extractor.ExtractorInitializer
import com.ioi.youtube.data.extractor.OkHttpDownloader
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Живий димний тест проти реального YouTube (мережа + робочий NewPipe Extractor).
 * За замовчуванням ПРОПУСКАЄТЬСЯ, щоб звичайний прогiн був детермiнованим.
 * Увiмкнути:
 *
 *   IOITUBE_LIVE_TESTS=1 ./gradlew :app:testDebugUnitTest --tests "*SmokeTest*"
 */
class NewPipeRemoteDataSourceSmokeTest {

    private val live = System.getenv("IOITUBE_LIVE_TESTS") == "1"

    private fun client() = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Test
    fun `feed search stream details and comments return data`() = runBlocking {
        assumeTrue("IOITUBE_LIVE_TESTS=1 не встановлено — живий тест пропущено", live)

        ExtractorInitializer(OkHttpDownloader(client())).init()
        val dataSource = NewPipeRemoteDataSource()

        val feed = dataSource.fetchHomeFeed()
        val search = dataSource.search("kotlin")
        val stream = dataSource.fetchVideoStream("dQw4w9WgXcQ")
        val details = dataSource.fetchVideoDetails("dQw4w9WgXcQ")
        val comments = dataSource.fetchComments("dQw4w9WgXcQ")

        check(feed.isNotEmpty() || search.isNotEmpty()) { "feed=${feed.size}, search=${search.size}" }
        check(stream.url.isNotBlank()) { "stream url порожнiй" }
        check(details.title.isNotBlank()) { "details title порожнiй" }
        check(comments.isNotEmpty()) { "comments порожнi" }

        println(
            "live ok: feed=${feed.size}, search=${search.size}, " +
                "stream=${stream.resolution}, comments=${comments.size}",
        )
    }
}
