<template>
  <section class="boundary-panel-wrapper">

    <!-- ═══ LOADING STATE (auto-running after discovery) ═══ -->
    <section v-if="loadingExternal || loadingLlm" class="boundary-empty">
      <div class="boundary-empty-card">
        <div class="boundary-loading-spinner"></div>
        <p class="eyebrow">{{ loadingExternal ? 'Detecting boundary nodes…' : 'Generating AI recommendations…' }}</p>
        <h2 v-if="loadingExternal">Scanning cross-subsystem edges</h2>
        <h2 v-else>Analysing results with LLM</h2>
        <p style="font-size:12px;color:#94a3b8;margin-top:8px;">
          This runs automatically after subsystem discovery completes.
        </p>
      </div>
    </section>

    <!-- ═══ EMPTY STATE (no discovery yet) ═══ -->
    <section v-else-if="!boundaryResult" class="boundary-empty">
      <div class="boundary-empty-card">
        <div class="boundary-empty-icon">⬡</div>
        <p class="eyebrow">Waiting for discovery</p>
        <h2>Run subsystem discovery to populate this view</h2>
        <p>Once the Leiden algorithm completes, boundary nodes and AI improvement<br/>
          recommendations will appear here automatically.</p>
        <div class="boundary-hint-arrow">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#94a3b8" stroke-width="1.8">
            <path d="M19 12H5M12 5l-7 7 7 7"/>
          </svg>
          Switch to <strong>Subsystem Discovery</strong> tab and run discovery first
        </div>
      </div>
    </section>

    <!-- ═══ RESULTS ═══ -->
    <template v-else>

      <!-- Info bar -->
      <div class="boundary-info-bar">
        <div class="boundary-info-chips">
          <span class="info-chip chip-run">
            <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>
            </svg>
            Run {{ boundaryResult.discoveryRunId }}
          </span>
          <span class="info-chip chip-subsystems">
            {{ boundaryResult.totalSubsystems }} subsystems
          </span>
          <span class="info-chip chip-nodes">
            {{ fmt(boundaryResult.totalNodes) }} nodes total
          </span>
          <span class="info-chip chip-boundary">
            <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="3"/><path d="M4 12h5M15 12h5M12 4v5M12 15v5"/>
            </svg>
            {{ fmt(boundaryResult.boundaryNodeCount) }} boundary nodes
          </span>
          <span class="info-chip chip-ratio">
            {{ boundaryRatioPct }}% bridge ratio
          </span>
        </div>
      </div>

      <!-- Metrics row (matching MetricsGrid symmetry) -->
      <section class="metrics-grid boundary-metrics">
        <article>
          <span>Subsystems</span>
          <strong>{{ boundaryResult.totalSubsystems }}</strong>
        </article>
        <article>
          <span>Total nodes</span>
          <strong>{{ fmt(boundaryResult.totalNodes) }}</strong>
        </article>
        <article>
          <span>Boundary nodes</span>
          <strong class="accent-orange">{{ fmt(boundaryResult.boundaryNodeCount) }}</strong>
        </article>
        <article>
          <div class="metric-label">
            <span>Bridge ratio</span>
            <span class="tooltip-container tooltip-align-right-down">
              <i class="tooltip-icon">ⓘ</i>
              <span class="tooltip-box">
                <strong>Bridge Ratio</strong><br/><br/>
                Percentage of all nodes that cross at least one subsystem boundary.<br/><br/>
                Interpretation:<br/>
                • &lt; 5% = Well-isolated subsystems<br/>
                • 5–15% = Moderate coupling<br/>
                • &gt; 15% = High coupling, refactoring recommended
              </span>
            </span>
          </div>
          <strong :class="ratioBadgeClass">{{ boundaryRatioPct }}%</strong>
        </article>
      </section>

      <!-- Section header + filter -->
      <div class="workspace-section-header boundary-table-header">
        <div>
          <h2>All boundary nodes <span class="badge-count">{{ filtered.length }}</span></h2>
          <p class="boundary-sub">Sorted by boundary score — higher means predominantly cross-subsystem</p>
        </div>
        <div class="boundary-filters">
          <input
            id="boundary-filter-input"
            v-model="filterText"
            type="text"
            class="boundary-input boundary-search"
            placeholder="Filter by name, type, subsystem…"
          />
          <select id="boundary-filter-type" v-model="filterType" class="boundary-select">
            <option value="">All types</option>
            <option value="CLASS">CLASS</option>
            <option value="METHOD">METHOD</option>
            <option value="PACKAGE">PACKAGE</option>
          </select>
        </div>
      </div>

      <!-- 2-col layout: node list + detail panel -->
      <div class="boundary-content-grid">

        <!-- Left: node list -->
        <div class="boundary-list-col">
          <div
            v-for="node in paginatedNodes"
            :key="node.nodeId"
            class="boundary-node-card"
            :class="{ selected: selectedNode?.nodeId === node.nodeId }"
            @click="selectedNode = node"
          >
            <div class="bnode-top">
              <div class="bnode-name-row">
                <span class="bnode-type-badge" :class="typeClass(node.nodeType)">{{ node.nodeType || '?' }}</span>
                <span class="bnode-role-badge" v-if="node.boundaryRole">{{ node.boundaryRole }}</span>
                <span class="bnode-name" :title="node.qualifiedName">{{ shortName(node.name) }}</span>
              </div>
              <div class="bnode-score-col">
                <div class="bnode-score-bar-wrap">
                  <div class="bnode-score-bar"
                    :style="{ width: (node.boundaryScore * 100) + '%', background: scoreColor(node.boundaryScore) }">
                  </div>
                </div>
                <span class="bnode-score-value" :style="{ color: scoreColor(node.boundaryScore) }">{{ pct(node.boundaryScore) }}%</span>
              </div>
            </div>
            <div class="bnode-meta">
              <span class="bnode-home">
                <svg viewBox="0 0 24 24" width="11" height="11" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M3 12 L12 3 L21 12 V20 H15 V14 H9 V20 H3 Z"/>
                </svg>
                {{ node.homeSubsystemName || node.homeSubsystemId }}
              </span>
              <span class="bnode-cross-edges">
                {{ node.crossSubsystemEdges }}/{{ node.totalDegree }} cross-edges
                <span class="bnode-risk-badge" :class="'risk-' + (node.riskLevel || 'LOW').toLowerCase()">{{ node.riskLevel || 'LOW' }}</span>
              </span>
            </div>
          </div>

          <!-- Pagination -->
          <div v-if="totalPages > 1" class="boundary-pagination">
            <button type="button" class="page-btn" :disabled="currentPage === 1" @click="currentPage--">‹</button>
            <span class="page-info">{{ currentPage }} / {{ totalPages }}</span>
            <button type="button" class="page-btn" :disabled="currentPage === totalPages" @click="currentPage++">›</button>
          </div>
        </div>

        <!-- Right: detail panel -->
        <div class="boundary-detail-col">
          <div v-if="!selectedNode" class="boundary-detail-empty">
            <p>← Select a node to see details</p>
          </div>
          <template v-else>
            <div class="boundary-detail-card">
              <div class="bdetail-header">
                <span class="bnode-type-badge" :class="typeClass(selectedNode.nodeType)">{{ selectedNode.nodeType }}</span>
                <div>
                  <p class="eyebrow">{{ selectedNode.packageName || 'unknown package' }}</p>
                  <h2 class="bdetail-name">{{ selectedNode.name }}</h2>
                  <p class="bdetail-qualified">{{ selectedNode.qualifiedName }}</p>
                </div>
              </div>

              <!-- Score gauge -->
              <div class="bdetail-score-block">
                <div class="bdetail-score-label">
                  <span>Boundary Score</span>
                  <strong :style="{ color: scoreColor(selectedNode.boundaryScore) }">{{ pct(selectedNode.boundaryScore) }}%</strong>
                </div>
                <div class="bdetail-score-track">
                  <div class="bdetail-score-fill"
                    :style="{ width: (selectedNode.boundaryScore * 100) + '%', background: scoreColor(selectedNode.boundaryScore) }">
                  </div>
                </div>
                <div class="bdetail-score-hints">
                  <span>Internal</span><span>Bridge</span>
                </div>
              </div>

              <!-- Edge summary -->
              <div class="bdetail-edge-summary">
                <div class="bdetail-stat">
                  <span>Total degree</span><strong>{{ selectedNode.totalDegree }}</strong>
                </div>
                <div class="bdetail-stat">
                  <span>Cross-subsystem</span>
                  <strong :style="{ color: scoreColor(selectedNode.boundaryScore) }">{{ selectedNode.crossSubsystemEdges }}</strong>
                </div>
                <div class="bdetail-stat">
                  <span>Home subsystem</span>
                  <strong>{{ selectedNode.homeSubsystemName || selectedNode.homeSubsystemId }}</strong>
                </div>
                <div class="bdetail-stat">
                  <span>Role</span>
                  <strong>{{ selectedNode.boundaryRole || 'Unknown' }}</strong>
                </div>
                <div class="bdetail-stat" :class="'risk-' + (selectedNode.riskLevel || 'LOW').toLowerCase()">
                  <span>Risk Level</span>
                  <strong>{{ selectedNode.riskLevel || 'LOW' }}</strong>
                </div>
                <div class="bdetail-stat">
                  <span>Importance</span>
                  <strong>{{ pct(selectedNode.importanceScore) }}%</strong>
                </div>
              </div>

              <!-- Cross-subsystem bridges -->
              <div class="bdetail-bridges-title">
                <p class="eyebrow">Bridges into</p>
              </div>
              <ul class="bdetail-bridges">
                <li v-for="link in selectedNode.crossSubsystemLinks" :key="link.subsystemId">
                  <div class="bridge-name">{{ link.subsystemName || link.subsystemId }}</div>
                  <div class="bridge-bar-wrap">
                    <div class="bridge-bar" :style="{ width: bridgePct(link, selectedNode) + '%' }"></div>
                  </div>
                  <span class="bridge-count">{{ link.crossEdgeCount }} edges</span>
                </li>
              </ul>

              <!-- Usage hint -->
              <div class="bdetail-hint">
                <div class="bdetail-hint-icon">💡</div>
                <p>
                  <strong>Action:</strong> {{ selectedNode.recommendedAction || 'Monitor during microservice extraction — occasional cross-calls may be acceptable.' }}
                </p>
              </div>
            </div>
          </template>
        </div>
      </div>

      <!-- LLM Improvement Summary (auto-generated, read-only) -->
      <div class="boundary-summary-section">
        <div class="workspace-section-header">
          <div>
            <p class="eyebrow">AI Analysis · Auto-generated</p>
            <h2>Subsystem Improvement Recommendations</h2>
          </div>
          <div class="header-actions" style="display:flex;align-items:center;gap:8px;">
            <span v-if="llmModelUsed" class="diagram-badge">{{ llmModelUsed }}</span>
            <button
              type="button"
              class="collapse-toggle-btn icon-only"
              @click="isSummaryCollapsed = !isSummaryCollapsed"
            >
              <svg v-if="isSummaryCollapsed" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M6 9l6 6 6-6" />
              </svg>
              <svg v-else viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M18 15l-6-6-6 6" />
              </svg>
            </button>
          </div>
        </div>

        <article class="summary-panel full-width-summary" :class="{ collapsed: isSummaryCollapsed }">
          <div class="summary-body">
            <!-- LLM loading -->
            <div v-if="loadingLlm" class="boundary-llm-loading">
              <div class="boundary-loading-spinner small"></div>
              <span>Generating improvement recommendations…</span>
            </div>
            <!-- LLM result -->
            <div v-else-if="formattedSummaryHtml" class="summary-container boundary-llm-result" v-html="formattedSummaryHtml"></div>
            <!-- No summary yet -->
            <p v-else style="font-size:13px;color:#94a3b8;">
              LLM recommendations will appear here automatically after boundary analysis completes.
            </p>
          </div>
        </article>
      </div>

    </template>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  externalResult:      { type: Object,  default: null  },
  loadingExternal:     { type: Boolean, default: false },
  boundarySummaryHtml: { type: String,  default: ''    },
  loadingLlm:          { type: Boolean, default: false },
  llmModelUsed:        { type: String,  default: ''    }
})

