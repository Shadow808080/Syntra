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
 * Online GIF search via the GIPHY API.
 *
 * There is no longer any keyless public GIF API (GIPHY's old public beta key is
 * banned, Tenor v1 is discontinued), so online search needs a FREE API key. GIPHY
 * is the quickest to get one:
 *   1. Open https://developers.giphy.com/dashboard/ and sign in (free).
 *   2. "Create an App" -> pick "API" (not SDK) -> copy the API Key.
 *   3. Paste it into [GIPHY_API_KEY] below and rebuild.
 * Until then the GIF tab still works via "GIF dari galeri HP" (device GIFs).
 *
 * We fetch a light `fixed_width_small` for the picker grid and a `downsized_medium`
 * (fallback `original`) as what actually gets sent into the chat.
 */
object GifClient {

    /** <- Paste your free GIPHY API key here (see the steps above). */
    const val GIPHY_API_KEY = "VRSv5QVB4P0tAcq2r52Z4G2mUyKGOoQD"

    val configured: Boolean get() = GIPHY_API_KEY.isNotBlank()

    private const val RATING = "pg-13"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** Trending GIFs, shown before the user types anything. */
    suspend fun featured(limit: Int = 24): List<GifItem> = request(
        "https://api.giphy.com/v1/gifs/trending?api_key=$GIPHY_API_KEY&limit=$limit&rating=$RATING",
    )

    /** Search GIFs for [query]. */
    suspend fun search(query: String, limit: Int = 24): List<GifItem> {
        if (query.isBlank()) return featured(limit)
        val q = URLEncoder.encode(query, "UTF-8")
        return request(
            "https://api.giphy.com/v1/gifs/search?api_key=$GIPHY_API_KEY&q=$q&limit=$limit&rating=$RATING",
        )
    }

    /**
     * GIPHY Animate ("generate"): turns a text phrase into a set of animated *text*
     * GIFs in different styles, which the user can pick and send. This is the
     * "buat/generate GIF" feature — the phrase becomes the GIF, no matching search.
     */
    suspend fun animate(text: String): List<GifItem> {
        if (text.isBlank()) return emptyList()
        val m = URLEncoder.encode(text, "UTF-8")
        return request("https://api.giphy.com/v1/text/animate?api_key=$GIPHY_API_KEY&m=$m")
    }

    private suspend fun request(url: String): List<GifItem> = withContext(Dispatchers.IO) {
        if (!configured) return@withContext emptyList()
        runCatching {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val body = resp.body?.string() ?: return@use emptyList()
                val results = JSONObject(body).optJSONArray("data") ?: return@use emptyList()
                (0 until results.length()).mapNotNull { i ->
                    val r = results.optJSONObject(i) ?: return@mapNotNull null
                    val images = r.optJSONObject("images") ?: return@mapNotNull null
                    val preview = images.optJSONObject("fixed_width_small")?.optString("url")?.ifBlank { null }
                        ?: images.optJSONObject("fixed_width")?.optString("url").orEmpty()
                    val send = images.optJSONObject("downsized_medium")?.optString("url")?.ifBlank { null }
                        ?: images.optJSONObject("original")?.optString("url")?.ifBlank { null }
                        ?: images.optJSONObject("fixed_width")?.optString("url")?.ifBlank { null }
                        ?: preview
                    if (preview.isBlank() || send.isBlank()) return@mapNotNull null
                    GifItem(id = r.optString("id", i.toString()), previewUrl = preview, sendUrl = send)
                }
            }
        }.getOrDefault(emptyList())
    }
}
