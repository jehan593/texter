package com.texter.app.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.texter.app.ui.theme.nord0
import com.texter.app.ui.theme.nord2
import com.texter.app.ui.theme.nord13

data class SearchMatch(val start: Int, val end: Int)

/** Always case-insensitive, always plain substring — matches a whole word just by virtue of the
 *  word containing the query, no separate "whole word" mode needed. */
fun findMatches(text: String, query: String): List<SearchMatch> {
    if (query.isEmpty()) return emptyList()
    val matches = mutableListOf<SearchMatch>()
    var index = text.indexOf(query, 0, ignoreCase = true)
    while (index >= 0) {
        matches.add(SearchMatch(index, index + query.length))
        index = text.indexOf(query, index + 1, ignoreCase = true)
    }
    return matches
}

/** Layers match highlighting on top of [base] (typically already syntax-highlighted) without
 *  disturbing its existing spans — the current match gets a distinct color from the rest so
 *  up/down navigation is visible at a glance. */
fun highlightSearchMatches(
    base: AnnotatedString,
    matches: List<SearchMatch>,
    currentMatchIndex: Int
): AnnotatedString {
    if (matches.isEmpty()) return base
    return buildAnnotatedString {
        append(base)
        matches.forEachIndexed { index, match ->
            val style = if (index == currentMatchIndex) {
                SpanStyle(background = nord13, color = nord0)
            } else {
                SpanStyle(background = nord2)
            }
            addStyle(style, match.start, match.end)
        }
    }
}
