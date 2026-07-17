<template>
  <div class="workbench-shell">
    <!-- Animated Pop-up Toasts -->
    <Transition name="toast">
      <div v-if="successMessage" class="toast success-toast">
        <span class="toast-icon">✓</span>
        <span class="toast-message">{{ successMessage }}</span>
        <button type="button" class="toast-close" @click="successMessage = ''">×</button>
      </div>
    </Transition>
    <Transition name="toast">
      <div v-if="errorMessage" class="toast error-toast">
        <span class="toast-icon">⚠</span>
        <span class="toast-message">{{ errorMessage }}</span>
        <button type="button" class="toast-close" @click="errorMessage = ''">×</button>
      </div>
    </Transition>

    <AppHeader
      :analysisTime="dataset?.analysisTime"
      :isCollapsed="isLnbCollapsed"
      @toggle-sidebar="isLnbCollapsed = !isLnbCollapsed"
    />

    <div class="layout-grid">
      <AppSidebar
        :isCollapsed="isLnbCollapsed"
        :loading="loading"
        :applications="applications"
        :nodeCounts="nodeCounts"
        :selectedTemplate="selectedTemplate"
        :selectedNodeCount="selectedNodeCount"
        :runs="leiden.runs"
        :consensusThreshold="leiden.consensusThreshold"
        :resolution="leiden.resolution"
        :llmModel="llm.model"
        :summaryType="llm.summaryType"
        :customPrompt="llm.customPrompt"
        :llmModels="llmModels"
        :hasDataset="!!dataset"
        :hasDiscovery="!!discovery"
        @update:selectedTemplate="selectedTemplate = $event"
        @update:selectedNodeCount="selectedNodeCount = $event"
        @update:runs="leiden.runs = $event"
        @update:consensusThreshold="leiden.consensusThreshold = $event"
        @update:resolution="leiden.resolution = $event"
        @update:llmModel="llm.model = $event"
        @update:summaryType="llm.summaryType = $event"
        @update:customPrompt="llm.customPrompt = $event"
        @generate="generateDataset"
        @discover="runDiscovery"
        @summary="generateSummary"
      />

      <main class="main-panel">
        <StatusBar
          v-show="!isTopPanelCollapsed || !discovery"
          :currentApplicationLabel="currentApplicationLabel"
          :nodeCount="dataset ? dataset.nodeCount : selectedNodeCount"
          :relationCount="dataset ? dataset.relationCount : '-'"
          :effectiveRuns="discovery?.algorithm?.runs || '-'"
          :isBusy="isBusy"
          :statusText="statusText"
        />

        <section v-if="!discovery" class="empty-panel">
          <div class="empty-card">
            <p class="eyebrow">Ready for POC</p>
            <h2>Generate a graph, then discover subsystem boundaries.</h2>
            <p>
              The demo creates application-specific dummy relations, runs Leiden discovery,
              and turns the result into a Graph Intelligence view.
            </p>
          </div>
        </section>

        <template v-else>
          <MetricsGrid
            v-show="!isTopPanelCollapsed"
            :totalNodes="discovery.summary.totalNodes"
            :totalEdges="discovery.summary.totalEdges"
            :subsystemCount="discovery.summary.subsystemCount"
            :averageStability="discovery.summary.averageStability"
          />

          <!-- Top Header Info Collapse Toggle Bar -->
          <div 
            class="top-collapse-bar" 
            @click="isTopPanelCollapsed = !isTopPanelCollapsed"
            :title="isTopPanelCollapsed ? 'Expand header info' : 'Collapse header info'"
          >
            <span class="collapse-icon">
              {{ isTopPanelCollapsed ? '▼' : '▲' }}
            </span>
            <span class="collapse-text">
              {{ isTopPanelCollapsed ? 'Expand Header Info' : 'Collapse Header Info' }}
            </span>
          </div>

          <!-- Tab Navigation for Discovery vs Boundary Analysis -->
          <div class="view-tabs" style="display: flex; gap: 8px; border-bottom: 2px solid #cbd5e1; margin-bottom: 20px; margin-top: 16px;">
            <button 
              type="button" 
              class="view-tab-btn" 
              :class="{ active: currentTab === 'discovery' }"
              @click="currentTab = 'discovery'"
              style="padding: 10px 20px; border: none; background: none; font-weight: 700; cursor: pointer; border-bottom: 3px solid transparent; margin-bottom: -2px; font-size: 13px; letter-spacing: 0.03em; text-transform: uppercase; color: #64748b; transition: all 0.2s;"
            >
              📊 Subsystem Graph
            </button>
            <button 
              type="button" 
              class="view-tab-btn" 
              :class="{ active: currentTab === 'boundary' }"
              @click="currentTab = 'boundary'"
              style="padding: 10px 20px; border: none; background: none; font-weight: 700; cursor: pointer; border-bottom: 3px solid transparent; margin-bottom: -2px; font-size: 13px; letter-spacing: 0.03em; text-transform: uppercase; color: #64748b; transition: all 0.2s;"
            >
              ⚡ Boundary Node Detection
            </button>
          </div>

          <div v-show="currentTab === 'discovery'" style="flex: 1; display: flex; flex-direction: column; overflow: hidden; min-height: 0;">
            <div class="workspace-section-header">
              <h2>Cluster Tree Subsystems and associated nodes graph</h2>
              <button
                type="button"
                class="collapse-toggle-btn icon-only"
                @click="isDiscoveryCollapsed = !isDiscoveryCollapsed"
                :title="isDiscoveryCollapsed ? 'Expand Viewport' : 'Collapse Viewport'"
              >
                <svg v-if="isDiscoveryCollapsed" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5">
                  <path d="M6 9l6 6 6-6" />
                </svg>
                <svg v-else viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5">
                  <path d="M18 15l-6-6-6 6" />
                </svg>
              </button>
            </div>

            <section
              class="discovery-grid"
              :class="{
                'collapsed': isDiscoveryCollapsed,
                'summary-collapsed': isSummaryCollapsed,
                'resizing-active': isVerticalResizing
              }"
              :style="{ height: gridHeight + 'px' }"
              ref="resizerContainerRef"
            >
              <ClusterTree
                :sortedSubsystems="sortedSubsystems"
                :selectedClusterId="selectedClusterId"
                :expandedClusters="expandedClusters"
                :selectedMermaidSubsystems="selectedMermaidSubsystems"
                :style="{ width: treeWidth + 'px', flex: '0 0 auto' }"
                @toggle-cluster="toggleCluster"
                @toggle-mermaid-subsystem="toggleMermaidSubsystem"
                @select-all="selectAllMermaidSubsystems"
                @select-none="selectNoneMermaidSubsystems"
              />

              <div class="panel-resizer" :class="{ resizing: isResizing }" @mousedown="onResizerMouseDown">
                <div class="resizer-line"></div>
              </div>

              <DiagramStage
                ref="diagramStageRef"
                :mermaidSvg="mermaidSvg"
                :loadingDiscovery="loading.discovery"
                :sortedSubsystems="sortedSubsystems"
                :selectedCluster="selectedCluster"
                @toggle-cluster="toggleCluster"
              />
            </section>

            <div 
              v-if="!isDiscoveryCollapsed && !isSummaryCollapsed"
              class="vertical-panel-resizer" 
              :class="{ resizing: isVerticalResizing }" 
              @mousedown="onVerticalResizerMouseDown"
            >
              <div class="vertical-resizer-line"></div>
            </div>

            <ArchitectureSummary
              :height="summaryHeight"
              :summaryText="summaryText"
              :formattedSummaryHtml="formattedSummaryHtml"
              :actualModelDisplay="actualModelDisplay"
              :isCollapsed="isSummaryCollapsed"
              :isDiscoveryCollapsed="isDiscoveryCollapsed"
              @toggle-collapse="isSummaryCollapsed = !isSummaryCollapsed"
            />
          </div>

          <div v-show="currentTab === 'boundary'" style="flex: 1; display: flex; flex-direction: column; overflow: hidden; min-height: 0;">
            <BoundaryNodesPanel 
              :boundaryData="boundaryData"
              :discovery="discovery"
              :loading="loadingBoundary"
              :nodeLimit="boundaryNodeLimit"
              :sortOrder="boundarySortOrder"
              :nodeType="boundaryNodeType"
              :fromSubsystem="boundaryFromSubsystem"
              :toSubsystem="boundaryToSubsystem"
              @update:nodeLimit="boundaryNodeLimit = $event"
              @update:sortOrder="boundarySortOrder = $event"
              @update:nodeType="boundaryNodeType = $event"
              @update:fromSubsystem="boundaryFromSubsystem = $event"
              @update:toSubsystem="boundaryToSubsystem = $event"
            />
          </div>
        </template>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch, onMounted, onUnmounted } from 'vue'
