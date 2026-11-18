package com.disiplinai.ai

import android.content.Context
import com.disiplinai.data.ChatMsg
import com.disiplinai.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Klien REST Gemini dengan pemilihan model otomatis:
 * daftar model diambil dari API key pengguna, dan jika satu model gagal (404/429/503)
 * otomatis mencoba model berikutnya.
 */
object GeminiClient {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
    private val EXCLUDE = listOf(
        "tts", "live", "image", "audio", "robotics", "embedding", "computer-use", "omni",
        "veo", "lyria", "imagen", "antigravity", "research", "gemma", "learnlm", "aqa", "vision", "nano"
    )

    class ApiError(val code: Int, message: String) : IOException(message)

    // ------------------------------------------------------------------ HTTP dasar
    private fun conn(url: String, key: String, post: Boolean, timeoutMs: Int): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = if (post) "POST" else "GET"
        c.connectTimeout = 15_000
        c.readTimeout = timeoutMs
        c.setRequestProperty("x-goog-api-key", key.trim())
        if (post) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
        }
        return c
    }

    private fun read(c: HttpURLConnection): String {
        val code = c.responseCode
        val stream = if (code in 200..299) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw ApiError(code, friendly(code, text))
        return text
    }

    private fun friendly(code: Int, raw: String): String {
        val msg = try {
            JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
        } catch (e: Exception) { "" }
        return when {
            code == 429 -> "Kuota gratis model ini sedang habis/terlalu cepat."
            code == 404 -> "Model tidak tersedia lagi: ${msg.take(80)}"
            code == 400 && msg.contains("API key", true) -> "API key salah. Cek lagi di Setelan."
            code == 403 -> "API key ditolak (403). Pastikan key dari Google AI Studio dan masih aktif."
            code == 503 -> "Server model sedang sibuk."
            else -> "Error $code: ${msg.ifBlank { raw.take(120) }}"
        }
    }

    // ------------------------------------------------------------------ daftar model
    private fun tier(n: String) = when {
        n.contains("flash-lite") -> 1
        n.contains("flash") -> 0
        n.contains("pro") -> 2
        else -> 3
    }

    private fun version(n: String): Double =
        Regex("gemini-(\\d+(?:\\.\\d+)?)").find(n)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

    /** Urutan pilihan: flash terbaru, lalu flash-lite terbaru, lalu pro. */
    private fun rank(list: List<String>): List<String> =
        list.sortedWith(compareBy<String>({ tier(it) }, { -version(it) }, { it.contains("preview") }))

    private fun fetchModels(key: String): List<String> {
        val out = ArrayList<String>()
        var token = ""
        var pages = 0
        do {
            val url = "$BASE/models?pageSize=1000" + if (token.isNotEmpty()) "&pageToken=$token" else ""
            val c = conn(url, key, false, 30_000)
            val text = try { read(c) } finally { c.disconnect() }
            val o = JSONObject(text)
            val arr = o.optJSONArray("models")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val m = arr.getJSONObject(i)
                    val name = m.optString("name").removePrefix("models/")
                    val methods = m.optJSONArray("supportedGenerationMethods")
                    var ok = false
                    if (methods != null) for (j in 0 until methods.length()) {
                        if (methods.optString(j) == "generateContent") ok = true
                    }
                    if (ok && name.startsWith("gemini-") && EXCLUDE.none { name.contains(it) }) out.add(name)
                }
            }
            token = if (o.has("nextPageToken")) o.optString("nextPageToken") else ""
            pages++
        } while (token.isNotEmpty() && pages < 10)
        return rank(out.distinct())
    }

    /** Ambil daftar model teks dari key pengguna, simpan, dan pilih otomatis bila perlu. */
    suspend fun refreshModels(ctx: Context): List<String> = withContext(Dispatchers.IO) {
        val s = Store.get(ctx)
        val key = s.config.value.apiKey
        if (key.isBlank()) throw IllegalStateException("API key Gemini belum diisi.")
        val list = fetchModels(key)
        s.updateConfig { c ->
            c.copy(
                modelCandidates = list.take(8),
                model = if (list.isNotEmpty() && (c.model.isBlank() || c.model !in list)) list.first() else c.model
            )
        }
        list
    }

    // ------------------------------------------------------------------ generate
    suspend fun generate(
        ctx: Context,
        system: String,
        history: List<ChatMsg>,
        jsonMode: Boolean = false,
        timeoutMs: Int = 60_000
    ): String = withContext(Dispatchers.IO) {
        val s = Store.get(ctx)
        var cfg = s.config.value
        if (cfg.apiKey.isBlank()) {
            throw IllegalStateException("API key Gemini belum diisi. Buka tab Setelan > Agent AI.")
        }
        if (cfg.model.isBlank() || cfg.modelCandidates.isEmpty()) {
            try { refreshModels(ctx) } catch (e: ApiError) {
                if (e.code == 400 || e.code == 403) throw e
            } catch (e: Exception) { /* lanjut dengan yang ada */ }
            cfg = s.config.value
        }
        val order = (listOf(cfg.model.trim()) + cfg.modelCandidates).filter { it.isNotBlank() }.distinct().take(5)
        if (order.isEmpty()) {
            throw IllegalStateException("Belum ada model yang bisa dipakai. Cek API key lalu tekan \"Ambil daftar model\" di Setelan.")
        }

        var last: ApiError? = null
        for (m in order) {
            try {
                val r = call(cfg.apiKey, m, system, history, jsonMode, timeoutMs)
                if (m != s.config.value.model) s.updateConfig { it.copy(model = m) }
                return@withContext r
            } catch (e: ApiError) {
                last = e
                if (e.code != 404 && e.code != 429 && e.code != 503) throw e
            }
        }
        throw IOException("Semua model gagal (${order.joinToString()}). Terakhir: ${last?.message}")
    }

    private fun call(
        key: String, model: String, system: String, history: List<ChatMsg>,
        jsonMode: Boolean, timeoutMs: Int
    ): String {
        val contents = JSONArray()
        history.forEach { m ->
            contents.put(
                JSONObject()
                    .put("role", if (m.role == "user") "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", m.text)))
            )
        }
        val gen = JSONObject().put("temperature", 0.7)
        if (jsonMode) gen.put("responseMimeType", "application/json")
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", gen)

        val c = conn("$BASE/models/$model:generateContent", key, true, timeoutMs)
        try {
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val text = read(c)
            val parts = JSONObject(text).optJSONArray("candidates")
                ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                ?: throw IOException("Respons AI kosong (mungkin diblokir filter keamanan). Coba lagi.")
            return buildString {
                for (i in 0 until parts.length()) append(parts.getJSONObject(i).optString("text"))
            }.ifBlank { throw IOException("Respons AI kosong. Coba lagi.") }
        } finally {
            c.disconnect()
        }
    }
}
