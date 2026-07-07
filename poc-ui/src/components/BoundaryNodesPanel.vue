<template>
  <div class="boundary-panel">
    <div v-if="loading" class="boundary-loading">
      <div class="spinner"></div>
      <p>Calculating boundary nodes on demand...</p>
    </div>

    <div v-else-if="!boundaryData" class="boundary-empty">
      <p>No boundary node data available. Run Subsystem Discovery first.</p>
    </div>

    <div v-else class="boundary-content animate-fade-in">
      <!-- Summary Metrics Grid -->
      <section class="metrics-grid" style="margin-bottom: 24px;">
        <article>
          <span>Total Boundary Nodes</span>
          <strong>{{ formatNumber(boundaryData.overview.totalBoundaryNodes) }}</strong>
        </article>
        <article>
          <span>Boundary Node Ratio</span>
          <strong>{{ (boundaryData.overview.boundaryNodeRatio * 100).toFixed(1) }}%</strong>
        </article>
        <article>
          <span>Avg. Cross Connections</span>
          <strong>{{ boundaryData.overview.averageCrossSubsystemConnections }}</strong>
        </article>
        <article>
          <span>Max Boundary Score</span>
          <strong style="color: #6366f1;">{{ boundaryData.overview.maximumBoundaryScore.toFixed(3) }}</strong>
        </article>
      </section>

      <!-- Critical Nodes & Overview Row -->
      <div class="boundary-dashboard-row">
        <!-- Top Critical Nodes list -->
        <div class="dashboard-card critical-nodes-card">
          <h3>Top Critical Boundary Nodes</h3>
          <p class="card-desc">Nodes with the highest cross-subsystem impact and connectivity.</p>
          <div class="critical-nodes-list">
            <div 
              v-for="(nodeName, idx) in boundaryData.overview.topCriticalBoundaryNodes" 
              :key="idx" 
              class="critical-node-item"
            >
              <span class="critical-node-rank">{{ idx + 1 }}</span>
              <span class="critical-node-name" :title="nodeName">{{ nodeName }}</span>
            </div>
          </div>
        </div>

        <!-- Description / Insight Card -->
        <div class="dashboard-card insights-card">
          <h3>Boundary Nodes Insight</h3>
          <p class="card-desc">What are boundary nodes?</p>
          <div class="insight-content">
            <p>
              Boundary nodes represent the coupling surface between subsystems. They act as the entry and exit points for service communication.
            </p>
            <div class="insight-tips">
              <div class="insight-tip-item">
                <span class="tip-dot" style="background: #ef4444;"></span>
                <span><strong>High-risk endpoints</strong>: High boundary scores indicate critical interfaces.</span>
              </div>
              <div class="insight-tip-item">
                <span class="tip-dot" style="background: #2563eb;"></span>
                <span><strong>Refactoring Candidates</strong>: Ideal boundaries for microservice extraction.</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Top Boundary Nodes Table -->
      <div class="dashboard-card table-card">
        <div class="card-header" style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; padding-bottom: 16px;">
          <div>
            <h3>
              {{ sortOrder === 'TOP' ? 'Top' : 'Bottom' }} 
              {{ boundaryData.boundaryNodes.length }} Boundary Nodes
            </h3>
            <p class="card-desc" style="margin-bottom: 0;">
              Ordered by Boundary Score {{ sortOrder === 'TOP' ? 'descending (Most Connected)' : 'ascending (Least Connected)' }}.
            </p>
          </div>
          <div class="filter-controls" style="display: flex; gap: 12px; align-items: center;">
            <div style="display: flex; flex-direction: column; gap: 4px;">
              <label style="font-size: 10px; font-weight: 700; color: #64748b; letter-spacing: 0.05em;">SORT ORDER</label>
              <select 
                :value="sortOrder" 
                @change="$emit('update:sortOrder', $event.target.value)"
                style="padding: 6px 12px; border-radius: 6px; border: 1px solid #cbd5e1; font-size: 13px; background: #fff; cursor: pointer; min-width: 155px; color: #1e293b; font-weight: 500; outline: none;"
              >
                <option value="TOP">Most Connected (Top)</option>
                <option value="BOTTOM">Least Connected (Bottom)</option>
              </select>
            </div>
            <div style="display: flex; flex-direction: column; gap: 4px;">
              <label style="font-size: 10px; font-weight: 700; color: #64748b; letter-spacing: 0.05em;">LIMIT</label>
              <select 
                :value="nodeLimit" 
                @change="$emit('update:nodeLimit', Number($event.target.value))"
                style="padding: 6px 12px; border-radius: 6px; border: 1px solid #cbd5e1; font-size: 13px; background: #fff; cursor: pointer; min-width: 90px; color: #1e293b; font-weight: 500; outline: none;"
              >
                <option :value="10">10 nodes</option>
                <option :value="20">20 nodes</option>
                <option :value="50">50 nodes</option>
                <option :value="100">100 nodes</option>
              </select>
            </div>
          </div>
        </div>

        <div class="table-wrapper">
          <table class="boundary-table">
            <thead>
              <tr>
                <th>Node Identifier</th>
                <th>Subsystem</th>
                <th>Connected Subsystems</th>
                <th class="num-col">Outgoing Edges</th>
                <th class="num-col">Incoming Edges</th>
                <th class="num-col">Total Edges</th>
                <th style="width: 180px;">Boundary Score</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="node in boundaryData.boundaryNodes" :key="node.nodeId">
                <td class="node-cell">
                  <div class="node-name-wrapper" :title="node.nodeName">
                    <span class="node-icon">⚡</span>
                    <strong class="node-name">{{ node.nodeName }}</strong>
                  </div>
                </td>
                <td>
                  <span class="subsystem-badge self-subsystem">
                    {{ node.subsystemName }}
                    <small>{{ node.subsystemId }}</small>
                  </span>
                </td>
                <td>
                  <div class="connected-subsystems-list">
                    <span 
                      v-for="sub in node.connectedSubsystems" 
                      :key="sub" 
                      class="subsystem-badge target-subsystem"
                      :title="sub"
                    >
                      {{ sub }}
                    </span>
                  </div>
                </td>
                <td class="num-col">{{ formatNumber(node.outgoingCrossEdges) }}</td>
                <td class="num-col">{{ formatNumber(node.incomingCrossEdges) }}</td>
                <td class="num-col font-medium">{{ formatNumber(node.crossSubsystemEdgeCount) }}</td>
                <td>
                  <div class="score-cell">
                    <div class="score-bar-bg">
                      <div 
                        class="score-bar-fill" 
                        :style="{ width: (node.boundaryScore * 100) + '%' }"
                      ></div>
                    </div>
                    <span class="score-val">{{ node.boundaryScore.toFixed(3) }}</span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  boundaryData: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  nodeLimit: { type: Number, default: 20 },
  sortOrder: { type: String, default: 'TOP' }
})