// ── Local display state only ──────────────────────────────────────────────────
const boundaryResult      = ref(null)
const selectedNode        = ref(null)
const filterText          = ref('')
const filterType          = ref('')
const currentPage         = ref(1)
const isSummaryCollapsed  = ref(false)

const PAGE_SIZE = 12

const formattedSummaryHtml = computed(() => {
  return markdownToHtml(props.boundarySummaryHtml)
})

function markdownToHtml(md) {
  if (!md) return ''
  return md
    .replace(/^### (.+)$/gm, '<h3>$1</h3>')
    .replace(/^## (.+)$/gm, '<h2>$1</h2>')
    .replace(/^# (.+)$/gm, '<h1>$1</h1>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/^[-•] (.+)$/gm, '<li>$1</li>')
    .replace(/(<li>.*<\/li>\n?)+/g, '<ul>$&</ul>')
    .replace(/\n{2,}/g, '</p><p>')
    .replace(/^(?!<[hul])(.+)$/gm, '<p>$1</p>')
    .replace(/<p><\/p>/g, '')
}

// Sync parent result into local display state
watch(() => props.externalResult, (newResult) => {
  if (newResult) {
    boundaryResult.value = newResult
    filterText.value = ''
    filterType.value = ''
    currentPage.value = 1
    selectedNode.value = newResult.boundaryNodes?.length ? newResult.boundaryNodes[0] : null
  } else {
    boundaryResult.value = null
    selectedNode.value = null
  }
}, { immediate: true })

// ── Computed ──────────────────────────────────────────────────────────────────
const filtered = computed(() => {
  const q = filterText.value.toLowerCase()
  const t = filterType.value
  return (boundaryResult.value?.boundaryNodes || []).filter(n => {
    const matchText = !q ||
      (n.name || '').toLowerCase().includes(q) ||
      (n.qualifiedName || '').toLowerCase().includes(q) ||
      (n.homeSubsystemName || '').toLowerCase().includes(q) ||
      (n.homeSubsystemId || '').toLowerCase().includes(q)
    const matchType = !t || n.nodeType === t
    return matchText && matchType
  })
})

watch([filterText, filterType], () => { currentPage.value = 1 })
watch(filtered, () => {
  if (!selectedNode.value) return
  const still = filtered.value.find(n => n.nodeId === selectedNode.value.nodeId)
  if (!still) selectedNode.value = null
})

const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / PAGE_SIZE)))
const paginatedNodes = computed(() => {
  const start = (currentPage.value - 1) * PAGE_SIZE
  return filtered.value.slice(start, start + PAGE_SIZE)
})

