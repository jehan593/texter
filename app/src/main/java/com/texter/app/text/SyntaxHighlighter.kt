package com.texter.app.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.texter.app.ui.theme.nord14
import com.texter.app.ui.theme.nord15
import com.texter.app.ui.theme.nord3
import com.texter.app.ui.theme.nord9

private data class LanguageProfile(
    val lineComment: List<String>,
    val blockComment: Pair<String, String>?,
    val stringDelimiters: List<Char>
)

private val C_LIKE = LanguageProfile(
    lineComment = listOf("//"),
    blockComment = "/*" to "*/",
    stringDelimiters = listOf('"', '\'')
)

private val HASH_COMMENTED = LanguageProfile(
    lineComment = listOf("#"),
    blockComment = null,
    stringDelimiters = listOf('"', '\'')
)

private val MARKUP = LanguageProfile(
    lineComment = emptyList(),
    blockComment = "<!--" to "-->",
    stringDelimiters = listOf('"', '\'')
)

private val PROFILES: Map<String, LanguageProfile> = mapOf(
    "kt" to C_LIKE, "kts" to C_LIKE, "java" to C_LIKE, "js" to C_LIKE, "ts" to C_LIKE,
    "jsx" to C_LIKE, "tsx" to C_LIKE, "c" to C_LIKE, "cpp" to C_LIKE, "h" to C_LIKE,
    "hpp" to C_LIKE, "cs" to C_LIKE, "go" to C_LIKE, "rs" to C_LIKE, "swift" to C_LIKE,
    "dart" to C_LIKE, "gradle" to C_LIKE, "css" to C_LIKE.copy(lineComment = emptyList()),
    "json" to LanguageProfile(emptyList(), null, listOf('"')),
    "py" to HASH_COMMENTED, "sh" to HASH_COMMENTED, "bash" to HASH_COMMENTED,
    "yml" to HASH_COMMENTED, "yaml" to HASH_COMMENTED, "rb" to HASH_COMMENTED,
    "toml" to HASH_COMMENTED, "properties" to HASH_COMMENTED, "gitignore" to HASH_COMMENTED,
    "conf" to HASH_COMMENTED, "ini" to HASH_COMMENTED,
    "sql" to LanguageProfile(listOf("--"), "/*" to "*/", listOf('\'')),
    "html" to MARKUP, "htm" to MARKUP, "xml" to MARKUP
)

private val DEFAULT_PROFILE = C_LIKE

// A generic set spanning keywords common to most C-like/Python/Kotlin/JS-family languages —
// not exhaustive for any single one, but enough to make control flow and declarations visually
// distinct without maintaining a full grammar per language.
private val KEYWORDS = setOf(
    "if", "else", "for", "while", "do", "return", "function", "def", "class", "import", "from",
    "const", "let", "var", "val", "fun", "true", "false", "null", "none", "nil", "void",
    "public", "private", "protected", "static", "new", "try", "catch", "finally", "throw",
    "throws", "switch", "case", "break", "continue", "this", "self", "super", "extends",
    "implements", "interface", "enum", "package", "async", "await", "yield", "in", "is", "as",
    "when", "object", "override", "data", "sealed", "companion", "struct", "impl", "use", "mod",
    "pub", "fn", "match", "type", "namespace", "using"
)

private val NUMBER_REGEX = Regex("""\d+(\.\d+)?""")
private val WORD_REGEX = Regex("""[A-Za-z_][A-Za-z0-9_]*""")

fun fileExtensionOf(displayName: String): String = displayName.substringAfterLast('.', "")

/** Lightweight regex/scan-based highlighter — comments, strings, numbers, and a generic keyword
 *  set — not a real parser, but enough to make code and config files easier to read without
 *  pulling in a full language-grammar dependency for a plain text editor. */
fun highlightSyntax(text: String, fileExtension: String): AnnotatedString {
    val profile = PROFILES[fileExtension.lowercase()] ?: DEFAULT_PROFILE
    return buildAnnotatedString {
        append(text)
        var i = 0
        while (i < text.length) {
            val blockComment = profile.blockComment
            if (blockComment != null && text.startsWith(blockComment.first, i)) {
                val closeAt = text.indexOf(blockComment.second, i + blockComment.first.length)
                val end = if (closeAt == -1) text.length else closeAt + blockComment.second.length
                addStyle(SpanStyle(color = nord3), i, end)
                i = end
                continue
            }

            val lineCommentPrefix = profile.lineComment.firstOrNull { text.startsWith(it, i) }
            if (lineCommentPrefix != null) {
                val end = text.indexOf('\n', i).let { if (it == -1) text.length else it }
                addStyle(SpanStyle(color = nord3), i, end)
                i = end
                continue
            }

            if (text[i] in profile.stringDelimiters) {
                val quote = text[i]
                var end = i + 1
                while (end < text.length && text[end] != quote) {
                    if (text[end] == '\\' && end + 1 < text.length) end++
                    end++
                }
                end = (end + 1).coerceAtMost(text.length)
                addStyle(SpanStyle(color = nord14), i, end)
                i = end
                continue
            }

            val numberMatch = NUMBER_REGEX.matchAt(text, i)
            if (numberMatch != null) {
                addStyle(SpanStyle(color = nord15), i, numberMatch.range.last + 1)
                i = numberMatch.range.last + 1
                continue
            }

            val wordMatch = WORD_REGEX.matchAt(text, i)
            if (wordMatch != null) {
                if (wordMatch.value.lowercase() in KEYWORDS) {
                    addStyle(SpanStyle(color = nord9), i, wordMatch.range.last + 1)
                }
                i = wordMatch.range.last + 1
                continue
            }

            i++
        }
    }
}
