/**
 * FraudLens — app.js
 * All fetch() calls to the Spring Boot API, Vis.js graph logic,
 * date filter, display mode switching, and account table sorting.
 * No frameworks. No npm. Pure vanilla JavaScript.
 *
 * Status labels used everywhere: CRITICAL, HIGH, MEDIUM, LOW, NORMAL
 */

'use strict';

const API = '/fraudlens/api';

// ─────────────────────────────────────────────
// DASHBOARD (index.html)
// ─────────────────────────────────────────────

function loadDashboard() {
  refreshStats();
  refreshAlerts();
  refreshFeed();
  // Poll every 3 seconds
  setInterval(() => { refreshStats(); refreshFeed(); }, 3000);
}

async function refreshStats() {
  try {
    const data = await fetchJSON(`${API}/stats`);
    setText('stat-total',    data.totalTransactions.toLocaleString());
    setText('stat-accounts', data.totalAccounts);
    setText('stat-cycles',   data.cyclesDetected);
    setText('stat-hubs',     data.hubAccounts);
    setText('stat-rapids',   data.rapidHopAlerts);
    setText('stat-alerts',   data.fraudAlerts);
  } catch (e) { console.error('Stats error', e); }
}

async function refreshAlerts() {
  const list = document.getElementById('alert-list');
  if (!list) return;
  try {
    const [cycles, hubs, rapids, thresholds] = await Promise.all([
      fetchJSON(`${API}/fraud/cycles`),
      fetchJSON(`${API}/fraud/hubs`),
      fetchJSON(`${API}/fraud/rapid-hops`),
      fetchJSON(`${API}/fraud/thresholds`)
    ]);

    const alerts = [];
    cycles.forEach(c => alerts.push({ type: 'CYCLE', desc: 'Circular pattern: ' + c.join(' → ') + ' → ' + c[0], accts: c }));
    if (hubs.length)       alerts.push({ type: 'HUB',       desc: 'High-degree hub accounts detected',          accts: hubs });
    if (rapids.length)     alerts.push({ type: 'RAPID_HOP',  desc: 'Rapid fund-layering within 5-minute window', accts: rapids });
    if (thresholds.length) alerts.push({ type: 'THRESHOLD',  desc: 'Structuring / smurfing below reporting threshold', accts: thresholds });

    setText('alert-count', alerts.length + ' alert' + (alerts.length !== 1 ? 's' : ''));

    if (alerts.length === 0) {
      list.innerHTML = '<div class="empty"><div class="empty-icon">✅</div><p>No fraud patterns detected.</p></div>';
      return;
    }

    list.innerHTML = alerts.map(a => `
      <div class="alert-item ${a.type}">
        <div class="alert-icon">${alertIcon(a.type)}</div>
        <div class="alert-body">
          <div class="alert-type">${a.type.replace('_', ' ')}</div>
          <div class="alert-desc">${a.desc}</div>
          <div class="alert-accts">${a.accts.slice(0, 8).join(', ')}${a.accts.length > 8 ? ' +' + (a.accts.length - 8) + ' more' : ''}</div>
        </div>
      </div>`).join('');
  } catch (e) { list.innerHTML = '<div class="empty"><p>Failed to load alerts.</p></div>'; }
}

async function refreshFeed() {
  const feed = document.getElementById('txn-feed');
  if (!feed) return;
  try {
    const txns = await fetchJSON(`${API}/transactions`);
    if (!txns.length) { feed.innerHTML = '<div class="empty"><p>No transactions yet.</p></div>'; return; }
    feed.innerHTML = txns.slice(0, 30).map(t => `
      <div class="txn-row">
        <div class="acct">${t.fromAccount}</div>
        <div class="arrow">→</div>
        <div class="acct">${t.toAccount}</div>
        <div class="amount">₹${Math.round(t.amount).toLocaleString()}</div>
        <div class="type-tag"><span class="badge badge-${(t.type || 'NORMAL').toLowerCase()}">${t.type || 'NORMAL'}</span></div>
        <div style="font-size:11px;color:var(--text-muted);">${fmtDate(t.timestamp)}</div>
      </div>`).join('');
  } catch (e) { feed.innerHTML = '<div class="empty"><p>Failed to load feed.</p></div>'; }
}

// ─────────────────────────────────────────────
// GRAPH PAGE (graph.html)
// ─────────────────────────────────────────────

let network = null;
let currentViewMode = 'month';