const boundaryRatioPct = computed(() => {
  if (!boundaryResult.value?.totalNodes) return '0'
  return ((boundaryResult.value.boundaryNodeCount / boundaryResult.value.totalNodes) * 100).toFixed(1)
})

const ratioBadgeClass = computed(() => {
  const r = parseFloat(boundaryRatioPct.value)
  if (r > 15) return 'accent-red'
  if (r > 5)  return 'accent-orange'
  return 'accent-green'
})

// ── Helpers ───────────────────────────────────────────────────────────────────
function fmt(v) {
  if (v === null || v === undefined) return '-'
  return new Intl.NumberFormat('en-US').format(v)
}
function pct(score) { return Math.round((score || 0) * 100) }
function shortName(name) {
  if (!name) return '?'
  const parts = name.split('.')
  return parts.slice(-2).join('.')
}
function scoreColor(score) {
  if (score >= 0.7) return '#dc2626'
  if (score >= 0.4) return '#d97706'
  return '#2563eb'
}
function typeClass(type) {
  if (type === 'CLASS')   return 'type-class'
  if (type === 'METHOD')  return 'type-method'
  if (type === 'PACKAGE') return 'type-package'
  return 'type-unknown'
}
function bridgePct(link, node) {
  const max = Math.max(...(node.crossSubsystemLinks || []).map(l => l.crossEdgeCount), 1)
  return Math.round((link.crossEdgeCount / max) * 100)
}
function hintText(node) {
  if (node.boundaryScore >= 0.7) return 'Consider extracting into a dedicated API layer or shared service to cleanly separate concerns.'
  if (node.boundaryScore >= 0.4) return 'Review whether cross-subsystem calls can be reduced via events or a mediator pattern.'
  return 'Monitor during microservice extraction — occasional cross-calls may be acceptable.'
}
</script>

