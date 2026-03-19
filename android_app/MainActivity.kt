package com.jobhunter.ai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                val vm: MainViewModel = viewModel()

                LaunchedEffect(Unit) {
                    vm.loadFromPrefs()
                    vm.refreshBackendConfig()
                    JobWorker.start(this@MainActivity)
                }

                val toast = vm.consumeToast()
                if (toast != null) {
                    Toast.makeText(this@MainActivity, toast, Toast.LENGTH_SHORT).show()
                }

                JobHunterApp(vm) { url ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
        }
    }
}

private enum class Tab { Search, Saved, Match }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JobHunterApp(vm: MainViewModel, openUrl: (String) -> Unit) {
    var tab by remember { mutableStateOf(Tab.Search) }

    val s = vm.state.value

    var previewDialog by remember { mutableStateOf<ApplyPreview?>(null) }
    var detailsJob by remember { mutableStateOf<JobItem?>(null) }
    var modalError by remember { mutableStateOf<String?>(null) }

    fun runPreview(job: JobItem) {
        vm.selectJob(job)
        vm.previewApply { r ->
            r.onSuccess { previewDialog = it }
            r.onFailure { modalError = it.message ?: "erro" }
        }
    }

    if (previewDialog != null) {
        val p = previewDialog!!
        AlertDialog(
            onDismissRequest = { previewDialog = null },
            confirmButton = { Button(onClick = { openUrl(p.url) }) { Text("Abrir vaga") } },
            dismissButton = { Button(onClick = { previewDialog = null }) { Text("Fechar") } },
            title = { Text("Preview apply (seguro)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CTAs: ${p.detectedCtas.joinToString(", ").ifBlank { "(nenhum)" }}")
                    Divider()
                    p.notes.forEach { Text("- $it") }
                }
            }
        )
    }

    if (detailsJob != null) {
        val j = detailsJob!!
        AlertDialog(
            onDismissRequest = { detailsJob = null },
            confirmButton = {
                Button(onClick = {
                    vm.selectJob(j)
                    tab = Tab.Match
                    detailsJob = null
                }) { Text("Usar no match") }
            },
            dismissButton = { Button(onClick = { detailsJob = null }) { Text("Fechar") } },
            title = { Text("Detalhes") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(j.titulo.ifBlank { "(sem titulo)" }, fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull(j.plataforma, j.empresa, j.local).filter { it.isNotBlank() }.joinToString(" • "))
                    if (!j.resumo.isNullOrBlank()) Text(j.resumo)
                    Divider()
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { openUrl(j.link) }) { Text("Abrir") }
                        Button(onClick = { runPreview(j) }) { Text("Preview apply") }
                    }
                }
            }
        )
    }

    if (modalError != null) {
        AlertDialog(
            onDismissRequest = { modalError = null },
            confirmButton = { Button(onClick = { modalError = null }) { Text("OK") } },
            title = { Text("Erro") },
            text = { Text(modalError ?: "") }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("JobHunter AI", fontWeight = FontWeight.ExtraBold) }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.Search,
                    onClick = { tab = Tab.Search },
                    label = { Text("Buscar") },
                    icon = { Text("B") }
                )
                NavigationBarItem(
                    selected = tab == Tab.Saved,
                    onClick = { tab = Tab.Saved; vm.loadSaved() },
                    label = { Text("Salvas") },
                    icon = { Text("S") }
                )
                NavigationBarItem(
                    selected = tab == Tab.Match,
                    onClick = { tab = Tab.Match },
                    label = { Text("Match") },
                    icon = { Text("M") }
                )
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                Tab.Search -> SearchScreen(
                    vm = vm,
                    onDetails = { detailsJob = it },
                    onPreview = { runPreview(it) },
                    onOpen = { openUrl(it.link) }
                )
                Tab.Saved -> JobsListScreen(
                    title = "Vagas salvas no servidor",
                    jobs = s.savedJobs,
                    selected = s.selectedJob,
                    onSelect = { vm.selectJob(it) },
                    onDetails = { detailsJob = it },
                    onOpen = { openUrl(it.link) },
                    onPreview = { runPreview(it) }
                )
                Tab.Match -> MatchScreen(vm)
            }

            if (s.busy) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(tonalElevation = 6.dp, shape = MaterialTheme.shapes.large) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                            Text("Processando...")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(
    vm: MainViewModel,
    onDetails: (JobItem) -> Unit,
    onPreview: (JobItem) -> Unit,
    onOpen: (JobItem) -> Unit
) {
    val s = vm.state.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Configuracao", fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = s.baseUrl,
                    onValueChange = { vm.setBaseUrl(it) },
                    label = { Text("Backend URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = s.keyword,
                    onValueChange = { vm.setKeyword(it) },
                    label = { Text("Keyword") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = s.location,
                    onValueChange = { vm.setLocation(it) },
                    label = { Text("Local (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = s.limit.toString(),
                    onValueChange = { v -> vm.setLimit(v.toIntOrNull() ?: 20) },
                    label = { Text("Limite") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = if (s.allowNetworkCrawling) "Crawler de rede: ATIVO" else "Crawler de rede: DESATIVADO",
                    color = if (s.allowNetworkCrawling) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )

                Text("Fontes", fontWeight = FontWeight.Bold)
                FlowChips(items = s.availableSources, selected = s.selectedSources, onToggle = { vm.toggleSource(it) })

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { vm.persistSettings(); JobWorker.start(vm.getApplication()) }) { Text("Salvar") }
                    Button(onClick = { vm.searchNow() }) { Text("Buscar") }
                }

                if (s.errors.isNotEmpty()) {
                    Divider()
                    Text("Erros", fontWeight = FontWeight.Bold)
                    s.errors.take(6).forEach { Text("${it.source}: ${it.error}") }
                }
            }
        }

        JobsListScreen(
            title = "Resultados",
            jobs = s.jobs,
            selected = s.selectedJob,
            onSelect = { vm.selectJob(it) },
            onDetails = onDetails,
            onOpen = onOpen,
            onPreview = onPreview
        )
    }
}

