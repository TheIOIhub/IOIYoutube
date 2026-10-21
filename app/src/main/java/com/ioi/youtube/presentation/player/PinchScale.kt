package com.ioi.youtube.presentation.player

/** Мінімальний масштаб pinch-жесту, після якого відео заповнює екран. */
internal const val PINCH_OUT_THRESHOLD = 1.25f

/** Максимальний масштаб pinch-жесту, після якого відео вписується у плеєр. */
internal const val PINCH_IN_THRESHOLD = 0.8f

/**
 * Перетворює накопичений масштаб pinch-жесту на новий режим.
 * `null` означає, що поріг ще не перейдено і режим змінювати не треба.
 */
internal fun scaleAfterPinch(totalScale: Float): VideoScale? = when {
    totalScale >= PINCH_OUT_THRESHOLD -> VideoScale.FILL
    totalScale <= PINCH_IN_THRESHOLD -> VideoScale.FIT
    else -> null
}
