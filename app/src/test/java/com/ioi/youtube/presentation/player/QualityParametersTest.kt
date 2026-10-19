package com.ioi.youtube.presentation.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QualityParametersTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun tracks(vararg formats: Format): Tracks {
        val groups = formats.map { format ->
            Tracks.Group(
                TrackGroup(format),
                /* adaptiveSupported= */ true,
                /* trackSupport= */ intArrayOf(C.FORMAT_HANDLED),
                /* trackSelected= */ booleanArrayOf(true),
            )
        }
        return Tracks(groups)
    }

    private fun playerWith(
        tracks: Tracks,
        baseline: TrackSelectionParameters = TrackSelectionParameters.Builder(context).build(),
    ): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.currentTracks } returns tracks
        every { player.trackSelectionParameters } returns baseline
        return player
    }

    private fun videoFormat(height: Int, bitrate: Int = Format.NO_VALUE): Format =
        Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setHeight(height)
            .setAverageBitrate(bitrate)
            .build()

    private fun audioFormat(): Format =
        Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build()

    /** Одна група з кiлькома дорiжками — щоб перевiрити, що `trackIndex` справдi вживається. */
    private fun trackGroup(vararg formats: Format): Tracks =
        Tracks(
            listOf(
                Tracks.Group(
                    TrackGroup(*formats),
                    /* adaptiveSupported= */ true,
                    /* trackSupport= */ IntArray(formats.size) { C.FORMAT_HANDLED },
                    /* trackSelected= */ BooleanArray(formats.size) { true },
                ),
            ),
        )

    @Test
    fun `video track infos expose group and track indexes with height and bitrate`() {
        val tracks = tracks(
            videoFormat(1080, 4_300_000),
            videoFormat(720, 1_058_000),
        )

        val infos = tracks.videoTrackInfos()

        assertThat(infos.map { it.height }).containsExactly(1080, 720).inOrder()
        assertThat(infos.map { it.groupIndex }).containsExactly(0, 1).inOrder()
        assertThat(infos.map { it.trackIndex }).containsExactly(0, 0).inOrder()
        assertThat(infos.first().bitrate).isEqualTo(4_300_000)
    }

    @Test
    fun `auto clears the video override and keeps the audio one`() {
        val tracks = tracks(videoFormat(1080), audioFormat())
        val videoGroup = tracks.groups[0].mediaTrackGroup
        val audioGroup = tracks.groups[1].mediaTrackGroup
        val baseline = TrackSelectionParameters.Builder(context).build().buildUpon()
            .setOverrideForType(TrackSelectionOverride(videoGroup, 0))
            .setOverrideForType(TrackSelectionOverride(audioGroup, 0))
            .build()
        val player = playerWith(tracks, baseline)

        val expected = baseline.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .build()

        assertThat(qualityParameters(player, option = null)).isEqualTo(expected)
    }

    @Test
    fun `explicit option overrides exactly that track group`() {
        val player = playerWith(
            tracks(
                videoFormat(1080, 4_300_000),
                videoFormat(720, 1_058_000),
            ),
        )
        val group = player.currentTracks.groups[1].mediaTrackGroup
        val expected = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group, 0))
            .build()

        val actual = qualityParameters(player, QualityOption(groupIndex = 1, trackIndex = 0, height = 720, bitrate = 1_058_000))

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `option pointing outside the current tracks keeps parameters untouched`() {
        val player = playerWith(tracks(videoFormat(1080)))

        val actual = qualityParameters(
            player,
            QualityOption(groupIndex = 7, trackIndex = 0, height = 480, bitrate = 500_000),
        )

        assertThat(actual).isEqualTo(player.trackSelectionParameters)
    }

    @Test
    fun `stale option with an out-of-range track index keeps parameters untouched`() {
        val player = playerWith(tracks(videoFormat(1080)))

        val actual = qualityParameters(
            player,
            QualityOption(groupIndex = 0, trackIndex = 5, height = 1080, bitrate = 4_300_000),
        )

        assertThat(actual).isEqualTo(player.trackSelectionParameters)
    }

    @Test
    fun `video track infos ignore audio groups`() {
        val tracks = tracks(videoFormat(1080), audioFormat())

        val infos = tracks.videoTrackInfos()

        assertThat(infos.map { it.groupIndex }).containsExactly(0)
        assertThat(infos.map { it.height }).containsExactly(1080)
    }

    @Test
    fun `video track infos enumerate every track in a group`() {
        val tracks = trackGroup(videoFormat(1080, 1_500_000), videoFormat(1080, 4_300_000))

        val infos = tracks.videoTrackInfos()

        assertThat(infos.map { it.trackIndex }).containsExactly(0, 1)
        assertThat(infos.map { it.bitrate }).containsExactly(1_500_000, 4_300_000)
    }

    @Test
    fun `option pointing at a non-video group keeps parameters untouched`() {
        val player = playerWith(tracks(videoFormat(1080), audioFormat()))

        val actual = qualityParameters(
            player,
            QualityOption(groupIndex = 1, trackIndex = 0, height = 720, bitrate = 1_058_000),
        )

        assertThat(actual).isEqualTo(player.trackSelectionParameters)
    }

    @Test
    fun `explicit option uses the given track index`() {
        val tracks = trackGroup(videoFormat(1080, 1_500_000), videoFormat(1080, 4_300_000))
        val player = playerWith(tracks)
        val group = tracks.groups[0].mediaTrackGroup
        val expected = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group, 1))
            .build()

        val actual = qualityParameters(
            player,
            QualityOption(groupIndex = 0, trackIndex = 1, height = 1080, bitrate = 4_300_000),
        )

        assertThat(actual).isEqualTo(expected)
    }
}