@Composable
private fun JobsListScreen(
    title: String,
    jobs: List<JobItem>,
    selected: JobItem?,
    onSelect: (JobItem) -> Unit,
    onDetails: (JobItem) -> Unit,
    onOpen: (JobItem) -> Unit,
    onPreview: (JobItem) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(jobs.size.toString(), color = MaterialTheme.colorScheme.primary)
            }

            if (jobs.isEmpty()) {
                Text("Sem itens")
                return@Column
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(jobs) { j ->
                    JobCard(
                        job = j,
                        selected = selected?.link == j.link,
                        onSelect = { onSelect(j) },
                        onDetails = { onDetails(j) },
                        onOpen = { onOpen(j) },
                        onPreview = { onPreview(j) }
                    )
                }
            }
        }
    }
}

@Composable
private fun JobCard(
    job: JobItem,
    selected: Boolean,
    onSelect: () -> Unit,
    onDetails: () -> Unit,
    onOpen: () -> Unit,
    onPreview: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(job.titulo.ifBlank { "(sem titulo)" }, fontWeight = FontWeight.SemiBold)
                    val meta = listOfNotNull(job.plataforma, job.empresa, job.local).filter { it.isNotBlank() }
                    Text(meta.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
                }
                Text(if (selected) "Selecionada" else "", color = MaterialTheme.colorScheme.primary)
            }

            if (!job.resumo.isNullOrBlank()) {
                Text(job.resumo, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onDetails) { Text("Detalhes") }
                Button(onClick = onSelect) { Text("Match") }
                Button(onClick = onPreview) { Text("Preview") }
                Button(onClick = onOpen) { Text("Abrir") }
            }
        }
    }
}

@Composable
private fun MatchScreen(vm: MainViewModel) {
    val s = vm.state.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Match (IA)", fontWeight = FontWeight.Bold)

                if (s.openaiModel.isNotBlank()) {
                    Text("Modelo: ${s.openaiModel}", style = MaterialTheme.typography.bodySmall)
                }

                Text(
                    text = "Vaga selecionada: ${s.selectedJob?.titulo ?: "(nenhuma)"}",
                    style = MaterialTheme.typography.bodySmall
                )

                OutlinedTextField(
                    value = s.resume,
                    onValueChange = { vm.setResume(it) },
                    label = { Text("Curriculo (texto)") },
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                )

                OutlinedTextField(
                    value = s.jobDesc,
                    onValueChange = { vm.setJobDesc(it) },
                    label = { Text("Descricao da vaga (texto)") },
                    modifier = Modifier.fillMaxWidth().height(140.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { vm.calcScore() }) { Text("Calcular") }
                    if (s.lastScore != null) {
                        Text(
                            text = "Score: ${s.lastScore}/100",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }

                Text(
                    text = "Obs: cole a descricao da vaga manualmente. O app nao faz scraping de detalhes.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun FlowChips(items: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (items.isEmpty()) {
            Text("(sem fontes)")
            return
        }

        val chunked = items.chunked(3)
        chunked.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { src ->
                    FilterChip(
                        selected = selected.contains(src),
                        onClick = { onToggle(src) },
                        label = { Text(src) }
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFFFFB703),
        secondary = androidx.compose.ui.graphics.Color(0xFFFB7185),
        background = androidx.compose.ui.graphics.Color(0xFF0C0E12),
        surface = androidx.compose.ui.graphics.Color(0xFF141826),
    )

    MaterialTheme(colorScheme = scheme, content = content)
}