import axios from 'axios'
import mermaid from 'mermaid'

// Import Split Vue Components
import AppHeader from './components/AppHeader.vue'
import AppSidebar from './components/AppSidebar.vue'
import StatusBar from './components/StatusBar.vue'
import MetricsGrid from './components/MetricsGrid.vue'
import ClusterTree from './components/ClusterTree.vue'
import DiagramStage from './components/DiagramStage.vue'
import ArchitectureSummary from './components/ArchitectureSummary.vue'
import BoundaryNodesPanel from './components/BoundaryNodesPanel.vue'

const isDiscoveryCollapsed = ref(false)
const isSummaryCollapsed = ref(false)
const isTopPanelCollapsed = ref(false)
const currentTab = ref('discovery')
const boundaryData = ref(null)
const loadingBoundary = ref(false)
const gridHeight = ref(450)
const isVerticalResizing = ref(false)
const windowHeight = ref(window.innerHeight)

const totalAvailableHeight = computed(() => {
  const offset = isTopPanelCollapsed.value ? 230 : 380
  return Math.max(400, windowHeight.value - offset)
})

const summaryHeight = computed(() => {
  if (isDiscoveryCollapsed.value || isSummaryCollapsed.value) {
    return null
  }
  return `${totalAvailableHeight.value - gridHeight.value}px`
})

