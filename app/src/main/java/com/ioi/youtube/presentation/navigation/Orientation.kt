package com.ioi.youtube.presentation.navigation

import android.content.pm.ActivityInfo

/** Вільний поворот лише на екрані вiдео (як у YouTube); решта застосунку — портрет. */
fun orientationForRoute(route: String?): Int =
    if (route == Routes.PLAYER) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
