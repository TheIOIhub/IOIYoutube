package com.ioi.youtube.presentation.player

import kotlin.math.roundToInt

/** Ширина лівої та правої зон тапу; решта — центральна зона контролера. */
internal const val TAP_EDGE_FRACTION = 0.35f

/** Крок перемотки тапом. */
internal const val SEEK_STEP_MS = 5_000L

/** Частка висоти відео, після якої відпускання згортає плеєр. */
internal const val MINIMIZE_DRAG_FRACTION = 0.30f

internal fun shouldMinimize(dragPx: Float, heightPx: Float): Boolean =
    heightPx > 0f && dragPx >= heightPx * MINIMIZE_DRAG_FRACTION

/** Що робити після відпускання протягування: згорнути плеєр чи повернути на місце. */
internal enum class MinimizeRelease { MINIMIZE, SETTLE }

/**
 * Багатопальцевий жест (наприклад, початок пінчу) або системний
 * `ACTION_CANCEL` завжди повертає відео на місце, навіть якщо протягування
 * вже перевищило поріг.
 */
internal fun minimizeRelease(
    dragPx: Float,
    heightPx: Float,
    canceled: Boolean,
): MinimizeRelease =
    if (!canceled && shouldMinimize(dragPx, heightPx)) {
        MinimizeRelease.MINIMIZE
    } else {
        MinimizeRelease.SETTLE
    }

/** Мінімальна яскравість вікна, щоб екран не ставав повністю чорним. */
internal const val MIN_BRIGHTNESS = 0.05f

internal enum class TapZone { LEFT, CENTER, RIGHT }

internal fun tapZone(x: Float, width: Float): TapZone {
    if (width <= 0f) return TapZone.CENTER
    val fraction = (x / width).coerceIn(0f, 1f)
    return when {
        fraction < TAP_EDGE_FRACTION -> TapZone.LEFT
        fraction > 1f - TAP_EDGE_FRACTION -> TapZone.RIGHT
        else -> TapZone.CENTER
    }
}

/**
 * Плавна зміна рівня 0..1 за вертикальним зсувом пальця.
 * Свайп угору (від'ємний dy) збільшує рівень, вниз — зменшує.
 */
internal fun adjustedLevel(startLevel: Float, verticalDeltaPx: Float, heightPx: Float): Float {
    if (heightPx <= 0f) return startLevel.coerceIn(0f, 1f)
    return (startLevel - verticalDeltaPx / heightPx).coerceIn(0f, 1f)
}

/** Перемотка з обмеженням початком і, якщо тривалість відома, кінцем відео. */
internal fun seekTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
    val raw = (positionMs + deltaMs).coerceAtLeast(0L)
    return if (durationMs > 0L) raw.coerceAtMost(durationMs) else raw
}

internal fun levelPercent(level: Float): Int = (level.coerceIn(0f, 1f) * 100f).roundToInt()
