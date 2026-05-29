package com.duty.weibotoy.data

fun cleanWeiboHtmlText(content: String): String {
    var result = content
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<img\\b[^>]*\\balt\\s*=\\s*([\"'])(.*?)\\1[^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
            decodeHtmlEntities(it.groupValues[2])
        }
        .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")

    repeat(2) {
        result = decodeHtmlEntities(result)
    }

    return result
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(Regex("[ \\t]+\\n"), "\n")
        .replace(Regex("\\n[ \\t]+"), "\n")
        .replace(Regex("\\n{2,}"), "\n")
        .trim()
}

private fun decodeHtmlEntities(content: String): String {
    return content
        .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
            match.groupValues[1].toIntOrNull(16)?.let { code ->
                codePointToString(code)
            } ?: match.value
        }
        .replace(Regex("&#(\\d+);")) { match ->
            match.groupValues[1].toIntOrNull()?.let { code ->
                codePointToString(code)
            } ?: match.value
        }
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
}

private fun codePointToString(code: Int): String {
    return try {
        String(Character.toChars(code))
    } catch (e: IllegalArgumentException) {
        ""
    }
}
