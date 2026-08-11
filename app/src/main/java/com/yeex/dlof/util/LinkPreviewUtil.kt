package com.yeex.dlof.util

import com.yeex.dlof.data.model.LinkCardType
import com.yeex.dlof.data.model.LinkPreview
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * Turns a pasted URL into a [LinkPreview] "Link Card" for YEEX TOPICS instead
 * of showing the raw link — see the "🔗 نشر الروابط" spec: YouTube -> video
 * card, GitHub -> project card, a direct image/video URL -> media preview,
 * anything else -> a generic website/article card built from the page's
 * Open Graph tags when reachable.
 *
 * Runs entirely with the platform's built-in [HttpURLConnection]/regex — no
 * extra HTML-parsing dependency — since only a handful of `<meta ...>` tags
 * near the top of the document need to be read, not a full DOM.
 */
object LinkPreviewUtil {

    private val YOUTUBE_ID = Pattern.compile(
        "(?:youtube\\.com/watch\\?v=|youtube\\.com/shorts/|youtu\\.be/)([A-Za-z0-9_-]{6,})"
    )
    private val GITHUB_REPO = Pattern.compile("github\\.com/([\\w.-]+)/([\\w.-]+)")
    private val IMAGE_EXT = Regex("\\.(jpg|jpeg|png|gif|webp)(\\?.*)?$", RegexOption.IGNORE_CASE)
    private val VIDEO_EXT = Regex("\\.(mp4|webm|mov|m3u8)(\\?.*)?$", RegexOption.IGNORE_CASE)

    /** Very small check so the compose UI can decide whether to try a preview at all. */
    fun looksLikeUrl(text: String): Boolean =
        Regex("^https?://\\S+$", RegexOption.IGNORE_CASE).matches(text.trim())

    /**
     * Builds the [LinkPreview] on a background thread. Never throws: on any
     * network/parse failure it falls back to a bare-bones WEBSITE card built
     * only from the URL itself, so publishing a topic never hard-fails just
     * because a preview couldn't be fetched.
     */
    fun fetch(rawUrl: String): LinkPreview {
        val url = rawUrl.trim()
        val host = runCatching { URL(url).host.removePrefix("www.") }.getOrDefault("")

        youtubeId(url)?.let { videoId ->
            return LinkPreview(
                url = url,
                cardType = LinkCardType.YOUTUBE.name,
                title = "YouTube",
                siteName = "YouTube",
                imageUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
            )
        }

        githubRepo(url)?.let { (owner, repo) ->
            val info = runCatching { fetchGithubRepoInfo(owner, repo) }.getOrNull()
            return LinkPreview(
                url = url,
                cardType = LinkCardType.GITHUB.name,
                title = info?.first ?: "$owner/$repo",
                description = info?.second.orEmpty(),
                siteName = "GitHub"
            )
        }

        if (IMAGE_EXT.containsMatchIn(url)) {
            return LinkPreview(url = url, cardType = LinkCardType.IMAGE.name, imageUrl = url, siteName = host)
        }
        if (VIDEO_EXT.containsMatchIn(url)) {
            return LinkPreview(url = url, cardType = LinkCardType.VIDEO.name, siteName = host)
        }

        val og = runCatching { fetchOpenGraph(url) }.getOrNull()
        return LinkPreview(
            url = url,
            cardType = LinkCardType.WEBSITE.name,
            title = og?.title.orEmpty().ifBlank { host },
            description = og?.description.orEmpty(),
            imageUrl = og?.image.orEmpty(),
            siteName = host
        )
    }

    private fun youtubeId(url: String): String? {
        val m = YOUTUBE_ID.matcher(url)
        return if (m.find()) m.group(1) else null
    }

    private fun githubRepo(url: String): Pair<String, String>? {
        val m = GITHUB_REPO.matcher(url)
        if (!m.find()) return null
        val owner = m.group(1) ?: return null
        val repo = m.group(2)?.removeSuffix(".git") ?: return null
        // Avoid mistaking github.com/{owner} (no repo) or well-known non-repo
        // paths for a repo card.
        if (repo.isBlank() || repo in setOf("issues", "pulls", "settings", "notifications")) return null
        return owner to repo
    }

    /** owner/repo -> (display name, short description), via GitHub's public REST API (no auth needed for public repos, rate-limited). */
    private fun fetchGithubRepoInfo(owner: String, repo: String): Pair<String, String>? {
        val json = httpGet("https://api.github.com/repos/$owner/$repo") ?: return null
        val obj = org.json.JSONObject(json)
        val fullName = obj.optString("full_name", "$owner/$repo")
        val description = obj.optString("description", "")
        return fullName to description
    }

    private data class OpenGraph(val title: String?, val description: String?, val image: String?)

    private fun fetchOpenGraph(url: String): OpenGraph? {
        val html = httpGet(url, maxBytes = 200_000) ?: return null
        fun meta(prop: String): String? {
            val pattern = Pattern.compile(
                "<meta[^>]+(?:property|name)=[\"']$prop[\"'][^>]+content=[\"']([^\"']*)[\"']",
                Pattern.CASE_INSENSITIVE
            )
            var m = pattern.matcher(html)
            if (m.find()) return m.group(1)
            // Some pages put content before property/name — try the reverse order.
            val reversed = Pattern.compile(
                "<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+(?:property|name)=[\"']$prop[\"']",
                Pattern.CASE_INSENSITIVE
            )
            m = reversed.matcher(html)
            return if (m.find()) m.group(1) else null
        }
        val title = meta("og:title") ?: Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)
        val description = meta("og:description") ?: meta("description")
        val image = meta("og:image")
        if (title == null && description == null && image == null) return null
        return OpenGraph(title?.trim(), description?.trim(), image?.trim())
    }

    private fun httpGet(url: String, maxBytes: Int = 100_000): String? {
        val connection = URL(url).openConnection() as? HttpURLConnection ?: return null
        return try {
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (YEEX link preview bot)")
            if (connection.responseCode !in 200..299) return null
            val bytes = connection.inputStream.use { it.readBytes(maxBytes) }
            String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** Reads at most [limit] bytes — a preview only ever needs the document's `<head>`. */
    private fun java.io.InputStream.readBytes(limit: Int): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (total < limit) {
            val read = this.read(chunk)
            if (read <= 0) break
            buffer.write(chunk, 0, read)
            total += read
        }
        return buffer.toByteArray()
    }
}