async function loadGraph() {
  // Hide date picker initially for month view
  const datePicker = document.getElementById('date-picker');
  const dateLabel = document.getElementById('date-label');
  if (datePicker) datePicker.style.display = 'none';
  if (dateLabel) dateLabel.style.display = 'none';
  
  await updateGraph();
}

async function updateGraph() {
  const datePicker = document.getElementById('date-picker');
  const date = datePicker ? datePicker.value : '2024-01-18';
  
  try {
    const data = await fetchJSON(`${API}/graph?view=${currentViewMode}&date=${date}`);
    renderNetwork(data.nodes, data.edges);
  } catch (e) {
    document.getElementById('network-container').innerHTML =
      '<div style="display:flex;align-items:center;justify-content:center;height:100%;color:rgba(255,255,255,0.5)">Failed to load graph.</div>';
  }
}

function renderNetwork(nodes, edges) {
  const container = document.getElementById('network-container');

  const visNodes = new vis.DataSet(nodes.map(n => ({
    id: n.id,
    label: n.id,
    title: `${n.id}\nStatus: ${n.status}\nRisk Score: ${n.riskScore}`,
    color: { background: nodeColor(n.status), border: nodeBorder(n.status), highlight: { background: '#ffffff', border: '#0C447C' } },
    size: 8 + Math.min(n.riskScore / 4, 20),
    font: { color: '#ffffff', size: 10, face: 'Segoe UI' },
    borderWidth: (n.status === 'FRAUD' || n.status === 'SUSPICIOUS') ? 3 : 1.5,
    shadow: n.status !== 'NORMAL'
  })));

  const visEdges = new vis.DataSet(edges.map((e, i) => ({
    id: i,
    from: e.from,
    to: e.to,
    color: { color: e.type === 'FRAUD' ? 'rgba(226,75,74,0.7)' : 'rgba(100,150,200,0.25)', highlight: '#EF9F27' },
    width: e.type === 'FRAUD' ? 2.5 : 1,
    arrows: { to: { enabled: true, scaleFactor: 0.5 } },
    smooth: { type: 'curvedCW', roundness: 0.1 },
    title: `${e.from} → ${e.to}\n₹${Math.round(e.totalAmount || 0).toLocaleString()} (${e.txnCount} txns)`
  })));

  setText('node-count', nodes.length);
  setText('edge-count', edges.length);

  network = new vis.Network(container, { nodes: visNodes, edges: visEdges }, {
    physics: { enabled: true, stabilization: { iterations: 150 }, barnesHut: { gravitationalConstant: -8000, springLength: 120 } },
    interaction: { hover: true, tooltipDelay: 200, hideEdgesOnDrag: true },
    layout: { improvedLayout: false }
  });

  network.on('click', params => {
    if (params.nodes.length > 0) {
      const nodeId = params.nodes[0];
      showDetailPanel(nodeId, nodes, edges);
    }
  });
}

async function setViewMode(mode) {
  currentViewMode = mode;
  ['month', 'week', 'day'].forEach(m => {
    const btn = document.getElementById('btn-' + m);
    if (btn) btn.classList.toggle('active', m === mode);
  });

  const datePicker = document.getElementById('date-picker');
  const dateLabel = document.getElementById('date-label');
  if (mode === 'month') {
    if (datePicker) datePicker.style.display = 'none';
    if (dateLabel) dateLabel.style.display = 'none';
  } else {
    if (datePicker) datePicker.style.display = 'inline-block';
    if (dateLabel) dateLabel.style.display = 'inline-block';
  }

  await updateGraph();
}

async function onDateChange() {
  if (currentViewMode !== 'month') {
    await updateGraph();
  }
}

function showDetailPanel(nodeId, nodes, edges) {
  const node = nodes.find(n => n.id === nodeId);
  if (!node) return;
  const outEdges = edges.filter(e => e.from === nodeId);
  const inEdges  = edges.filter(e => e.to   === nodeId);
  document.getElementById('dp-title').textContent = nodeId;
  document.getElementById('dp-content').innerHTML = `
    <div class="detail-row"><span class="detail-key">Status</span><span class="detail-value"><span class="badge badge-${statusBadgeClass(node.status)}">${node.status}</span></span></div>
    <div class="detail-row"><span class="detail-key">Risk Score</span><span class="detail-value">${node.riskScore}</span></div>
    <div class="detail-row"><span class="detail-key">Outgoing links</span><span class="detail-value">${outEdges.length}</span></div>
    <div class="detail-row"><span class="detail-key">Incoming links</span><span class="detail-value">${inEdges.length}</span></div>
    <div class="detail-row"><span class="detail-key">Total sent</span><span class="detail-value">₹${Math.round(outEdges.reduce((s,e)=>s+(e.totalAmount||0),0)).toLocaleString()}</span></div>
    <div class="detail-row"><span class="detail-key">Total received</span><span class="detail-value">₹${Math.round(inEdges.reduce((s,e)=>s+(e.totalAmount||0),0)).toLocaleString()}</span></div>`;
  document.getElementById('detail-panel').classList.add('open');
}

