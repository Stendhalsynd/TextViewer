package com.jihun.textviewer.domain.util

object PaginationUtil {
    fun paginate(content: String, pageCharLimit: Int): List<String> {
        require(pageCharLimit > 0) { "pageCharLimit must be greater than 0" }

        if (content.isEmpty()) return listOf("")

        val pages = mutableListOf<String>()
        var index = 0

        while (index < content.length) {
            val rawEnd = (index + pageCharLimit).coerceAtMost(content.length)
            var splitPoint = rawEnd

            if (rawEnd < content.length) {
                val lineBreakPoint = content.lastIndexOf('\n', startIndex = rawEnd)
                if (lineBreakPoint in (index + 1) until rawEnd) {
                    splitPoint = lineBreakPoint + 1
                } else {
                    val whitespacePoint = content.lastIndexOf(' ', startIndex = rawEnd)
                    if (whitespacePoint in (index + 1) until rawEnd) {
                        splitPoint = whitespacePoint + 1
                    }
                }
            }

            pages += content.substring(index, splitPoint)
            index = splitPoint
        }

        return pages
    }

    fun clampPage(requestedPage: Int, totalPages: Int): Int {
        if (totalPages <= 0) return 0
        return requestedPage.coerceIn(0, totalPages - 1)
    }
}