<style scoped>
.boundary-panel-wrapper { display: flex; flex-direction: column; gap: 0; }

/* ── Info bar ── */
.boundary-info-bar {
  padding: 10px 24px;
  background: #ffffff;
  border-bottom: 1px solid #e2e8f0;
}
.boundary-info-chips { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.info-chip {
  display: inline-flex; align-items: center; gap: 5px;
  font-size: 12px; font-weight: 600;
  padding: 3px 10px; border-radius: 999px;
}
.chip-run      { background: #eff6ff; color: #1d4ed8; border: 1px solid #bfdbfe; }
.chip-subsystems { background: #f0fdf4; color: #166534; border: 1px solid #bbf7d0; }
.chip-nodes    { background: #f8fafc; color: #475569; border: 1px solid #e2e8f0; }
.chip-boundary { background: #fff7ed; color: #c2410c; border: 1px solid #fed7aa; }
.chip-ratio    { background: #fdf4ff; color: #7e22ce; border: 1px solid #e9d5ff; }

/* ── Empty / loading state ── */
.boundary-empty {
  display: flex; align-items: center; justify-content: center;
  padding: 80px 24px;
}
.boundary-empty-card { text-align: center; max-width: 480px; }
.boundary-empty-icon { font-size: 48px; margin-bottom: 16px; opacity: 0.2; }
.boundary-empty-card h2 { font-size: 17px; margin: 6px 0 10px; color: #1e293b; }
.boundary-empty-card p  { font-size: 13px; color: #64748b; line-height: 1.6; }
.boundary-hint-arrow {
  display: inline-flex; align-items: center; gap: 8px;
  margin-top: 20px; padding: 10px 16px;
  background: #f8fafc; border: 1px dashed #cbd5e1; border-radius: 8px;
  font-size: 12.5px; color: #64748b;
}

/* spinner */
.boundary-loading-spinner {
  width: 40px; height: 40px;
  border: 3px solid #e2e8f0; border-top-color: #2563eb;
  border-radius: 50%; animation: spin 0.8s linear infinite;
  margin: 0 auto 16px;
}
.boundary-loading-spinner.small {
  width: 18px; height: 18px; border-width: 2px; margin: 0;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ── Metrics ── */
.boundary-metrics { margin: 16px 24px 0; }
.accent-orange { color: #d97706; }
.accent-red    { color: #dc2626; }
.accent-green  { color: #16a34a; }

/* ── Table header + filters ── */
.boundary-table-header { padding: 16px 24px 12px; flex-wrap: wrap; gap: 12px; }
.boundary-sub { font-size: 12px; color: #94a3b8; margin-top: 2px; }
.badge-count {
  display: inline-flex; align-items: center;
  background: #eff6ff; color: #2563eb;
  border-radius: 999px; font-size: 11px; font-weight: 700;
  padding: 1px 8px; margin-left: 6px; vertical-align: middle;
}
.boundary-filters { display: flex; gap: 8px; flex-wrap: wrap; }
.boundary-input {
  height: 36px; padding: 0 10px;
  border: 1px solid #cbd5e1; border-radius: 6px;
  font-size: 13px; color: #1e293b; background: #f8fafc;
  transition: border-color 0.15s;
}
.boundary-input:focus { outline: none; border-color: #2563eb; background: #fff; }
.boundary-search { width: 210px; }
.boundary-select {
  height: 36px; padding: 0 8px;
  border: 1px solid #cbd5e1; border-radius: 6px;
  font-size: 13px; color: #1e293b; background: #f8fafc; cursor: pointer;
}

/* ── 2-col content ── */
.boundary-content-grid {
  display: grid; grid-template-columns: 420px 1fr;
  gap: 0; margin: 0 24px 0;
  min-height: 480px;
  border: 1px solid #e2e8f0; border-radius: 10px;
  overflow: hidden; background: #fff;
}

/* ── Node list col ── */
.boundary-list-col {
  border-right: 1px solid #e2e8f0; overflow-y: auto;
  max-height: 580px; padding: 12px;
  display: flex; flex-direction: column; gap: 6px; background: #f8fafc;
}
.boundary-node-card {
  padding: 10px 12px;
  border: 1px solid #e2e8f0; border-radius: 8px; background: #ffffff;
  cursor: pointer; transition: border-color 0.15s, box-shadow 0.15s;
}
.boundary-node-card:hover { border-color: #93c5fd; box-shadow: 0 2px 8px rgba(37,99,235,0.08); }
.boundary-node-card.selected { border-color: #2563eb; box-shadow: 0 0 0 2px rgba(37,99,235,0.15); }
.bnode-top { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.bnode-name-row { display: flex; align-items: center; gap: 7px; flex: 1; min-width: 0; }
.bnode-name { font-size: 12.5px; font-weight: 600; color: #0f172a; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.bnode-score-col { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
.bnode-score-bar-wrap { width: 56px; height: 6px; background: #f1f5f9; border-radius: 999px; overflow: hidden; }
.bnode-score-bar { height: 100%; border-radius: 999px; transition: width 0.4s ease; }
.bnode-score-value { font-size: 11px; font-weight: 700; width: 28px; text-align: right; }
.bnode-meta { display: flex; align-items: center; gap: 12px; font-size: 11px; color: #94a3b8; }
.bnode-home { display: flex; align-items: center; gap: 3px; }
.bnode-cross-edges { margin-left: auto; }

/* type badges */
.bnode-type-badge {
  display: inline-block; font-size: 9.5px; font-weight: 800;
  letter-spacing: 0.05em; text-transform: uppercase;
  padding: 1px 6px; border-radius: 4px; flex-shrink: 0;
}
.type-class   { background: #eff6ff; color: #1d4ed8; }
.type-method  { background: #f3e8ff; color: #7c3aed; }
.type-package { background: #fffbeb; color: #b45309; }
.type-unknown { background: #f1f5f9; color: #475569; }

/* Pagination */
.boundary-pagination {
  display: flex; align-items: center; justify-content: center;
  gap: 10px; padding: 10px 0 4px; margin-top: auto;
}
.page-btn {
  width: 28px; height: 28px;
  border: 1px solid #e2e8f0; border-radius: 6px;
  background: #fff; color: #475569; font-size: 15px;
  display: flex; align-items: center; justify-content: center;
  transition: background 0.15s;
}
.page-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.page-btn:not(:disabled):hover { background: #eff6ff; border-color: #93c5fd; }
.page-info { font-size: 12px; color: #64748b; }

/* ── Detail col ── */
.boundary-detail-col {
  overflow-y: auto; max-height: 580px;
  padding: 20px 22px; background: #fff;
}
.boundary-detail-empty {
  display: flex; align-items: center; justify-content: center;
  height: 100%; color: #94a3b8; font-size: 13px;
}
.boundary-detail-card { display: flex; flex-direction: column; gap: 18px; }
.bdetail-header { display: flex; align-items: flex-start; gap: 12px; }
.bdetail-header .bnode-type-badge { margin-top: 3px; font-size: 10.5px; }
.bdetail-name { font-size: 15px; color: #0f172a; font-weight: 700; margin-top: 2px; }
.bdetail-qualified { font-size: 11px; color: #94a3b8; margin-top: 3px; word-break: break-all; }

.bdetail-score-block { background: #f8fafc; border-radius: 8px; padding: 14px 16px; }
.bdetail-score-label {
  display: flex; justify-content: space-between;
  font-size: 12px; color: #64748b; margin-bottom: 8px;
}
.bdetail-score-label strong { font-size: 16px; }
.bdetail-score-track { height: 10px; background: #e2e8f0; border-radius: 999px; overflow: hidden; margin-bottom: 4px; }
.bdetail-score-fill { height: 100%; border-radius: 999px; transition: width 0.4s ease; }
.bdetail-score-hints { display: flex; justify-content: space-between; font-size: 10px; color: #94a3b8; }

.bdetail-edge-summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.bdetail-stat {
  background: #f8fafc; border-radius: 8px; padding: 10px 12px;
  display: flex; flex-direction: column; gap: 4px;
}
.bdetail-stat span { font-size: 11px; color: #94a3b8; }
.bdetail-stat strong { font-size: 14px; color: #0f172a; }

.bdetail-bridges-title { margin-bottom: -4px; }
.bdetail-bridges { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 8px; }
.bdetail-bridges li {
  display: grid; grid-template-columns: 1fr 80px 52px;
  align-items: center; gap: 10px; font-size: 12px;
}
.bridge-name { color: #1e293b; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.bridge-bar-wrap { height: 6px; background: #f1f5f9; border-radius: 999px; overflow: hidden; }
.bridge-bar { height: 100%; background: #3b82f6; border-radius: 999px; transition: width 0.4s; }
.bridge-count { font-size: 11px; color: #94a3b8; text-align: right; }

.bdetail-hint {
  background: linear-gradient(135deg, #eff6ff, #f0fdf4);
  border: 1px solid #bfdbfe; border-radius: 8px;
  padding: 12px 14px; display: flex; gap: 10px; align-items: flex-start;
}
.bdetail-hint-icon { font-size: 16px; flex-shrink: 0; margin-top: 1px; }
.bdetail-hint p { font-size: 12px; color: #1e293b; line-height: 1.55; margin: 0; }

/* ── LLM Summary section ── */
.boundary-summary-section { margin: 20px 0 0; }
.boundary-summary-section .workspace-section-header { padding: 14px 24px 12px; }
.boundary-llm-loading { display: flex; align-items: center; gap: 10px; color: #64748b; font-size: 13px; padding: 20px 0; }
.boundary-llm-result { font-size: 13.5px; line-height: 1.7; color: #1e293b; }

/* ── Role & Risk badges ── */
.bnode-role-badge {
  display: inline-block;
  font-size: 9.5px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 4px;
  background: #f1f5f9;
  color: #475569;
  border: 1px solid #e2e8f0;
  flex-shrink: 0;
}
.bnode-risk-badge {
  display: inline-block;
  font-size: 9px;
  font-weight: 800;
  padding: 0px 4px;
  border-radius: 4px;
  text-transform: uppercase;
  margin-left: 6px;
  vertical-align: middle;
}
.bnode-risk-badge.risk-high { background: #fef2f2; color: #dc2626; border: 1px solid #fecaca; }
.bnode-risk-badge.risk-medium { background: #fffbeb; color: #d97706; border: 1px solid #fde68a; }
.bnode-risk-badge.risk-low { background: #f0fdf4; color: #16a34a; border: 1px solid #bbf7d0; }

.bdetail-stat.risk-high strong { color: #dc2626; }
.bdetail-stat.risk-medium strong { color: #d97706; }
.bdetail-stat.risk-low strong { color: #16a34a; }
</style>
