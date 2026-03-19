package com.jobhunter.ai

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    data class UiState(
        val baseUrl: String = SettingsStore.DEFAULT_BASE_URL,
        val keyword: String = "dados",
        val location: String = "",
        val limit: Int = 20,
        val availableSources: List<String> = listOf("indeed", "vagascom", "nerdin", "mentoradados", "gupy"),
        val selectedSources: Set<String> = setOf("indeed"),
        val allowNetworkCrawling: Boolean = false,
        val openaiModel: String = "",

        val jobs: List<JobItem> = emptyList(),
        val errors: List<SearchError> = emptyList(),
        val savedJobs: List<JobItem> = emptyList(),

        val selectedJob: JobItem? = null,

        val resume: String = "",
        val jobDesc: String = "",
        val lastScore: Int? = null,

        val busy: Boolean = false,
        val toast: String? = null
    )

    var state: MutableState<UiState> = mutableStateOf(UiState())
        private set

    private fun ctx() = getApplication<Application>().applicationContext

    fun loadFromPrefs() {
        val c = ctx()
        val savedSources = SettingsStore.sources(c)
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()

        state.value = state.value.copy(
            baseUrl = SettingsStore.baseUrl(c),
            keyword = SettingsStore.keyword(c),
            location = SettingsStore.location(c),
            limit = SettingsStore.limit(c),
            selectedSources = if (savedSources.isEmpty()) state.value.selectedSources else savedSources
        )
    }

    fun persistSettings() {
        val c = ctx()
        SettingsStore.save(
            context = c,
            baseUrl = state.value.baseUrl,
            keyword = state.value.keyword,
            location = state.value.location,
            limit = state.value.limit,
            sources = state.value.selectedSources.joinToString(",")
        )
    }

    fun refreshBackendConfig() {
        viewModelScope.launch {
            setBusy(true)
            try {
                val cfg = withContext(Dispatchers.IO) { ApiClient.getConfig(ctx()) }

                val defaults = cfg.sourcesDefault.split(",")
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .toSet()

                state.value = state.value.copy(
                    allowNetworkCrawling = cfg.allowNetworkCrawling,
                    availableSources = if (cfg.availableSources.isEmpty()) state.value.availableSources else cfg.availableSources,
                    selectedSources = if (state.value.selectedSources.isEmpty()) defaults else state.value.selectedSources,
                    openaiModel = cfg.openaiModel
                )

                if (SettingsStore.sources(ctx()).isBlank() && defaults.isNotEmpty()) {
                    state.value = state.value.copy(selectedSources = defaults)
                    persistSettings()
                }
            } catch (e: Exception) {
                toast("Falha ao carregar /config: ${e.message}")
            } finally {
                setBusy(false)
            }
        }
    }

    fun setBaseUrl(v: String) { state.value = state.value.copy(baseUrl = v) }
    fun setKeyword(v: String) { state.value = state.value.copy(keyword = v) }
    fun setLocation(v: String) { state.value = state.value.copy(location = v) }
    fun setLimit(v: Int) { state.value = state.value.copy(limit = v) }

    fun toggleSource(src: String) {
        val cur = state.value.selectedSources.toMutableSet()
        if (cur.contains(src)) cur.remove(src) else cur.add(src)
        state.value = state.value.copy(selectedSources = cur)
    }

    fun searchNow() {
        persistSettings()
        viewModelScope.launch {
            setBusy(true)
            try {
                val res = withContext(Dispatchers.IO) { ApiClient.buscarDebug(ctx()) }
                state.value = state.value.copy(jobs = res.jobs, errors = res.errors)
                if (res.jobs.isEmpty()) toast("Sem resultados")
            } catch (e: Exception) {
                state.value = state.value.copy(
                    jobs = emptyList(),
                    errors = listOf(SearchError("api", e.message ?: "erro"))
                )
                toast("Erro na busca")
            } finally {
                setBusy(false)
            }
        }
    }

    fun loadSaved() {
        viewModelScope.launch {
            setBusy(true)
            try {
                val jobs = withContext(Dispatchers.IO) { ApiClient.listarSalvas(ctx(), limit = 80) }
                state.value = state.value.copy(savedJobs = jobs)
            } catch (e: Exception) {
                toast("Erro ao carregar salvas: ${e.message}")
            } finally {
                setBusy(false)
            }
        }
    }

    fun selectJob(job: JobItem?) {
        state.value = state.value.copy(selectedJob = job)
    }

    fun setResume(v: String) { state.value = state.value.copy(resume = v) }
    fun setJobDesc(v: String) { state.value = state.value.copy(jobDesc = v) }

    fun calcScore() {
        val resume = state.value.resume.trim()
        val desc = state.value.jobDesc.trim()
        if (resume.isEmpty() || desc.isEmpty()) {
            toast("Preencha curriculo e descricao")
            return
        }

        viewModelScope.launch {
            setBusy(true)
            try {
                val score = withContext(Dispatchers.IO) {
                    ApiClient.match(ctx(), resume, desc, state.value.selectedJob?.link)
                }
                state.value = state.value.copy(lastScore = score)
            } catch (e: Exception) {
                toast("Erro no match: ${e.message}")
            } finally {
                setBusy(false)
            }
        }
    }

    fun previewApply(onResult: (Result<ApplyPreview>) -> Unit) {
        val url = state.value.selectedJob?.link
        if (url.isNullOrBlank()) {
            onResult(Result.failure(IllegalStateException("Nenhuma vaga selecionada")))
            return
        }

        viewModelScope.launch {
            setBusy(true)
            try {
                val preview = withContext(Dispatchers.IO) { ApiClient.previewApply(ctx(), url) }
                onResult(Result.success(preview))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            } finally {
                setBusy(false)
            }
        }
    }

    fun consumeToast(): String? {
        val t = state.value.toast
        if (t != null) state.value = state.value.copy(toast = null)
        return t
    }

    private fun toast(msg: String) {
        state.value = state.value.copy(toast = msg)
    }

    private fun setBusy(b: Boolean) {
        state.value = state.value.copy(busy = b)
    }
}
