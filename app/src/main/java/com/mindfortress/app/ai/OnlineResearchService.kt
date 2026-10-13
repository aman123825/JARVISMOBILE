package com.mindfortress.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class ResearchSource(
    val title: String,
    val url: String,
    val sourceType: String,
    val publishedYear: Int? = null,
    val citationCount: Int? = null,
    val excerpt: String = ""
)

data class ResearchBundle(val query: String, val sources: List<ResearchSource>) {
    fun asPromptContext(): String = buildString {
        append("Research question: ").append(query).append("\n\n")
        sources.forEachIndexed { index, source ->
            append("[S").append(index + 1).append("] ")
                .append(source.title).append("\n")
                .append("Type: ").append(source.sourceType)
            source.publishedYear?.let { append("; year: ").append(it) }
            source.citationCount?.let { append("; OpenAlex cited-by count: ").append(it) }
            append("\nURL: ").append(source.url).append("\n")
            if (source.excerpt.isNotBlank()) append("Retrieved text: ").append(source.excerpt).append("\n")
            append("\n")
        }
    }

    fun renderMarkdown(): String = buildString {
        append("## Online research results\n\n")
        append("Query: **").append(query).append("**\n\n")
        append("I retrieved these sources. This is a source list, not a claim that every result has been independently peer-reviewed.\n\n")
        sources.forEachIndexed { index, source ->
            append("### [S").append(index + 1).append("] [")
                .append(source.title.replace("[", "\\[").replace("]", "\\]"))
                .append("](").append(source.url).append(")\n")
            append(source.sourceType)
            source.publishedYear?.let { append(" · ").append(it) }
            source.citationCount?.let { append(" · OpenAlex cited-by count: ").append(it) }
            append("\n")
            if (source.excerpt.isNotBlank()) append(source.excerpt).append("\n")
            append("\n")
        }
        append("For a critical review, inspect the primary paper and its methods; citation counts and encyclopedia summaries are discovery aids, not proof.")
    }
}

/**
 * Explicit, online-only research fetcher.
 * Uses Wikipedia's search API for background context and OpenAlex's public works API for
 * scholarly discovery. It returns source URLs and excerpts to allow transparent synthesis.
 */
class OnlineResearchService {
    suspend fun search(query: String): ResearchBundle {
        val clean = query.trim()
        require(clean.isNotBlank()) { "Enter a research topic." }
        require(clean.length <= 240) { "Research query must be 240 characters or fewer." }

        val sourceGroups = coroutineScope {
            val encyclopedia = async(Dispatchers.IO) { searchWikipedia(clean) }
            val scholarly = async(Dispatchers.IO) { searchOpenAlex(clean) }
            listOf(encyclopedia.await(), scholarly.await())
        }
        val sources = sourceGroups.flatten().distinctBy { it.url }.take(MAX_SOURCES)
        if (sources.isEmpty()) throw IOException("No sources were returned for that query. Try different keywords.")
        return ResearchBundle(clean, sources)
    }

    private fun searchWikipedia(query: String): List<ResearchSource> {
        val encoded = encode(query)
        val url = "https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&utf8=1&srlimit=3&srsearch=$encoded"
        val root = JSONObject(fetchText(url))
        val items = root.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            val title = item.optString("title").trim()
            val pageId = item.optLong("pageid", 0L)
            if (title.isBlank() || pageId <= 0L) return@mapNotNull null
            ResearchSource(
                title = title,
                url = "https://en.wikipedia.org/?curid=$pageId",
                sourceType = "Wikipedia background article",
                excerpt = cleanMarkup(item.optString("snippet")).take(MAX_EXCERPT)
            )
        }
    }

    private fun searchOpenAlex(query: String): List<ResearchSource> {
        val encoded = encode(query)
        val url = "https://api.openalex.org/works?search=$encoded&per-page=5&select=id,doi,title,publication_year,cited_by_count,authorships,primary_location,abstract_inverted_index"
        val root = JSONObject(fetchText(url))
        val items = root.optJSONArray("results") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            val title = item.optString("title").trim()
            if (title.isBlank()) return@mapNotNull null
            val doi = item.optString("doi")
            val openAlexId = item.optString("id")
            val urlValue = when {
                doi.startsWith("https://doi.org/") -> doi
                openAlexId.startsWith("http") -> openAlexId
                else -> return@mapNotNull null
            }
            val authorships = item.optJSONArray("authorships")
            val firstAuthor = authorships?.optJSONObject(0)
                ?.optJSONObject("author")?.optString("display_name").orEmpty()
            val venue = item.optJSONObject("primary_location")
                ?.optJSONObject("source")?.optString("display_name").orEmpty()
            val type = listOf("Scholarly work", firstAuthor, venue)
                .filter { it.isNotBlank() }.distinct().joinToString(" · ")
            ResearchSource(
                title = title,
                url = urlValue,
                sourceType = type,
                publishedYear = item.optInt("publication_year").takeIf { it > 0 },
                citationCount = item.optInt("cited_by_count").takeIf { it >= 0 },
                excerpt = reconstructAbstract(item.optJSONObject("abstract_inverted_index")).take(MAX_EXCERPT)
            )
        }
    }

    private fun fetchText(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "JARVISMOBILE/1.0 (on-device research assistant)")
        return try {
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Research service returned HTTP $status.")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun reconstructAbstract(index: JSONObject?): String {
        if (index == null) return ""
        val words = mutableMapOf<Int, String>()
        val keys = index.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            val positions = index.optJSONArray(word) ?: continue
            for (j in 0 until positions.length()) {
                val position = positions.optInt(j, -1)
                if (position >= 0 && position < 5000) words[position] = word
            }
        }
        if (words.isEmpty()) return ""
        return (0..(words.keys.maxOrNull() ?: 0)).mapNotNull(words::get).joinToString(" ")
            .replace(Regex("\\s+"), " ")
    }

    private fun cleanMarkup(value: String): String =
        value.replace(Regex("<[^>]*>"), "")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    companion object {
        private const val MAX_SOURCES = 8
        private const val MAX_EXCERPT = 900
        private const val CONNECT_TIMEOUT_MS = 4_000
        private const val READ_TIMEOUT_MS = 7_000
    }
}
