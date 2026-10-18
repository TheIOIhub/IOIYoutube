package com.ioi.youtube.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HtmlTextTest {

    @Test
    fun `decodes entities and strips tags`() {
        val html = "If you&apos;re enjoying <b>this</b> &amp; want more, join <a href=\"https://x/y\">https://x/y</a>"

        assertThat(HtmlText.toPlainText(html))
            .isEqualTo("If you're enjoying this & want more, join https://x/y")
    }

    @Test
    fun `keeps line breaks from br tags`() {
        assertThat(HtmlText.toPlainText("Never<br><br>Gonna<br/>give")).isEqualTo("Never\n\nGonna\ngive")
    }

    @Test
    fun `decodes numeric entities and nbsp`() {
        assertThat(HtmlText.toPlainText("5 &gt; 4&#33;&#x20;ok&nbsp;done")).isEqualTo("5 > 4! ok done")
    }

    @Test
    fun `plain text without html stays as is`() {
        assertThat(HtmlText.toPlainText("Просто текст\nз переносом")).isEqualTo("Просто текст\nз переносом")
    }

    @Test
    fun `empty html gives empty text`() {
        assertThat(HtmlText.toPlainText("")).isEmpty()
    }
}