defineEmits(['update:nodeLimit', 'update:sortOrder'])

function formatNumber(value) {
  if (value === null || value === undefined || value === '') return '-'
  return new Intl.NumberFormat('en-US').format(value)
}
</script>

<style scoped>
.boundary-panel {
  width: 100%;
}

.boundary-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #64748b;
  gap: 16px;
}

.spinner {
  width: 36px;
  height: 36px;
  border: 3.5px solid #e2e8f0;
  border-top-color: #2563eb;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.boundary-empty {
  text-align: center;
  padding: 48px 20px;
  color: #64748b;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
}

.boundary-dashboard-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 24px;
}

.dashboard-card {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.dashboard-card h3 {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.card-desc {
  font-size: 12.5px;
  color: #64748b;
  margin: 0 0 16px;
}

.critical-nodes-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.critical-node-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: #f8fafc;
  border-left: 3px solid #ef4444;
  border-radius: 0 6px 6px 0;
  font-size: 13px;
}

.critical-node-rank {
  font-weight: 700;
  color: #ef4444;
  background: #fee2e2;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
}

.critical-node-name {
  font-weight: 600;
  color: #334155;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

.insight-content {
  font-size: 13px;
  color: #334155;
  line-height: 1.5;
}

.insight-tips {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}

.insight-tip-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 12.5px;
}

.tip-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  margin-top: 5px;
  flex-shrink: 0;
}

.table-card {
  padding: 0;
  overflow: hidden;
}

.card-header {
  padding: 20px 20px 0;
}

.table-wrapper {
  overflow-x: auto;
  border-top: 1px solid #e2e8f0;
}

.boundary-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
  font-size: 13px;
}

.boundary-table th {
  background: #f8fafc;
  padding: 12px 16px;
  font-weight: 600;
  color: #475569;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  border-bottom: 1px solid #e2e8f0;
}

.boundary-table td {
  padding: 12px 16px;
  border-bottom: 1px solid #f1f5f9;
  vertical-align: middle;
}

.boundary-table tr:hover {
  background: #f8fafc;
}

.num-col {
  text-align: right;
}

.font-medium {
  font-weight: 600;
}

.node-cell {
  max-width: 250px;
}

.node-name-wrapper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-icon {
  font-size: 14px;
}

.node-name {
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.subsystem-badge {
  display: inline-flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 10px;
  border-radius: 6px;
  font-weight: 600;
  font-size: 12px;
  line-height: 1.15;
  max-width: 180px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.self-subsystem {
  background: #eff6ff;
  color: #1d4ed8;
  border: 1px solid #bfdbfe;
}

.self-subsystem small {
  color: #3b82f6;
  font-weight: 500;
  font-size: 10px;
}

.connected-subsystems-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  max-width: 280px;
}

.target-subsystem {
  background: #f3f4f6;
  color: #374151;
  border: 1px solid #e5e7eb;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 11px;
}

.score-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.score-bar-bg {
  flex: 1;
  height: 8px;
  background: #e2e8f0;
  border-radius: 4px;
  overflow: hidden;
  min-width: 80px;
}

.score-bar-fill {
  height: 100%;
  background: linear-gradient(90deg, #3b82f6, #6366f1);
  border-radius: 4px;
}

.score-val {
  font-weight: 700;
  color: #334155;
  font-variant-numeric: tabular-nums;
  width: 42px;
}

.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
