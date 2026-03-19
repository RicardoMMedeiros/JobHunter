package com.jobhunter.ai

import android.content.Context

object SettingsStore {
    private const val PREFS = "jobhunter_prefs"

    const val KEY_BASE_URL = "base_url"
    const val KEY_KEYWORD = "keyword"
    const val KEY_LOCATION = "location"
    const val KEY_LIMIT = "limit"
    const val KEY_SOURCES = "sources"

    const val DEFAULT_BASE_URL = "http://10.0.2.2:8000"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun baseUrl(context: Context): String = prefs(context).getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    fun keyword(context: Context): String = prefs(context).getString(KEY_KEYWORD, "dados") ?: "dados"
    fun location(context: Context): String = prefs(context).getString(KEY_LOCATION, "") ?: ""
    fun limit(context: Context): Int = prefs(context).getInt(KEY_LIMIT, 20)
    fun sources(context: Context): String = prefs(context).getString(KEY_SOURCES, "") ?: ""

    fun save(
        context: Context,
        baseUrl: String,
        keyword: String,
        location: String,
        limit: Int,
        sources: String
    ) {
        prefs(context).edit()
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_KEYWORD, keyword)
            .putString(KEY_LOCATION, location)
            .putInt(KEY_LIMIT, limit)
            .putString(KEY_SOURCES, sources)
            .apply()
    }
}
