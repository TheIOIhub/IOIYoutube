package com.ioi.youtube.presentation.player

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** Діагностичний файл: записує порядок запуску класів у /tmp/opencode/order.txt. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OrderProbeTest {

    private var className = ""

    @Test
    fun probe() {
        className = "com.ioi.youtube." + java.lang.Class.forName("com.ioi.youtube.presentation.player.OrderProbeTest").simpleName
        val f = java.io.File("/tmp/opencode/order.txt")
        f.appendText("\nPROBE $className ${java.util.UUID.randomUUID()}\n")
    }
}
