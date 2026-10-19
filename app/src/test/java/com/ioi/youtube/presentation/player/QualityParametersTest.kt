package com.ioi.youtube.presentation.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
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

    private fun playerWith(tracks: Tracks): Player {
        val player = mockk<Player>(relaxed = true)
        every { player.currentTracks } returns tracks
        every { player.trackSelectionParameters } returns TrackSelectionParameters.Builder().build()
        return player
    }

    private fun videoFormat(height: Int, bitrate: Int = Format.NO_VALUE): Format =
        Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_H264)
            .setHeight(height)
            .setAverageBitrate(bitrate)
            .build()

    @Test
    fun `mime-less group counts as video only when it carries a height`() {
        val tracks = tracks(
            Format.Builder().setHeight(480).setAverageBitrate(500_000).build(),
            Format.Builder().build(),
        )

        val infos = tracks.videoTrackInfos()

        assertThat(infos.map { it.height }).containsExactly(480)
        assertThat(infos.map { it.groupIndex }).containsExactly(0)
    }

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
    fun `auto clears the video override`() {
        val player = playerWith(tracks(videoFormat(1080)))

        val expected = player.trackSelectionParameters.buildUpon()
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
}