function closeDetailPanel() { document.getElementById('detail-panel').classList.remove('open'); }

// ─────────────────────────────────────────────
// ACCOUNTS PAGE (accounts.html)
// ─────────────────────────────────────────────

let allAccountData = [];
let sortKey = 'riskScore', sortDir = -1;

async function loadAccounts() {
  try {
    allAccountData = await fetchJSON(`${API}/accounts`);
    renderTable();
  } catch (e) {
    document.getElementById('table-container').innerHTML = '<div class="empty"><p>Failed to load accounts.</p></div>';
  }
}

function renderTable() {
  const search = (document.getElementById('search-input')?.value || '').toLowerCase();
  const statusF = document.getElementById('status-filter')?.value || '';

  let rows = allAccountData.filter(a =>
    (a.accountId.toLowerCase().includes(search) || a.name.toLowerCase().includes(search)) &&
    (!statusF || a.status === statusF)
  );

  rows.sort((a, b) => {
    const av = a[sortKey], bv = b[sortKey];
    return typeof av === 'number' ? (av - bv) * sortDir : String(av).localeCompare(String(bv)) * sortDir;
  });

  setText('row-count', `Showing ${rows.length} of ${allAccountData.length} accounts`);

  if (!rows.length) {
    document.getElementById('table-container').innerHTML = '<div class="empty"><div class="empty-icon">🔍</div><p>No accounts match your filter.</p></div>';
    return;
  }

  document.getElementById('table-container').innerHTML = `
    <table>
      <thead><tr>
        ${thCell('accountId', 'Account ID')}
        ${thCell('name', 'Name')}
        ${thCell('totalTransactions', 'Transactions')}
        ${thCell('riskScore', 'Risk Score')}
        ${thCell('status', 'Status')}
      </tr></thead>
      <tbody>
        ${rows.map(a => `
          <tr>
            <td class="mono">${a.accountId}</td>
            <td>${a.name}</td>
            <td style="text-align:right">${Number(a.totalTransactions).toLocaleString()}</td>
            <td style="text-align:right">
              <div style="display:flex;align-items:center;gap:8px;justify-content:flex-end;">
                <div style="width:60px;height:6px;background:#e8ecf0;border-radius:4px;overflow:hidden;">
                  <div style="width:${Math.min(a.riskScore, 100)}%;height:100%;background:${riskColor(a.riskScore)};border-radius:4px;"></div>
                </div>
                <span style="font-weight:700;min-width:30px;text-align:right">${a.riskScore}</span>
              </div>
            </td>
            <td><span class="badge badge-${statusBadgeClass(a.status)}">${a.status}</span></td>
          </tr>`).join('')}
      </tbody>
    </table>`;
}

function thCell(key, label) {
  const active = key === sortKey ? ' sorted' : '';
  const icon = key === sortKey ? (sortDir === -1 ? '↓' : '↑') : '↕';
  return `<th class="${active}" onclick="sortBy('${key}')">${label} <span class="sort-icon">${icon}</span></th>`;
}

function sortBy(key) {
  if (sortKey === key) sortDir *= -1; else { sortKey = key; sortDir = -1; }
  renderTable();
}

function filterAccounts() { renderTable(); }

// ─────────────────────────────────────────────
// SIMULATOR PAGE (simulate.html)
// ─────────────────────────────────────────────

async function loadSimulator() {
  try {
    const accounts = await fetchJSON(`${API}/accounts`);
    const opts = accounts.map(a => `<option value="${a.accountId}">${a.accountId} — ${a.name}</option>`).join('');
    document.getElementById('sim-from').innerHTML = '<option value="">Select account…</option>' + opts;
    document.getElementById('sim-to').innerHTML   = '<option value="">Select account…</option>' + opts;
  } catch (e) { console.error('Sim load error', e); }
}

