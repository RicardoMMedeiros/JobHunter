const el = (id) => document.getElementById(id);

const state = {
  config: null,
  jobs: [],
  lastSelectedJob: null,
};

function setStatus(text, ok = true) {
  el('status').textContent = text;
  el('status').style.color = ok ? 'rgba(242,243,245,.72)' : 'rgba(251,113,133,.92)';
}

function selectedSources() {
  const boxes = Array.from(document.querySelectorAll('input[data-source]'));
  return boxes.filter(b => b.checked).map(b => b.getAttribute('data-source'));
}

function renderSources(available, defaults) {
  const container = el('sources');
  container.innerHTML = '';

  const defaultSet = new Set((defaults || '').split(',').map(s => s.trim().toLowerCase()).filter(Boolean));

  for (const src of available) {
    const label = document.createElement('label');
    label.className = 'chk';

    const input = document.createElement('input');
    input.type = 'checkbox';
    input.checked = defaultSet.size ? defaultSet.has(src) : (src === 'indeed');
    input.setAttribute('data-source', src);

    const txt = document.createElement('span');
    txt.textContent = src;

    label.appendChild(input);
    label.appendChild(txt);
    container.appendChild(label);
  }

  el('pillSources').textContent = `${available.length} fontes`;
}

function renderErrors(errors) {
  const container = el('errors');
  container.innerHTML = '';
  if (!errors || !errors.length) return;

  for (const e of errors) {
    const d = document.createElement('div');
    d.className = 'err';
    d.textContent = `${e.source}: ${e.error}`;
    container.appendChild(d);
  }
}

function renderJobs(jobs) {
  state.jobs = jobs || [];
  el('pillCount').textContent = String(state.jobs.length);

  const container = el('jobs');
  container.innerHTML = '';

  if (!state.jobs.length) {
    const empty = document.createElement('div');
    empty.className = 'hint';
    empty.textContent = 'Sem resultados. Tente outra keyword ou habilite o crawler de rede.';
    container.appendChild(empty);
    return;
  }

  for (const j of state.jobs) {
    const card = document.createElement('div');
    card.className = 'job';

    const top = document.createElement('div');
    top.className = 'row';

    const left = document.createElement('div');
    const name = document.createElement('div');
    name.className = 'name';
    name.textContent = j.titulo || j.title || '(sem titulo)';

    const meta = document.createElement('div');
    meta.className = 'meta';
    const platform = j.plataforma || j.platform || 'unknown';
    const company = j.empresa || j.company || '';
    const loc = j.local || j.location || '';
    meta.innerHTML = `<b>${platform}</b>${company ? ` • ${company}` : ''}${loc ? ` • ${loc}` : ''}`;

    left.appendChild(name);
    left.appendChild(meta);

    const right = document.createElement('div');
    right.style.display = 'flex';
    right.style.gap = '8px';

    const btnPick = document.createElement('button');
    btnPick.className = 'btn';
    btnPick.textContent = 'Usar no match';
    btnPick.addEventListener('click', () => {
      state.lastSelectedJob = j;
      openModal('Match', `Selecionado: ${escapeHtml(name.textContent)}<div class="code">job_url: ${escapeHtml(j.link || '')}</div>`);
    });

    const btnPrev = document.createElement('button');
    btnPrev.className = 'btn primary';
    btnPrev.textContent = 'Preview apply';
    btnPrev.addEventListener('click', async () => {
      await previewApply(j.link);
    });

    right.appendChild(btnPick);
    right.appendChild(btnPrev);

    top.appendChild(left);
    top.appendChild(right);

    const links = document.createElement('div');
    links.className = 'links';

    if (j.link) {
      const a = document.createElement('a');
      a.href = j.link;
      a.target = '_blank';
      a.rel = 'noreferrer';
      a.textContent = 'Abrir vaga';
      links.appendChild(a);
    }

    card.appendChild(top);
    card.appendChild(links);
    container.appendChild(card);
  }
}