mermaid.initialize({
  startOnLoad: false,
  theme: 'base',
  securityLevel: 'loose',
  maxTextSize: 10000000,
  maxEdges: 1000,
  flowchart: {
    htmlLabels: true,
    curve: 'basis'
  },
  themeVariables: {
    primaryColor: '#eff6ff',
    primaryTextColor: '#0f172a',
    primaryBorderColor: '#2563eb',
    lineColor: '#64748b',
    fontFamily: 'Inter, Segoe UI, Arial, sans-serif'
  }
})

const SERVER_CONTEXT = `${['code', 'analy', 'zer'].join('')}/server`
const API_BASE = `/${SERVER_CONTEXT}/api/poc`

const applications = [
  { label: 'Amazon', value: 'AMAZON' },
  { label: 'Swiggy', value: 'SWIGGY' },
  { label: 'Blinkit', value: 'BLINKIT' },
  { label: 'Zepto', value: 'ZEPTO' },
  { label: 'Myntra', value: 'MYNTRA' },
  { label: 'MakeMyTrip', value: 'MAKEMYTRIP' }
]

const llmModels = [
  { label: 'google/gemma-3-27b-it', value: 'google/gemma-3-27b-it' },
  { label: 'meta-llama/llama-3.3-70b-instruct', value: 'meta-llama/llama-3.3-70b-instruct' },
  { label: 'deepseek/deepseek-chat', value: 'deepseek/deepseek-chat' },
  { label: 'qwen/qwen3-32b', value: 'qwen/qwen3-32b' },
  { label: 'mistralai/mistral-small', value: 'mistralai/mistral-small' },
  { label: 'gemini-2.5-flash', value: 'gemini-2.5-flash' }
]

const nodeCounts = [100, 1000, 10000, 50000]

const selectedTemplate = ref('AMAZON')
const selectedNodeCount = ref(1000)
const dataset = ref(null)
const discovery = ref(null)
const mermaidSvg = ref('')
const summaryText = ref('')
const formattedSummaryHtml = ref('')
const selectedClusterId = ref(null)
const errorMessage = ref('')
const successMessage = ref('')
const expandedClusters = reactive(new Set())
const selectedMermaidSubsystems = reactive(new Set())
const summaryMeta = reactive({ provider: '', fallback: false, llmModel: '' })

const BOUNDARY_API = `/${SERVER_CONTEXT}/api/codeanalyzer/subsystem/boundary-nodes`

const diagramStageRef = ref(null)

const actualModelDisplay = computed(() => {
  if (!summaryMeta.provider) return ''
  if (summaryMeta.fallback) {
    return `${summaryMeta.llmModel} (Mock fallback)`
  }
  if (summaryMeta.provider === 'MOCK') {
    return 'Mock fallback'
  }
  return summaryMeta.llmModel || summaryMeta.provider
})

const isLnbCollapsed = ref(false)
const treeWidth = ref(380)
const isResizing = ref(false)
const resizerContainerRef = ref(null)

watch(successMessage, (newVal) => {
  if (newVal) {
    setTimeout(() => {
      if (successMessage.value === newVal) {
        successMessage.value = ''
      }
    }, 4000)
  }
})

watch(errorMessage, (newVal) => {
  if (newVal) {
    setTimeout(() => {
      if (errorMessage.value === newVal) {
        errorMessage.value = ''
      }
    }, 5000)
  }
})

const leiden = reactive({
  runs: 10,
  consensusThreshold: 0.7,
  resolution: 1.0
})

const llm = reactive({
  model: 'deepseek/deepseek-chat',
  summaryType: 'MEDIUM_DETAILED',
  customPrompt: ''
})

const loading = reactive({
  dataset: false,
  discovery: false,
  summary: false
})

const isBusy = computed(() => loading.dataset || loading.discovery || loading.summary)

const currentApplicationLabel = computed(() => {
  return applications.find((app) => app.value === selectedTemplate.value)?.label || selectedTemplate.value
})

const sortedSubsystems = computed(() => {
  return [...(discovery.value?.subsystems || [])].sort((a, b) => b.nodeCount - a.nodeCount)
})

const selectedCluster = computed(() => {
  return sortedSubsystems.value.find((cluster) => cluster.id === selectedClusterId.value) || sortedSubsystems.value[0]
})

const statusText = computed(() => {
  if (loading.dataset) return 'Generating graph'
  if (loading.discovery) return 'Running Leiden'
  if (loading.summary) return 'Generating summary'
  if (discovery.value) return 'Discovery ready'
  if (dataset.value) return 'Dataset ready'
  return 'Ready'
})

watch(
  () => [discovery.value, Array.from(expandedClusters).join('|'), Array.from(selectedMermaidSubsystems).join('|')],
  async () => {
    if (discovery.value) {
      await renderMermaid()
    }
  },
  { deep: true }
)

