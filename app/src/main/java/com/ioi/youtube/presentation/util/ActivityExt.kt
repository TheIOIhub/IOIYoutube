package com.ioi.youtube.presentation.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Activity з Compose-контексту (орієнтація і системні бари). */
fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