function escapeHtml(s) {
  return String(s || '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
}

function openModal(title, bodyHtml) {
  el('modalTitle').textContent = title;
  el('modalBody').innerHTML = bodyHtml;
  el('modal').showModal();
}

function closeModal() {
  el('modal').close();
}

async function fetchJson(url, options) {
  const resp = await fetch(url, options);
  const text = await resp.text();
  let data = null;
  try { data = JSON.parse(text); } catch { /* ignore */ }
  if (!resp.ok) {
    const msg = (data && data.detail) ? data.detail : text;
    throw new Error(msg);
  }
  return data;
}

async function loadConfig() {
  setStatus('Conectando...');
  const cfg = await fetchJson('/config');
  state.config = cfg;

  renderSources(cfg.available_sources || [], cfg.sources_default || '');
  el('pillModel').textContent = cfg.openai_model || 'modelo';

  if (!cfg.allow_network_crawling) {
    el('hintCrawl').innerHTML = 'Crawler de rede esta <b>desativado</b>. Defina <span class="code">JOBHUNTER_ALLOW_NETWORK_CRAWLING=true</span> e reinicie o backend.';
  } else {
    el('hintCrawl').textContent = 'Crawler de rede ativo. Se alguma fonte bloquear, ela vai aparecer em Erros.';
  }

  setStatus('Conectado');
}

async function searchJobs(saved = false) {
  renderErrors([]);

  if (saved) {
    setStatus('Carregando salvas...');
    const jobs = await fetchJson('/jobs?limit=50');
    renderJobs(jobs);
    setStatus('Salvas carregadas');
    return;
  }

  const keyword = el('keyword').value.trim();
  const location = el('location').value.trim();
  const limit = Number(el('limit').value || 20);
  const sources = selectedSources().join(',');

  if (!keyword) {
    openModal('Falta keyword', 'Digite uma keyword para buscar.');
    return;
  }

  const qp = new URLSearchParams();
  qp.set('keyword', keyword);
  if (location) qp.set('location', location);
  qp.set('limit', String(limit));
  if (sources) qp.set('sources', sources);

  setStatus('Buscando...');
  try {
    const data = await fetchJson(`/buscar_debug?${qp.toString()}`);
    renderJobs(data.jobs || []);
    renderErrors(data.errors || []);
    setStatus('Busca concluida');
  } catch (e) {
    setStatus('Erro na busca', false);
    renderErrors([{ source: 'api', error: String(e.message || e) }]);
    renderJobs([]);
  }
}

async function scoreMatch() {
  const curriculo = el('resume').value.trim();
  const descricao = el('jobdesc').value.trim();
  if (!curriculo || !descricao) {
    openModal('Faltando texto', 'Preencha curriculo e descricao da vaga.');
    return;
  }

  const payload = {
    curriculo,
    descricao,
    job_url: state.lastSelectedJob ? (state.lastSelectedJob.link || null) : null,
  };

  el('score').innerHTML = 'Calculando...';
  try {
    const data = await fetchJson('/match', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    el('score').innerHTML = `Score: <strong>${data.score}</strong>/100`;
  } catch (e) {
    el('score').innerHTML = `<span style="color: rgba(251,113,133,.92)">Erro: ${escapeHtml(e.message || String(e))}</span>`;
  }
}

async function previewApply(url) {
  if (!url) return;
  openModal('Preview apply', 'Abrindo preview...');

  try {
    const data = await fetchJson('/apply/preview', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url, timeout_ms: 15000 }),
    });

    const ctas = (data.detected_ctas || []).map(escapeHtml).join(', ') || '(nenhum)';
    const notes = (data.notes || []).map(n => `<li>${escapeHtml(n)}</li>`).join('');

    openModal(
      'Preview apply (seguro)',
      `<div class="hint">Esta funcao nao envia candidatura automaticamente.</div>
       <div class="hint"><b>CTAs detectados:</b> ${ctas}</div>
       <div class="hint"><b>URL:</b> <a href="${escapeHtml(url)}" target="_blank" rel="noreferrer">abrir vaga</a></div>
       <ul>${notes}</ul>`
    );
  } catch (e) {
    openModal('Preview apply (erro)', `<div class="err">${escapeHtml(e.message || String(e))}</div>`);
  }
}

function wire() {
  el('btnSearch').addEventListener('click', () => searchJobs(false));
  el('btnSaved').addEventListener('click', () => searchJobs(true));
  el('btnScore').addEventListener('click', scoreMatch);

  el('modalClose').addEventListener('click', closeModal);
  el('modal').addEventListener('click', (e) => {
    const rect = el('modal').getBoundingClientRect();
    const inDialog = rect.top <= e.clientY && e.clientY <= rect.bottom && rect.left <= e.clientX && e.clientX <= rect.right;
    if (!inDialog) closeModal();
  });
}

(async function init() {
  wire();
  try {
    await loadConfig();
  } catch (e) {
    setStatus('Nao conectou no backend', false);
    renderErrors([{ source: 'config', error: String(e.message || e) }]);
  }
})();