async function generateDataset() {
  loading.dataset = true
  errorMessage.value = ''
  successMessage.value = ''
  discovery.value = null
  dataset.value = null
  mermaidSvg.value = ''
  summaryText.value = ''
  formattedSummaryHtml.value = ''
  summaryMeta.provider = ''
  summaryMeta.fallback = false
  expandedClusters.clear()
  selectedMermaidSubsystems.clear()
  boundaryData.value = null
  currentTab.value = 'discovery'
  diagramStageRef.value?.resetZoom()

  try {
    const response = await axios.post(`${API_BASE}/dataset/generate`, null, {
      params: {
        template: selectedTemplate.value,
        nodeCount: selectedNodeCount.value
      }
    })
    dataset.value = response.data
    successMessage.value = 'Graph generated successfully. Ready for Leiden discovery.'
  } catch (error) {
    handleError(error, 'Failed to generate graph data')
  } finally {
    loading.dataset = false
  }
}

async function runDiscovery() {
  if (!dataset.value) return
  loading.discovery = true
  errorMessage.value = ''
  successMessage.value = ''
  summaryText.value = ''
  formattedSummaryHtml.value = ''
  summaryMeta.provider = ''
  summaryMeta.fallback = false
  expandedClusters.clear()
  selectedMermaidSubsystems.clear()
  boundaryData.value = null
  currentTab.value = 'discovery'
  diagramStageRef.value?.resetZoom()

  try {
    const response = await axios.post(`${API_BASE}/discover`, null, {
      params: discoveryParams()
    })
    discovery.value = response.data
    boundaryFromSubsystem.value = null
    boundaryToSubsystem.value = null
    successMessage.value = `Leiden discovery completed successfully with ${discovery.value.summary.subsystemCount} subsystems.`
    selectedClusterId.value = sortedSubsystems.value[0]?.id || null
    if (selectedClusterId.value) {
      expandedClusters.add(selectedClusterId.value)
    }
    
    // Select all by default for visualization
    if (discovery.value?.subsystems) {
      discovery.value.subsystems.forEach(sub => selectedMermaidSubsystems.add(sub.id))
    }

    // Trigger boundary node detection in background
    fetchBoundaryNodes()

    await nextTick()
    await renderMermaid()
  } catch (error) {
    handleError(error, 'Subsystem discovery failed')
  } finally {
    loading.discovery = false
  }
}

async function generateSummary() {
  if (!dataset.value || !discovery.value) return
  loading.summary = true
  errorMessage.value = ''
  successMessage.value = ''

  try {
    const response = await axios.post(`${API_BASE}/summary`, null, {
      params: {
        ...discoveryParams(),
        llmModel: llm.model,
        summaryType: llm.summaryType,
        customPrompt: llm.customPrompt
      }
    })
    summaryText.value = response.data.summary
    formattedSummaryHtml.value = response.data.formattedSummary
    summaryMeta.provider = response.data.provider
    summaryMeta.fallback = response.data.fallback
    summaryMeta.llmModel = response.data.llmModel
    successMessage.value = `Summary successfully generated for ${currentApplicationLabel.value}.`
  } catch (error) {
    const reason = error.response?.data?.error || error.message || 'unknown error'
    errorMessage.value = `Summary generation failed due to: ${reason}`
  } finally {
    loading.summary = false
  }
}

function discoveryParams() {
  return {
    applicationId: dataset.value.applicationId,
    applicationKey: dataset.value.applicationKey,
    analysisTime: dataset.value.analysisTime,
    runs: leiden.runs,
    consensusThreshold: leiden.consensusThreshold,
    resolution: leiden.resolution
  }
}

function toggleCluster(clusterId) {
  selectedClusterId.value = clusterId
  if (expandedClusters.has(clusterId)) {
    expandedClusters.delete(clusterId)
  } else {
    expandedClusters.add(clusterId)
  }
}

async function renderMermaid() {
  try {
    const { graph, nodeCount, edgeCount } = buildMermaidGraph()
    if (nodeCount > 600 || edgeCount > 1000) {
      mermaidSvg.value = ''
      errorMessage.value = 'Selected subsystems are too large to render smoothly — deselect some subsystems to view the diagram'
      return
    }

    // Clear previous error if successful
    errorMessage.value = ''

    const renderId = `gi-mermaid-${Date.now()}-${Math.round(Math.random() * 10000)}`
    const { svg } = await mermaid.render(renderId, graph)
    mermaidSvg.value = svg
  } catch (error) {
    mermaidSvg.value = ''
    errorMessage.value = `Mermaid rendering failed: ${error.message}`
  }
}

function onResizerMouseDown(e) {
  e.preventDefault()
  isResizing.value = true
  document.addEventListener('mousemove', onResizerMouseMove)
  document.addEventListener('mouseup', onResizerMouseUp)
  document.body.style.cursor = 'col-resize'
  document.body.classList.add('resizing-active')
}

function onResizerMouseMove(e) {
  if (!isResizing.value || !resizerContainerRef.value) return
  const containerRect = resizerContainerRef.value.getBoundingClientRect()
  const relativeX = e.clientX - containerRect.left
  treeWidth.value = Math.max(240, Math.min(600, relativeX))
}

