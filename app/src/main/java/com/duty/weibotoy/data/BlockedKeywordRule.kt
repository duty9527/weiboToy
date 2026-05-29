package com.duty.weibotoy.data

enum class MatchMode(val label: String) {
    CONTAINS("包含"),
    EXACT("全匹配"),
    REGEX("正则")
}

data class BlockedKeywordRule(
    val text: String,
    val mode: MatchMode = MatchMode.CONTAINS
)
