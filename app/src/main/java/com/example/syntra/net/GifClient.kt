package com.example.syntra.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** One GIF result: a small animated [previewUrl] for the grid, [sendUrl] to send. */
data class GifItem(
    val id: String,
    val previewUrl: String,
    val sendUrl: String,
)

/**
 * GIF search via the Tenor (Google) v2 API. Needs an API key from Google Cloud
 * (enable the "Tenor API"). Paste it into [TENOR_API_KEY]; until then the GIF tab
 * shows a setup hint instead of results.
 *
 * We fetch two formats per result: `tinygif` (light, for the picker grid) and
 * `mediumgif`/`gif` (what actually gets sent into the chat).
 */
object GifClient {

    /** ← Paste your Tenor API key here. Get one at https://developers.google.com/tenor. */
    const val TENOR_API_KEY = ""

    val configured: Boolean get() = TENOR_API_KEY.isNotBlank()

    private const val CLIENT_KEY = "syntra_android"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** Trending GIFs, shown before the user types anything. */
    suspend fun featured(limit: Int = 24): List<GifItem> = request(
        "https://tenor.googleapis.com/v2/featured?key=$TENOR_API_KEY&client_key=$CLIENT_KEY" +
            "&limit=$limit&media_filter=tinygif,mediumgif,gif&contentfilter=high",
    )

    /** Search GIFs for [query]. */
    suspend fun search(query: String, limit: Int = 24): List<GifItem> {
        if (query.isBlank()) return featured(limit)
        val q = URLEncoder.encode(query, "UTF-8")
        return request(
            "https://tenor.googleapis.com/v2/search?q=$q&key=$TENOR_API_KEY&client_key=$CLIENT_KEY" +
                "&limit=$limit&media_filter=tinygif,mediumgif,gif&contentfilter=high",
        )
    }

    private suspend fun request(url: String): List<GifItem> = withContext(Dispatchers.IO) {
        if (!configured) return@withContext emptyList()
        runCatching {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val body = resp.body?.string() ?: return@use emptyList()
                val results = JSONObject(body).optJSONArray("results") ?: return@use emptyList()
                (0 until results.length()).mapNotNull { i ->
                    val r = results.optJSONObject(i) ?: return@mapNotNull null
                    val formats = r.optJSONObject("media_formats") ?: return@mapNotNull null
                    val preview = formats.optJSONObject("tinygif")?.optString("url").orEmpty()
                    val send = formats.optJSONObject("mediumgif")?.optString("url")?.ifBlank { null }
                        ?: formats.optJSONObject("gif")?.optString("url")?.ifBlank { null }
                        ?: preview
                    if (preview.isBlank() || send.isBlank()) return@mapNotNull null
                    GifItem(id = r.optString("id", i.toString()), previewUrl = preview, sendUrl = send)
                }
            }
        }.getOrDefault(emptyList())
    }
}