function onResizerMouseUp() {
  isResizing.value = false
  document.removeEventListener('mousemove', onResizerMouseMove)
  document.removeEventListener('mouseup', onResizerMouseUp)
  document.body.style.cursor = ''
  document.body.classList.remove('resizing-active')
}

function onVerticalResizerMouseDown(e) {
  e.preventDefault()
  isVerticalResizing.value = true
  document.addEventListener('mousemove', onVerticalResizerMouseMove)
  document.addEventListener('mouseup', onVerticalResizerMouseUp)
  document.body.style.cursor = 'row-resize'
  document.body.classList.add('resizing-active')
}

function onVerticalResizerMouseMove(e) {
  if (!isVerticalResizing.value || !resizerContainerRef.value) return
  const containerRect = resizerContainerRef.value.getBoundingClientRect()
  const relativeY = e.clientY - containerRect.top
  const minGrid = 200
  const maxGrid = Math.max(300, totalAvailableHeight.value - 120)
  gridHeight.value = Math.max(minGrid, Math.min(maxGrid, relativeY))
}

function onVerticalResizerMouseUp() {
  isVerticalResizing.value = false
  document.removeEventListener('mousemove', onVerticalResizerMouseMove)
  document.removeEventListener('mouseup', onVerticalResizerMouseUp)
  document.body.style.cursor = ''
  document.body.classList.remove('resizing-active')
}

function handleWindowResize() {
  windowHeight.value = window.innerHeight
  const minGrid = 200
  const maxGrid = Math.max(300, totalAvailableHeight.value - 120)
  
  if (gridHeight.value > maxGrid) {
    gridHeight.value = maxGrid
  }
  if (gridHeight.value < minGrid) {
    gridHeight.value = minGrid
  }
}

onMounted(() => {
  window.addEventListener('resize', handleWindowResize)
  // Set initial grid height to 60% of available height
  gridHeight.value = Math.max(300, Math.min(750, Math.round(totalAvailableHeight.value * 0.6)))
  handleWindowResize()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleWindowResize)
})

