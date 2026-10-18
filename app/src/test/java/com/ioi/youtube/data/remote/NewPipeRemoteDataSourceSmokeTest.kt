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

        val feedStart = System.currentTimeMillis()
        val feed = dataSource.fetchHomeFeed()
        val feedMillis = System.currentTimeMillis() - feedStart

        val search = dataSource.search("kotlin")
        val stream = dataSource.fetchVideoStream("dQw4w9WgXcQ")
        val details = dataSource.fetchVideoDetails("dQw4w9WgXcQ")
        val comments = dataSource.fetchComments("dQw4w9WgXcQ")

        check(feed.size >= 20) { "стрiчка замала: ${feed.size}" }
        check(search.isNotEmpty()) { "пошук порожнiй" }
        check(stream.url.isNotBlank()) { "stream url порожнiй" }
        check(details.title.isNotBlank()) { "details title порожнiй" }
        check(comments.isNotEmpty()) { "comments порожнi" }

        // YouTube вiддає опис/коментарi як HTML — у UI не має лишитись тегiв i сутностей.
        check(!details.description.contains("<a href") && !details.description.contains("&amp;")) {
            "опис не очищено вiд HTML: ${details.description.take(120)}"
        }
        val dirtyComment = comments.firstOrNull { it.text.contains("<a href") || it.text.contains("&apos;") }
        check(dirtyComment == null) { "коментар не очищено вiд HTML: ${dirtyComment?.text?.take(120)}" }

        println(
            "live ok: feed=${feed.size} (${feedMillis} ms), search=${search.size}, " +
                "stream=${stream.resolution}, comments=${comments.size}",
        )
        println("live feed sample: " + feed.take(6).joinToString(" | ") { it.title.take(40) })
    }
}
