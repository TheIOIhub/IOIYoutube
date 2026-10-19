package com.ioi.youtube.presentation.navigation

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SHORTS = "shorts"
    const val PLAYER = "player/{videoId}"
    const val ARG_VIDEO_ID = "videoId"

    fun player(videoId: String) = "player/$videoId"
}