function buildMermaidGraph() {
  const clusters = sortedSubsystems.value.filter(sub => selectedMermaidSubsystems.has(sub.id)).slice(0, 18)
  const clusterIds = new Set(clusters.map((cluster) => cluster.id))
  const lines = ['flowchart LR']
  const visibleNodesMap = new Map()

  const renderedNodeIds = new Set()
  let renderedEdgeCount = 0

  function hashCode(str) {
    if (!str) return 0
    let hash = 0
    for (let i = 0; i < str.length; i++) {
      hash = (hash << 5) - hash + str.charCodeAt(i)
      hash |= 0
    }
    return Math.abs(hash)
  }

  clusters.forEach((cluster) => {
    const clusterId = mermaidId(cluster.id)
    const rootId = `${clusterId}_root`
    renderedNodeIds.add(rootId)
    lines.push(`  subgraph ${clusterId}["${escapeMermaid(cluster.name)}"]`)
    lines.push(`    ${rootId}["${escapeMermaid(cluster.name)}<br/>${formatNumber(cluster.nodeCount)} nodes<br/>stability ${cluster.stabilityScore}"]`)

    if (expandedClusters.has(cluster.id)) {
      const apis = cluster.apiEndpoints || []
      const central = (cluster.centralNodes || []).slice(0, 12)

      const packagesMap = new Map()
      const classesMap = new Map()
      const methodsMap = new Map()

      // Find boundary candidates linking this subsystem to other visible subsystems
      const boundaryCandidates = []
      ;(discovery.value?.crossNodeLinks || []).forEach(link => {
        if (link.sourceSubsystemId === cluster.name) {
          const targetSub = clusters.find(c => c.name === link.targetSubsystemId)
          if (targetSub) {
            boundaryCandidates.push({
              name: link.sourceNodeName.substring(link.sourceNodeName.lastIndexOf('.') + 1).replace('()', ''),
              qualifiedName: link.sourceNodeName,
              type: link.sourceNodeName.includes('(') ? 'METHOD' : 'CLASS'
            })
          }
        }
        if (link.targetSubsystemId === cluster.name) {
          const sourceSub = clusters.find(c => c.name === link.sourceSubsystemId)
          if (sourceSub) {
            boundaryCandidates.push({
              name: link.targetNodeName.substring(link.targetNodeName.lastIndexOf('.') + 1).replace('()', ''),
              qualifiedName: link.targetNodeName,
              type: link.targetNodeName.includes('(') ? 'METHOD' : 'CLASS'
            })
          }
        }
      })

      const addNodeToMap = (node) => {
        if (node.type === 'PACKAGE') {
          packagesMap.set(node.qualifiedName, { ...node })
        } else if (node.type === 'CLASS') {
          classesMap.set(node.qualifiedName, { ...node })
          
          const lastDot = node.qualifiedName.lastIndexOf('.')
          if (lastDot !== -1) {
            const pkgPath = node.qualifiedName.substring(0, lastDot)
            const pkgName = node.packageName || pkgPath.substring(pkgPath.lastIndexOf('.') + 1)
            if (!packagesMap.has(pkgPath)) {
              packagesMap.set(pkgPath, {
                id: hashCode(pkgPath),
                name: pkgName,
                qualifiedName: pkgPath,
                type: 'PACKAGE'
              })
            }
          }
        } else if (node.type === 'METHOD') {
          methodsMap.set(node.qualifiedName, { ...node })

          const lastDotM = node.qualifiedName.lastIndexOf('.')
          if (lastDotM !== -1) {
            const classPath = node.qualifiedName.substring(0, lastDotM)
            const className = classPath.substring(classPath.lastIndexOf('.') + 1)
            if (!classesMap.has(classPath)) {
              classesMap.set(classPath, {
                id: hashCode(classPath),
                name: className,
                qualifiedName: classPath,
                type: 'CLASS'
              })
            }

            const lastDotC = classPath.lastIndexOf('.')
            if (lastDotC !== -1) {
              const pkgPath = classPath.substring(0, lastDotC)
              const pkgName = pkgPath.substring(pkgPath.lastIndexOf('.') + 1)
              if (!packagesMap.has(pkgPath)) {
                packagesMap.set(pkgPath, {
                  id: hashCode(pkgPath),
                  name: pkgName,
                  qualifiedName: pkgPath,
                  type: 'PACKAGE'
                })
              }
            }
          }
        }
      }

      central.forEach(addNodeToMap)
      boundaryCandidates.forEach(addNodeToMap)

      const packages = Array.from(packagesMap.values())
      const classes = Array.from(classesMap.values())
      const methods = Array.from(methodsMap.values())

      const controllers = classes.filter(c => c.name.endsWith('Controller') || c.name.endsWith('Client'))
      const services = classes.filter(c => c.name.endsWith('Service') || c.name.endsWith('Policy') || c.name.endsWith('Workflow'))
      const repos = classes.filter(c => c.name.endsWith('Repository') || c.name.endsWith('Mapper') || c.name.endsWith('Dao'))
      // Draw APIs
      apis.forEach((api, index) => {
        const apiId = `${clusterId}_api_${index}`
        visibleNodesMap.set(api.path + "@" + cluster.name, apiId)
        renderedNodeIds.add(apiId)
        lines.push(`    ${apiId}(["${api.method} ${api.path}"])`)
        lines.push(`    class ${apiId} apiNode`)
        lines.push(`    ${rootId} -.--> ${apiId}`)
        renderedEdgeCount++

        let targetController = controllers.find(ctrl => {
          const apiPrefix = api.path.split('/')[2] || ''
          return ctrl.name.toLowerCase().startsWith(apiPrefix.toLowerCase())
        })
        if (!targetController && controllers.length > 0) {
          targetController = controllers[0]
        }

        if (targetController) {
          const targetId = `${clusterId}_class_${hashCode(targetController.qualifiedName)}`
          lines.push(`    ${apiId} == Routing ==> ${targetId}`)
          renderedEdgeCount++
        } else if (classes.length > 0) {
          const targetId = `${clusterId}_class_${hashCode(classes[0].qualifiedName)}`
          lines.push(`    ${apiId} == Routing ==> ${targetId}`)
          renderedEdgeCount++
        }
      })

      // Draw Packages
      packages.forEach(pkg => {
        const pkgId = `${clusterId}_pkg_${hashCode(pkg.qualifiedName)}`
        visibleNodesMap.set(pkg.qualifiedName + "@" + cluster.name, pkgId)
        renderedNodeIds.add(pkgId)
        lines.push(`    ${pkgId}{{"Package: ${escapeMermaid(pkg.name)}"}}`)
        lines.push(`    class ${pkgId} packageNode`)
        lines.push(`    ${rootId} -.- ${pkgId}`)
        renderedEdgeCount++
      })

      // Draw Classes
      classes.forEach(c => {
        const classId = `${clusterId}_class_${hashCode(c.qualifiedName)}`
        visibleNodesMap.set(c.qualifiedName + "@" + cluster.name, classId)
        renderedNodeIds.add(classId)
        let classLabel = c.name
        let nodeShape = `["${classLabel}"]`
        let nodeClass = 'serviceNode'

        if (controllers.includes(c)) {
          nodeShape = `["[Controller] ${classLabel}"]`
          nodeClass = 'controllerNode'
        } else if (services.includes(c)) {
          nodeShape = `["[Service] ${classLabel}"]`
          nodeClass = 'serviceNode'
        } else if (repos.includes(c)) {
          nodeShape = `[("${classLabel} Repository")]`
          nodeClass = 'repoNode'
        } else {
          nodeShape = `["[Class] ${classLabel}"]`
          nodeClass = 'classNode'
        }

        lines.push(`    ${classId}${nodeShape}`)
        lines.push(`    class ${classId} ${nodeClass}`)

        const lastDot = c.qualifiedName.lastIndexOf('.')
        const classPkgPath = lastDot !== -1 ? c.qualifiedName.substring(0, lastDot) : ''
        if (packagesMap.has(classPkgPath)) {
          const pkgId = `${clusterId}_pkg_${hashCode(classPkgPath)}`
          lines.push(`    ${pkgId} -. Contains .-> ${classId}`)
          renderedEdgeCount++
        } else {
          lines.push(`    ${rootId} --- ${classId}`)
          renderedEdgeCount++
        }
      })

      // Draw Methods
      methods.forEach(m => {
        const methodId = `${clusterId}_method_${hashCode(m.qualifiedName)}`
        visibleNodesMap.set(m.qualifiedName + "@" + cluster.name, methodId)
        renderedNodeIds.add(methodId)
        lines.push(`    ${methodId}("${m.name}()")`)
        lines.push(`    class ${methodId} methodNode`)

        const lastDotM = m.qualifiedName.lastIndexOf('.')
        const methodClassPath = lastDotM !== -1 ? m.qualifiedName.substring(0, lastDotM) : ''
        if (classesMap.has(methodClassPath)) {
          const classId = `${clusterId}_class_${hashCode(methodClassPath)}`
          lines.push(`    ${classId} === ${methodId}`)
          renderedEdgeCount++
        } else {
          lines.push(`    ${rootId} --- ${methodId}`)
          renderedEdgeCount++
        }
      })

      // Controller -> Service
      controllers.forEach(ctrl => {
        const ctrlId = `${clusterId}_class_${hashCode(ctrl.qualifiedName)}`
        services.forEach(srv => {
          const srvId = `${clusterId}_class_${hashCode(srv.qualifiedName)}`
          const isRelated = ctrl.name.replace('Controller', '').toLowerCase() === srv.name.replace('Service', '').toLowerCase()
          if (isRelated || controllers.length === 1 || services.length === 1) {
            lines.push(`    ${ctrlId} --> ${srvId}`)
            renderedEdgeCount++
          }
        })
      })

      // Service -> Repo
      services.forEach(srv => {
        const srvId = `${clusterId}_class_${hashCode(srv.qualifiedName)}`
        repos.forEach(rp => {
          const rpId = `${clusterId}_class_${hashCode(rp.qualifiedName)}`
          const isRelated = srv.name.replace('Service', '').toLowerCase() === rp.name.replace('Repository', '').toLowerCase()
          if (isRelated || services.length === 1 || repos.length === 1) {
            lines.push(`    ${srvId} --> ${rpId}`)
            renderedEdgeCount++
          }
        })
      })
    }
    lines.push('  end')
  })

  let linkCounter = 0
  const activeInternalLinks = new Set()

  // 1. Draw direct cross-subsystem internal connections (mapping to root if collapsed)
  ;(discovery.value?.crossNodeLinks || []).forEach(link => {
    let srcId = visibleNodesMap.get(link.sourceNodeName + "@" + link.sourceSubsystemId)
    let srcIsRoot = false
    if (!srcId) {
      const sub = sortedSubsystems.value.find(s => s.name === link.sourceSubsystemId)
      if (sub && selectedMermaidSubsystems.has(sub.id)) {
        srcId = `${mermaidId(sub.id)}_root`
        srcIsRoot = true
      }
    }

    let tgtId = visibleNodesMap.get(link.targetNodeName + "@" + link.targetSubsystemId)
    let tgtIsRoot = false
    if (!tgtId) {
      const sub = sortedSubsystems.value.find(s => s.name === link.targetSubsystemId)
      if (sub && selectedMermaidSubsystems.has(sub.id)) {
        tgtId = `${mermaidId(sub.id)}_root`
        tgtIsRoot = true
      }
    }

    if (srcId && tgtId && srcId !== tgtId) {
      // If both are root (collapsed), we skip drawing individual node-level lines between them
      if (srcIsRoot && tgtIsRoot) {
        return
      }

      // Draw the line from the visible internal node directly to target (which may be target internal node or target root)
      const relationLabel = escapeMermaid(link.relationType.replace('_', ' '))
      lines.push(`  ${srcId} -->|"${relationLabel}"| ${tgtId}`)
      lines.push(`  linkStyle ${linkCounter} stroke:#ef4444,stroke-width:1.5px,stroke-dasharray: 5 5`)
      linkCounter++
      renderedEdgeCount++

      activeInternalLinks.add(link.sourceSubsystemId + "-->" + link.targetSubsystemId)
      activeInternalLinks.add(link.targetSubsystemId + "-->" + link.sourceSubsystemId)
    }
  })

  // 2. Draw subsystem-level root connections if no internal connection has been drawn between this pair
  ;(discovery.value?.subsystemLinks || [])
    .filter((link) => clusterIds.has(link.source) && clusterIds.has(link.target))
    .slice(0, 1000)
    .forEach((link) => {
      const srcSubName = sortedSubsystems.value.find(s => s.id === link.source)?.name || link.source
      const tgtSubName = sortedSubsystems.value.find(s => s.id === link.target)?.name || link.target
      
      const linkKey = srcSubName + "-->" + tgtSubName
      if (activeInternalLinks.has(linkKey)) {
        return // Skip, already mapped at the internal class/method level!
      }

      const source = `${mermaidId(link.source)}_root`
      const target = `${mermaidId(link.target)}_root`
      const arrow = link.couplingStrength === 'HIGH' ? '==>' : link.couplingStrength === 'LOW' ? '-.->' : '-->'
      lines.push(`  ${source} ${arrow}|${escapeMermaid(link.couplingStrength)} / ${link.edgeCount} edges| ${target}`)
      lines.push(`  linkStyle ${linkCounter} stroke:${linkColor(link.couplingStrength)},stroke-width:${link.couplingStrength === 'HIGH' ? '3px' : '1.8px'}`)
      linkCounter++
      renderedEdgeCount++
    })

  lines.push('  classDef clusterRoot fill:#eff6ff,stroke:#2563eb,stroke-width:1.5px,color:#0f172a')
  lines.push('  classDef expandedNode fill:#ffffff,stroke:#94a3b8,color:#0f172a')
  lines.push('  classDef apiNode fill:#e2fcf1,stroke:#0f9f58,stroke-width:1.5px,color:#0b7a43,font-weight:700')
  lines.push('  classDef controllerNode fill:#eff6ff,stroke:#2563eb,stroke-width:1.5px,color:#1e40af,font-weight:700')
  lines.push('  classDef serviceNode fill:#f3e8ff,stroke:#9333ea,stroke-width:1.5px,color:#581c87')
  lines.push('  classDef repoNode fill:#ffedd5,stroke:#ea580c,stroke-width:1.5px,color:#7c2d12')
  lines.push('  classDef methodNode fill:#f8fafc,stroke:#94a3b8,stroke-width:1px,color:#475569,font-style:italic')
  lines.push('  classDef packageNode fill:#fffbeb,stroke:#d97706,stroke-width:1.2px,color:#78350f')
  lines.push('  classDef classNode fill:#f1f5f9,stroke:#475569,stroke-width:1.2px,color:#1e293b')

  clusters.forEach((cluster) => {
    const clusterId = mermaidId(cluster.id)
    lines.push(`  class ${clusterId}_root clusterRoot`)
  })

  return {
    graph: lines.join('\n'),
    nodeCount: renderedNodeIds.size,
    edgeCount: renderedEdgeCount
  }
}

