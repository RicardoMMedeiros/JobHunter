package com.jobhunter.ai

data class JobItem(
    val titulo: String,
    val empresa: String?,
    val local: String?,
    val plataforma: String,
    val link: String,
    val resumo: String?
)

data class SearchError(
    val source: String,
    val error: String
)

data class SearchResult(
    val jobs: List<JobItem>,
    val errors: List<SearchError>
)

data class ApplyPreview(
    val url: String,
    val detectedCtas: List<String>,
    val notes: List<String>,
    val capturedAt: String
)

data class BackendConfig(
    val allowNetworkCrawling: Boolean,
    val sourcesDefault: String,
    val availableSources: List<String>,
    val gupyCompanyUrls: List<String>,
    val openaiModel: String
)
