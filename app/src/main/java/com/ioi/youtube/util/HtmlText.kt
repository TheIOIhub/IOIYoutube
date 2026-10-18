package com.ioi.youtube.util

import org.jsoup.Jsoup

/**
 * YouTube вiддає описи вiдео та коментарi як HTML (NewPipe: `Description.type == HTML`):
 * у текстi трапляються теги (`<a href="...">`, `<br>`) i сутностi (`&apos;`, `&amp;`).
 * Для UI це смiття, тому перетворюємо HTML у звичайнiй текст, зберiгаючи переноси рядкiв.
 */
object HtmlText {

    private val LINE_BREAK = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val PARAGRAPH_END = Regex("</p\\s*>", RegexOption.IGNORE_CASE)

    fun toPlainText(html: String): String {
        if (html.isEmpty()) return ""
        val withBreaks = html
            .replace(LINE_BREAK, "\n")
            .replace(PARAGRAPH_END, "\n\n")
        return Jsoup.parse(withBreaks)
            .body()
            .wholeText()
            .replace('\u00A0', ' ')
            .trim()
    }
}
