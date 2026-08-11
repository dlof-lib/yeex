package com.yeex.dlof.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.yeex.dlof.ui.theme.YeexAccent

private val TOKEN_REGEX = Regex("(#[\\p{L}0-9_]+)|(@[a-zA-Z0-9_.]+)|(https?://\\S+)")

/**
 * Renders a Topic body's lightweight markdown subset — the spec's "نص منسق،
 * عناوين فرعية، اقتباسات، قوائم، أكواد برمجية ... روابط، هاشتاغات، ذكر
 * المستخدمين" — without pulling in a full markdown/HTML dependency.
 * Recognized per-line prefixes: `# `/`## ` headings, `> ` quotes, `- `/`* `
 * bullets, and ``` ``` ``` fenced code blocks; inline `#hashtag`, `@mention`,
 * and bare `https://` links are auto-highlighted and made tappable anywhere
 * else in the text.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    onHashtagClick: (String) -> Unit = {},
    onMentionClick: (String) -> Unit = {},
    onLinkClick: (String) -> Unit = {}
) {
    val lines = remember(text) { text.lines() }
    var inCodeBlock = false
    val codeBuffer = StringBuilder()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (line in lines) {
            if (line.trim().startsWith("```")) {
                if (inCodeBlock) {
                    CodeBlock(codeBuffer.toString())
                    codeBuffer.clear()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                }
                continue
            }
            if (inCodeBlock) {
                codeBuffer.append(line).append('\n')
                continue
            }
            when {
                line.isBlank() -> Text(" ", style = MaterialTheme.typography.bodySmall)
                line.startsWith("## ") -> Text(
                    line.removePrefix("## "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                line.startsWith("# ") -> Text(
                    line.removePrefix("# "),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                line.startsWith("> ") -> Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
                    Box(
                        Modifier
                            .padding(end = 8.dp, top = 2.dp, bottom = 2.dp)
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(YeexAccent, RoundedCornerShape(2.dp))
                    )
                    RichLine(
                        line.removePrefix("> "),
                        italic = true,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        onHashtagClick = onHashtagClick,
                        onMentionClick = onMentionClick,
                        onLinkClick = onLinkClick
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") -> Row {
                    Text("•  ", color = YeexAccent, fontWeight = FontWeight.Bold)
                    RichLine(
                        line.removePrefix(if (line.startsWith("- ")) "- " else "* "),
                        onHashtagClick = onHashtagClick,
                        onMentionClick = onMentionClick,
                        onLinkClick = onLinkClick
                    )
                }
                else -> RichLine(line, onHashtagClick = onHashtagClick, onMentionClick = onMentionClick, onLinkClick = onLinkClick)
            }
        }
        if (inCodeBlock && codeBuffer.isNotEmpty()) CodeBlock(codeBuffer.toString())
    }
}

@Composable
private fun CodeBlock(code: String) {
    Text(
        text = code.trimEnd('\n'),
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .padding(10.dp)
    )
}

@Composable
private fun RichLine(
    line: String,
    italic: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onHashtagClick: (String) -> Unit,
    onMentionClick: (String) -> Unit,
    onLinkClick: (String) -> Unit
) {
    val annotated = remember(line) {
        buildAnnotatedString {
            var last = 0
            for (match in TOKEN_REGEX.findAll(line)) {
                append(line.substring(last, match.range.first))
                val token = match.value
                val tag = when {
                    token.startsWith("#") -> "hashtag"
                    token.startsWith("@") -> "mention"
                    else -> "link"
                }
                pushStringAnnotation(tag, token)
                withStyle(
                    SpanStyle(
                        color = YeexAccent,
                        textDecoration = if (tag == "link") TextDecoration.Underline else TextDecoration.None
                    )
                ) { append(token) }
                pop()
                last = match.range.last + 1
            }
            if (last < line.length) append(line.substring(last))
        }
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = color,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal
        ),
        onClick = { offset ->
            annotated.getStringAnnotations("hashtag", offset, offset).firstOrNull()?.let {
                onHashtagClick(it.item.removePrefix("#")); return@ClickableText
            }
            annotated.getStringAnnotations("mention", offset, offset).firstOrNull()?.let {
                onMentionClick(it.item.removePrefix("@")); return@ClickableText
            }
            annotated.getStringAnnotations("link", offset, offset).firstOrNull()?.let { onLinkClick(it.item) }
        }
    )
}

/** Pulls `#hashtag` / `@mention` tokens out of free text — used when publishing a topic to populate its hashtags/mentions lists. */
fun extractTopicTags(text: String): Pair<List<String>, List<String>> {
    val hashtags = Regex("#([\\p{L}0-9_]+)").findAll(text).map { it.groupValues[1] }.distinct().toList()
    val mentions = Regex("@([a-zA-Z0-9_.]+)").findAll(text).map { it.groupValues[1] }.distinct().toList()
    return hashtags to mentions
}
