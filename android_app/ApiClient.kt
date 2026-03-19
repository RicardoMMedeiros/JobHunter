package com.jobhunter.ai

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

object ApiClient {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private fun client(): OkHttpClient = OkHttpClient()

    private fun normalizeBaseUrl(baseUrl: String): String {
        val b = baseUrl.trim().removeSuffix("/")
        return if (b.isEmpty()) SettingsStore.DEFAULT_BASE_URL else b
    }

    private fun readText(resp: okhttp3.Response): String {
        return resp.body?.string() ?: ""
    }

    fun getConfig(context: Context): BackendConfig {
        val baseUrl = normalizeBaseUrl(SettingsStore.baseUrl(context))
        val req = Request.Builder().url("$baseUrl/config").build()

        client().newCall(req).execute().use { resp ->
            val text = readText(resp)
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}: $text")
            val o = JSONObject(text)
            return BackendConfig(
                allowNetworkCrawling = o.optBoolean("allow_network_crawling", false),
                sourcesDefault = o.optString("sources_default", ""),
                availableSources = o.optJSONArray("available_sources")?.toStringList() ?: emptyList(),
                gupyCompanyUrls = o.optJSONArray("gupy_company_urls")?.toStringList() ?: emptyList(),
                openaiModel = o.optString("openai_model", "")
            )
        }
    }

    fun buscarDebug(context: Context): SearchResult {
        val baseUrl = normalizeBaseUrl(SettingsStore.baseUrl(context))
        val keyword = SettingsStore.keyword(context)
        val location = SettingsStore.location(context)
        val limit = SettingsStore.limit(context)
        val sources = SettingsStore.sources(context)

        val qp = StringBuilder()
        qp.append("keyword=").append(URLEncoder.encode(keyword, "UTF-8"))
        if (location.isNotBlank()) qp.append("&location=").append(URLEncoder.encode(location, "UTF-8"))
        qp.append("&limit=").append(limit)
        if (sources.isNotBlank()) qp.append("&sources=").append(URLEncoder.encode(sources, "UTF-8"))

        val req = Request.Builder().url("$baseUrl/buscar_debug?$qp").build()

        client().newCall(req).execute().use { resp ->
            val text = readText(resp)
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}: $text")

            val o = JSONObject(text)
            val jobs = o.optJSONArray("jobs")?.toJobs() ?: emptyList()
            val errors = o.optJSONArray("errors")?.toErrors() ?: emptyList()
            return SearchResult(jobs = jobs, errors = errors)
        }
    }

    fun listarSalvas(context: Context, limit: Int = 50): List<JobItem> {
        val baseUrl = normalizeBaseUrl(SettingsStore.baseUrl(context))
        val req = Request.Builder().url("$baseUrl/jobs?limit=$limit").build()

        client().newCall(req).execute().use { resp ->
            val text = readText(resp)
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}: $text")

            val arr = JSONArray(text)
            val out = ArrayList<JobItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    JobItem(
                        titulo = o.optString("title", ""),
                        empresa = o.optString("company", null),
                        local = o.optString("location", null),
                        plataforma = o.optString("platform", "unknown"),
                        link = o.optString("url", ""),
                        resumo = o.optString("snippet", null)
                    )
                )
            }
            return out
        }
    }

    fun previewApply(context: Context, url: String): ApplyPreview {
        val baseUrl = normalizeBaseUrl(SettingsStore.baseUrl(context))
        val payload = JSONObject().put("url", url).put("timeout_ms", 15000)
        val body = payload.toString().toRequestBody(jsonMedia)

        val req = Request.Builder()
            .url("$baseUrl/apply/preview")
            .post(body)
            .build()

        client().newCall(req).execute().use { resp ->
            val text = readText(resp)
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}: $text")
            val o = JSONObject(text)
            return ApplyPreview(
                url = o.optString("url", url),
                detectedCtas = o.optJSONArray("detected_ctas")?.toStringList() ?: emptyList(),
                notes = o.optJSONArray("notes")?.toStringList() ?: emptyList(),
                capturedAt = o.optString("captured_at", "")
            )
        }
    }

    fun match(context: Context, curriculo: String, descricao: String, jobUrl: String?): Int {
        val baseUrl = normalizeBaseUrl(SettingsStore.baseUrl(context))
        val payload = JSONObject()
            .put("curriculo", curriculo)
            .put("descricao", descricao)
        if (!jobUrl.isNullOrBlank()) payload.put("job_url", jobUrl)

        val body = payload.toString().toRequestBody(jsonMedia)

        val req = Request.Builder()
            .url("$baseUrl/match")
            .post(body)
            .build()

        client().newCall(req).execute().use { resp ->
            val text = readText(resp)
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}: $text")
            val o = JSONObject(text)
            return o.optInt("score", 0)
        }
    }

    private fun JSONArray.toStringList(): List<String> {
        val out = ArrayList<String>()
        for (i in 0 until length()) out.add(optString(i))
        return out
    }

    private fun JSONArray.toErrors(): List<SearchError> {
        val out = ArrayList<SearchError>()
        for (i in 0 until length()) {
            val o = getJSONObject(i)
            out.add(SearchError(source = o.optString("source", ""), error = o.optString("error", "")))
        }
        return out
    }

    private fun JSONArray.toJobs(): List<JobItem> {
        val out = ArrayList<JobItem>()
        for (i in 0 until length()) {
            val o = getJSONObject(i)
            out.add(
                JobItem(
                    titulo = o.optString("titulo", ""),
                    empresa = o.optString("empresa", null),
                    local = o.optString("local", null),
                    plataforma = o.optString("plataforma", "unknown"),
                    link = o.optString("link", ""),
                    resumo = o.optString("resumo", null)
                )
            )
        }
        return out
    }
}