async function submitSimulation(event) {
  event.preventDefault();
  const from   = document.getElementById('sim-from').value;
  const to     = document.getElementById('sim-to').value;
  const amount = parseFloat(document.getElementById('sim-amount').value);

  if (!from || !to || !amount) { alert('Please fill in all fields.'); return; }
  if (from === to) { alert('From and To accounts must be different.'); return; }

  const btn = document.getElementById('sim-btn');
  btn.disabled = true;
  btn.textContent = 'Running…';

  try {
    const result = await fetchJSON(`${API}/transactions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ fromAccount: from, toAccount: to, amount })
    });

    showSimResult(result);
  } catch (e) {
    alert('Simulation failed: ' + e.message);
  } finally {
    btn.disabled = false;
    btn.textContent = 'Run Simulation';
  }
}

function showSimResult(result) {
  const panel = document.getElementById('result-panel');
  const empty = document.getElementById('sim-empty');
  if (empty) empty.style.display = 'none';

  const isFraud = result.newAlerts && result.newAlerts.length > 0;
  panel.className = 'result-panel card ' + (isFraud ? 'fraud' : 'clean');
  panel.style.display = 'block';

  setText('result-title', isFraud ? '⚠ Fraud Pattern Detected!' : '✅ Transaction Is Clean');
  setText('result-message', result.message);

  // New alerts
  const alertsDiv = document.getElementById('result-alerts');
  if (result.newAlerts && result.newAlerts.length > 0) {
    alertsDiv.innerHTML = '<div class="card-title" style="margin-bottom:8px;">New Alerts Triggered</div>' +
      result.newAlerts.map(a => `
        <div class="alert-item ${a.type}" style="margin-bottom:6px;">
          <div class="alert-icon">${alertIcon(a.type)}</div>
          <div class="alert-body">
            <div class="alert-type">${a.type.replace('_',' ')}</div>
            <div class="alert-desc">${a.description}</div>
            <div class="alert-accts">${(a.involvedAccounts || []).slice(0, 10).join(', ')}</div>
          </div>
        </div>`).join('');
  } else {
    alertsDiv.innerHTML = '';
  }

  // Propagation risk scores (pure CSS bar chart — no library)
  const scores = result.propagationScores || {};
  const top20 = Object.entries(scores)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 20);

  const propSection = document.getElementById('propagation-section');
  const propBars    = document.getElementById('propagation-bars');

  if (top20.length > 0) {
    propSection.style.display = 'block';
    propBars.innerHTML = top20.map(([id, score]) => {
      const fillClass = score >= 75 ? 'high' : score >= 40 ? 'medium' : score >= 10 ? 'low' : 'safe';
      return `
        <div class="bar-row">
          <div class="bar-label">
            <span class="acct-id">${id}</span>
            <span class="score" style="color:${riskColor(score)}">${score.toFixed(1)}</span>
          </div>
          <div class="bar-track">
            <div class="bar-fill ${fillClass}" style="width:${score}%"></div>
          </div>
        </div>`;
    }).join('');
  } else {
    propSection.style.display = 'none';
  }
}

// ─────────────────────────────────────────────
// SHARED HELPERS
// ─────────────────────────────────────────────

async function fetchJSON(url, opts) {
  const res = await fetch(url, opts);
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${res.statusText}`);
  return res.json();
}

function setText(id, text) {
  const el = document.getElementById(id);
  if (el) el.textContent = text;
}

function fmtDate(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  return d.toLocaleDateString('en-IN', { month: 'short', day: 'numeric', year: '2-digit' });
}

/** Maps status labels to node background colors. */
function nodeColor(status) {
  return {
    FRAUD:      '#E24B4A',
    SUSPICIOUS: '#EF9F27',
    AT_RISK:    '#a1855c',
    NORMAL:     '#4e9af1'
  }[status] || '#4e9af1';
}
function nodeBorder(status) {
  return {
    FRAUD:      '#b82b2a',
    SUSPICIOUS: '#c07b10',
    AT_RISK:    '#7a5c35',
    NORMAL:     '#2e6fc7'
  }[status] || '#2e6fc7';
}

/** Maps numeric risk score to a color. */
function riskColor(score) {
  if (score >= 80) return '#E24B4A';
  if (score >= 40) return '#EF9F27';
  if (score >= 15) return '#a1855c';
  return '#639922';
}

/** Maps status string to CSS badge class suffix. */
function statusBadgeClass(status) {
  return (status || 'normal').toLowerCase().replace('_', '-');
}

function alertIcon(type) {
  return { CYCLE: '🔄', HUB: '🎯', RAPID_HOP: '⚡', THRESHOLD: '⚖️' }[type] || '⚠';
}
