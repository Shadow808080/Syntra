package com.example.syntra.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Lightweight text translation for chat messages, via the public, key-less
 * Google Translate `gtx` endpoint. Auto-detects the source language and returns
 * the translation in [target] (Indonesian by default, to match the app's UI).
 *
 * Best-effort: returns null on any failure so the caller can fall back to a toast.
 */
object Translate {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun translate(text: String, target: String = "id"): String? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        runCatching {
            val q = URLEncoder.encode(text, "UTF-8")
            val url = "https://translate.googleapis.com/translate_a/single" +
                "?client=gtx&sl=auto&tl=$target&dt=t&q=$q"
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val bodyStr = resp.body?.string() ?: return@use null
                // Shape: [[["translated","original",...],[...]], ...]
                val segs = JSONArray(bodyStr).optJSONArray(0) ?: return@use null
                val sb = StringBuilder()
                for (i in 0 until segs.length()) {
                    segs.optJSONArray(i)?.optString(0)?.let { sb.append(it) }
                }
                sb.toString().ifBlank { null }
            }
        }.getOrNull()
    }
}