function linkColor(strength) {
  if (strength === 'HIGH') return '#dc2626'
  if (strength === 'MEDIUM') return '#2563eb'
  return '#94a3b8'
}

function mermaidId(raw) {
  return `m_${String(raw || 'cluster').toLowerCase().replace(/[^a-z0-9_]/g, '_')}`
}

function escapeMermaid(raw) {
  return String(raw || '').replace(/"/g, "'").replace(/\[/g, '(').replace(/\]/g, ')')
}

function formatNumber(value) {
  if (value === null || value === undefined || value === '') return '-'
  return new Intl.NumberFormat('en-US').format(value)
}

function handleError(error, fallback) {
  const data = error?.response?.data
  const serverMessage = data?.message || data?.error || error.message
  errorMessage.value = serverMessage ? `${fallback}: ${serverMessage}` : fallback
}

const boundaryNodeLimit = ref(20)
const boundarySortOrder = ref('SCORE_DESC')
const boundaryNodeType = ref('ALL')
const boundaryFromSubsystem = ref(null)
const boundaryToSubsystem = ref(null)

watch([boundaryNodeLimit, boundarySortOrder, boundaryNodeType, boundaryFromSubsystem, boundaryToSubsystem], () => {
  fetchBoundaryNodes()
})

async function fetchBoundaryNodes() {
  if (!discovery.value || !discovery.value.discoveryRunId) return
  loadingBoundary.value = true
  try {
    const response = await axios.post(
      `${BOUNDARY_API}`,
      {
        discoveryRunId: discovery.value.discoveryRunId,
        nodeLimit: boundaryNodeLimit.value,
        sortOrder: boundarySortOrder.value,
        nodeType: boundaryNodeType.value,
        fromSubsystem: boundaryFromSubsystem.value,
        toSubsystem: boundaryToSubsystem.value
      }
    )
    boundaryData.value = response.data
  } catch (error) {
    handleError(error, 'Boundary node detection failed')
  } finally {
    loadingBoundary.value = false
  }
}

function toggleMermaidSubsystem(clusterId) {
  if (selectedMermaidSubsystems.has(clusterId)) {
    selectedMermaidSubsystems.delete(clusterId)
  } else {
    selectedMermaidSubsystems.add(clusterId)
  }
}

function selectAllMermaidSubsystems() {
  if (!discovery.value) return
  discovery.value.subsystems.forEach(sub => selectedMermaidSubsystems.add(sub.id))
}

function selectNoneMermaidSubsystems() {
  selectedMermaidSubsystems.clear()
}

</script>

<style>
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
