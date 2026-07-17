<template>
  <div class="boundary-panel">
    <!-- If no data at all and loading, show main loader -->
    <div v-if="!boundaryData && loading" class="boundary-loading">
      <div class="spinner"></div>
      <p>Calculating boundary nodes on demand...</p>
    </div>

    <!-- If no data and not loading, show empty state -->
    <div v-else-if="!boundaryData" class="boundary-empty">
      <p>No boundary node data available. Run Subsystem Discovery first.</p>
    </div>

    <!-- Otherwise show contents -->
    <div v-else class="boundary-content animate-fade-in">
      <!-- Summary Metrics Grid (4 stats) -->
      <section class="metrics-grid" style="margin-bottom: 20px;">
        <article>
          <span>
            Total Boundary Nodes
            <span class="tooltip-container tooltip-align-left-down">
              <i class="tooltip-icon">ⓘ</i>
              <span class="tooltip-box">
                <strong>Total Boundary Nodes</strong><br/><br/>
                The total number of unique nodes whose edges cross subsystem boundaries. These define the integration surface.
              </span>
            </span>
          </span>
          <strong>{{ formatNumber(boundaryData.overview.totalBoundaryNodes) }}</strong>
        </article>
        <article>
          <span>
            Boundary Node Ratio
            <span class="tooltip-container tooltip-align-left-down">
              <i class="tooltip-icon">ⓘ</i>
              <span class="tooltip-box">
                <strong>Boundary Node Ratio</strong><br/><br/>
                The proportion of boundary nodes relative to all nodes in the workspace. Lower ratio suggests stronger modular boundaries.
              </span>
            </span>
          </span>
          <strong>{{ (boundaryData.overview.boundaryRatio * 100).toFixed(1) }}%</strong>
        </article>
        <article>
          <span>
            Subsystem Interactions
            <span class="tooltip-container tooltip-align-left-down">
              <i class="tooltip-icon">ⓘ</i>
              <span class="tooltip-box">
                <strong>Subsystem Interactions</strong><br/><br/>
                The total number of subsystem-to-subsystem interaction pairs (bidirectional pairs are collapsed).
              </span>
            </span>
          </span>
          <strong>{{ formatNumber(collapsedInteractions.length) }}</strong>
        </article>
        <article>
          <span>
            Avg. Cross Connections
            <span class="tooltip-container tooltip-align-left-down">
              <i class="tooltip-icon">ⓘ</i>
              <span class="tooltip-box">
                <strong>Avg. Cross Connections</strong><br/><br/>
                The average number of target subsystems connected to each boundary node. Indicates coupling complexity.
              </span>
            </span>
          </span>
          <strong>{{ boundaryData.overview.avgCrossSubsystemConnections.toFixed(2) }}</strong>
        </article>
      </section>

      <!-- Main Explorer Layout Grid -->
      <div class="explorer-grid">
        
        <!-- Left Column: Collapsed Sidebar Tab -->
        <div 
          v-if="isInteractionsCollapsed" 
          class="collapsed-sidebar" 
          @click="isInteractionsCollapsed = false"
          title="Expand Subsystem Interactions"
        >
          <div class="collapsed-sidebar-inner">
            <span class="collapsed-title">Subsystem Interactions</span>
            <span class="expand-arrow">▶</span>
          </div>
        </div>

        <!-- Left Column: Subsystem Interactions (Expanded) -->
        <div 
          v-else
          class="dashboard-card interactions-card"
          :style="{ width: interactionsWidth + 'px', flex: '0 0 auto' }"
        >
          <div class="interactions-header" style="display: flex; justify-content: space-between; align-items: flex-start; gap: 8px;">
            <div style="min-width: 0;">
              <h3>Subsystem Interactions</h3>
              <p class="card-desc">Select a directed pair to view its boundary interfaces.</p>
            </div>
            <button 
              type="button" 
              class="collapse-btn" 
              @click="isInteractionsCollapsed = true"
              title="Collapse Panel"
              style="background: none; border: none; padding: 4px; cursor: pointer; color: #64748b; border-radius: 4px; display: flex; align-items: center; justify-content: center;"
            >
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.5">
                <path d="M15 19l-7-7 7-7" />
              </svg>
            </button>
          </div>
          <div class="interactions-list">
            <div 
              v-for="interaction in filteredCollapsedInteractions" 
              :key="interaction.subsystemA + '-' + interaction.subsystemB"
              class="interaction-item"
              :class="{ 
                'active': fromSubsystem === interaction.subsystemA && toSubsystem === interaction.subsystemB 
              }"
              @click="toggleInteraction(interaction)"
            >
              <div class="interaction-path">
                <span class="sub-name" :title="getSubsystemName(interaction.subsystemA)">{{ getSubsystemName(interaction.subsystemA) }}</span>
                <span class="path-arrow">➔</span>
                <span class="sub-name" :title="getSubsystemName(interaction.subsystemB)">{{ getSubsystemName(interaction.subsystemB) }}</span>
              </div>
              <span class="interaction-badge">
                {{ interaction.isMutual ? (interaction.forwardCount + interaction.backwardCount) : interaction.forwardCount }} edges
              </span>
            </div>
            <div v-if="!boundaryData.subsystemInteractions.length" class="empty-list-text">
              No cross-subsystem interactions found.
            </div>
          </div>
        </div>

        <!-- Horizontal Resizer Bar -->
        <div 
          v-if="!isInteractionsCollapsed"
          class="panel-resizer" 
          :class="{ resizing: isResizingInteractions }" 
          @mousedown="onResizerMouseDown"
        >
          <div class="resizer-line"></div>
        </div>

        <!-- Right Column Wrapper: Contains Boundary Nodes Table & Relationship Panel Side-by-Side -->
        <div class="explorer-detail-wrapper" style="flex: 1 1 0%; display: flex; gap: 0; min-width: 0; align-items: stretch; height: 100%;">
          <!-- Left Side: Boundary Nodes List -->
          <div 
            class="dashboard-card table-card" 
            :class="{ 'panel-loading-active': loading }" 
            :style="props.fromSubsystem && props.toSubsystem 
              ? { width: middlePanelWidth + 'px', flex: '0 0 auto', minWidth: '0' }
              : { flex: '1 1 0%', minWidth: '0' }"
            style="position: relative; height: 100%; display: flex; flex-direction: column; overflow: hidden;"
          >
            <!-- Subtle Loading Line -->
            <div v-if="loading" class="right-panel-loading-bar"></div>
            <div class="card-header" style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; padding-bottom: 16px;">
              <div>
                <h3>
                  <span v-if="boundaryData.selectedInteraction" class="drilldown-title-badge">Drill-down</span>
                  {{ boundaryData.selectedInteraction ? 'Participating' : 'Top' }}
                  {{ boundaryData.topBoundaryNodes.length }} Boundary Nodes
                </h3>
                <p class="card-desc" style="margin-bottom: 0; display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
                  <span v-if="boundaryData.selectedInteraction">
                    Showing nodes for <strong>{{ getSubsystemName(boundaryData.selectedInteraction.fromSubsystem) }}</strong> ➔ <strong>{{ getSubsystemName(boundaryData.selectedInteraction.toSubsystem) }}</strong> ({{ totalEdgesCount }} edges).
                    <button type="button" class="btn-clear-filter" @click="clearFilter">Clear filter</button>
                  </span>
                  <span v-else>
                    Global view ordered by selected filter configuration.
                  </span>
                </p>
              </div>
              <div class="filter-controls" style="display: flex; gap: 12px; align-items: center;">
                <!-- Sort dropdown removed (Option B: headers sort) -->
                <div style="display: flex; flex-direction: column; gap: 4px;">
                  <label style="font-size: 10px; font-weight: 700; color: #64748b; letter-spacing: 0.05em;">NODE TYPE</label>
                  <div class="node-type-dropdown-container" style="position: relative; display: inline-block; user-select: none;">
                    <button 
                      type="button"
                      class="custom-dropdown-btn"
                      @click.stop="isNodeTypeDropdownOpen = !isNodeTypeDropdownOpen"
                      style="display: flex; align-items: center; gap: 8px; padding: 6px 12px; border-radius: 6px; border: 1px solid #cbd5e1; font-size: 13px; background: #fff; cursor: pointer; min-width: 155px; color: #1e293b; font-weight: 500; outline: none; transition: all 0.15s ease;"
                    >
                      <!-- Selected Item Icon -->
                      <svg v-if="currentNodeTypeOption.value === 'ALL'" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="color: #64748b; flex-shrink: 0; vertical-align: middle;">
                        <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3" />
                      </svg>
                      <svg v-else-if="currentNodeTypeOption.value === 'PACKAGE'" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" style="color: #f59e0b; flex-shrink: 0; vertical-align: middle;">
                        <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
                      </svg>
                      <svg v-else-if="currentNodeTypeOption.value === 'CLASS'" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" style="color: #3b82f6; flex-shrink: 0; vertical-align: middle;">
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                        <polyline points="14 2 14 8 20 8" />
                        <circle cx="9" cy="15" r="3" fill="#3b82f6" fill-opacity="0.15" stroke="none" />
                        <text x="7" y="17.5" font-size="7" font-weight="900" fill="#3b82f6" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" style="user-select: none;">C</text>
                      </svg>
                      <svg v-else-if="currentNodeTypeOption.value === 'METHOD'" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" style="color: #8b5cf6; flex-shrink: 0; vertical-align: middle;">
                        <circle cx="12" cy="12" r="3" />
                        <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
                      </svg>
                      
                      <!-- Selected Item Label -->
                      <span style="flex: 1; text-align: left;">{{ currentNodeTypeOption.label }}</span>
                      
                      <!-- Arrow -->
                      <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.5" style="color: #64748b; margin-left: 4px; transition: transform 0.2s;" :style="{ transform: isNodeTypeDropdownOpen ? 'rotate(180deg)' : 'rotate(0)' }">
                        <polyline points="6 9 12 15 18 9" />
                      </svg>
                    </button>
                    
                    <!-- Dropdown Options -->
                    <div 
                      v-if="isNodeTypeDropdownOpen"
                      class="custom-dropdown-menu"
                      style="position: absolute; top: 100%; left: 0; right: 0; margin-top: 4px; background: #fff; border: 1px solid #cbd5e1; border-radius: 6px; box-shadow: 0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1); z-index: 999; padding: 4px 0;"
                    >
                      <div 
                        v-for="opt in nodeTypeOptions" 
                        :key="opt.value"
                        class="custom-dropdown-item"
                        @click.stop="selectNodeType(opt.value)"
                        style="display: flex; align-items: center; gap: 8px; padding: 8px 12px; font-size: 13px; color: #334155; cursor: pointer; transition: background 0.15s ease; font-weight: 500;"
                        :style="{ background: props.nodeType === opt.value ? '#f1f5f9' : '#fff' }"
                        @mouseenter="$event.target.style.background = '#f8fafc'"
                        @mouseleave="$event.target.style.background = props.nodeType === opt.value ? '#f1f5f9' : '#fff'"
                      >
                        <!-- Item Icon -->
                        <svg v-if="opt.value === 'ALL'" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" style="color: #64748b; flex-shrink: 0; vertical-align: middle;">
                          <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3" />
                        </svg>
                        <svg v-else-if="opt.value === 'PACKAGE'" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" style="color: #f59e0b; flex-shrink: 0; vertical-align: middle;">
                          <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
                        </svg>
                        <svg v-else-if="opt.value === 'CLASS'" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" style="color: #3b82f6; flex-shrink: 0; vertical-align: middle;">
                          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                          <polyline points="14 2 14 8 20 8" />
                          <circle cx="9" cy="15" r="3" fill="#3b82f6" fill-opacity="0.15" stroke="none" />
                          <text x="7" y="17.5" font-size="7" font-weight="900" fill="#3b82f6" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" style="user-select: none;">C</text>
                        </svg>
                        <svg v-else-if="opt.value === 'METHOD'" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" style="color: #8b5cf6; flex-shrink: 0; vertical-align: middle;">
                          <circle cx="12" cy="12" r="3" />
                          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
                        </svg>
                        
                        <span>{{ opt.label }}</span>
                      </div>
                    </div>
                  </div>
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
                    <th class="sortable-header" @click="handleHeaderClick('name')" style="cursor: pointer; user-select: none; width: 280px;">
                      Node Identifier
                      <span v-if="sortBy === 'name'" class="sort-direction">{{ sortDir === 'asc' ? '▲' : '▼' }}</span>
                    </th>
                    <th class="sortable-header" @click="handleHeaderClick('subsystemId')" style="cursor: pointer; user-select: none; width: 140px;">
                      Subsystem
                      <span v-if="sortBy === 'subsystemId'" class="sort-direction">{{ sortDir === 'asc' ? '▲' : '▼' }}</span>
                    </th>
                    <th style="width: 300px;">Connected Subsystems</th>
                    <th class="num-col sortable-header" @click="handleHeaderClick('outgoingCrossEdges')" style="cursor: pointer; user-select: none; width: 110px;">
                      Outgoing
                      <span v-if="sortBy === 'outgoingCrossEdges'" class="sort-direction">{{ sortDir === 'asc' ? '▲' : '▼' }}</span>
                    </th>
                    <th class="num-col sortable-header" @click="handleHeaderClick('incomingCrossEdges')" style="cursor: pointer; user-select: none; width: 110px;">
                      Incoming
                      <span v-if="sortBy === 'incomingCrossEdges'" class="sort-direction">{{ sortDir === 'asc' ? '▲' : '▼' }}</span>
                    </th>
                    <th class="sortable-header" @click="handleHeaderClick('boundaryScore')" style="cursor: pointer; user-select: none; width: 180px;">
                      Boundary Score
                      <span v-if="sortBy === 'boundaryScore'" class="sort-direction">{{ sortDir === 'asc' ? '▲' : '▼' }}</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr 
                    v-for="node in filteredBoundaryNodes" 
                    :key="node.id"
                    :data-node-id="node.qualifiedName"
                    :class="{ 
                      'active-row': boundaryData.selectedInteraction && isRelevantNode(node),
                      'highlighted-row': highlightedNodeId === node.qualifiedName
                    }"
                  >
                    <td class="node-cell">
                      <div class="node-name-wrapper" :title="node.qualifiedName">
                        <!-- Method Icon -->
                        <svg v-if="node.type === 'METHOD'" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" style="color: #8b5cf6; margin-right: 8px; flex-shrink: 0; vertical-align: middle; display: inline-block;">
                          <circle cx="12" cy="12" r="3" />
                          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
                        </svg>
                        <!-- Package Icon -->
                        <svg v-else-if="node.type === 'PACKAGE'" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" style="color: #f59e0b; margin-right: 8px; flex-shrink: 0; vertical-align: middle; display: inline-block;">
                          <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
                        </svg>
                        <!-- Class Icon -->
                        <svg v-else viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" style="color: #3b82f6; margin-right: 8px; flex-shrink: 0; vertical-align: middle; display: inline-block;">
                          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                          <polyline points="14 2 14 8 20 8" />
                          <circle cx="9" cy="15" r="3" fill="#3b82f6" fill-opacity="0.15" stroke="none" />
                          <text x="7" y="17.5" font-size="7" font-weight="900" fill="#3b82f6" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" style="user-select: none;">C</text>
                        </svg>
                        
                        <div style="display: flex; flex-direction: column; min-width: 0; flex: 1;">
                          <strong class="node-name" :title="node.qualifiedName">{{ node.name }}</strong>
                        </div>
                        <button 
                          type="button" 
                          class="btn-copy-node" 
                          title="Copy qualified name"
                          @click="copyToClipboard(node.qualifiedName, $event)"
                        >
                          <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.5">
                            <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                          </svg>
                        </button>
                      </div>
                    </td>
                    <td>
                      <span class="subsystem-badge self-subsystem" :title="'Stability Score: ' + node.subsystemStability">
                        {{ getSubsystemName(node.subsystemId) }}
                      </span>
                    </td>
                    <td>
                      <div class="connected-subsystems-list">
                        <template v-if="node.connectedSubsystems.length > 2">
                          <span 
                            v-for="subConn in node.connectedSubsystems.slice(0, 2)" 
                            :key="subConn.subsystem" 
                            class="subsystem-badge target-subsystem"
                            :class="{ 'de-emphasized': boundaryData.selectedInteraction && !isRelevantTargetSubsystem(subConn.subsystem) }"
                            :title="getSubsystemName(subConn.subsystem) + ' (' + (subConn.outgoingEdges + subConn.incomingEdges) + ' edges)'"
                          >
                            {{ getSubsystemName(subConn.subsystem) }}
                            <span class="badge-count">{{ subConn.outgoingEdges + subConn.incomingEdges }}</span>
                          </span>
                          <!-- Hover trigger for collapsed items -->
                          <div class="collapsed-tags-trigger">
                            +{{ node.connectedSubsystems.length - 2 }} more
                            <div class="collapsed-tags-popover">
                              <span 
                                v-for="subConn in node.connectedSubsystems.slice(2)" 
                                :key="subConn.subsystem" 
                                class="subsystem-badge target-subsystem"
                                :class="{ 'de-emphasized': boundaryData.selectedInteraction && !isRelevantTargetSubsystem(subConn.subsystem) }"
                                :title="getSubsystemName(subConn.subsystem) + ' (' + (subConn.outgoingEdges + subConn.incomingEdges) + ' edges)'"
                              >
                                {{ getSubsystemName(subConn.subsystem) }}
                                <span class="badge-count">{{ subConn.outgoingEdges + subConn.incomingEdges }}</span>
                              </span>
                            </div>
                          </div>
                        </template>
                        <template v-else>
                          <span 
                            v-for="subConn in node.connectedSubsystems" 
                            :key="subConn.subsystem" 
                            class="subsystem-badge target-subsystem"
                            :class="{ 'de-emphasized': boundaryData.selectedInteraction && !isRelevantTargetSubsystem(subConn.subsystem) }"
                            :title="getSubsystemName(subConn.subsystem) + ' (' + (subConn.outgoingEdges + subConn.incomingEdges) + ' edges)'"
                          >
                            {{ getSubsystemName(subConn.subsystem) }}
                            <span class="badge-count">{{ subConn.outgoingEdges + subConn.incomingEdges }}</span>
                          </span>
                        </template>
                      </div>
                    </td>
                    <td class="num-col font-mono">{{ formatNumber(node.outgoingCrossEdges) }}</td>
                    <td class="num-col font-mono">{{ formatNumber(node.incomingCrossEdges) }}</td>
                    <td>
                      <div class="score-cell">
                        <div class="score-bar-bg">
                          <div 
                            class="score-bar-fill" 
                            :style="{ width: (maxScoreInView > 0 ? (node.boundaryScore / maxScoreInView * 100) : 0) + '%' }"
                          ></div>
                        </div>
                        <span class="score-val font-mono">{{ node.boundaryScore.toFixed(3) }}</span>
                      </div>
                    </td>
                  </tr>
                  <tr v-if="!boundaryData.topBoundaryNodes.length">
                    <td colspan="6" style="text-align: center; color: #64748b; padding: 32px;">
                      No boundary nodes found matching the current filters.
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <!-- Middle Resizer Bar -->
          <div 
            v-if="props.fromSubsystem && props.toSubsystem"
            class="panel-resizer" 
            :class="{ resizing: isResizingMiddle }" 
            @mousedown="onMiddleResizerMouseDown"
          >
            <div class="resizer-line"></div>
          </div>

          <!-- Right Side: Detailed Edge Connections Card (visible only when interaction selected) -->
          <section 
            v-if="props.fromSubsystem && props.toSubsystem" 
            class="dashboard-card detailed-edges-card animate-fade-in" 
            :class="{ 'panel-loading-active': loading }" 
            style="flex: 1 1 0%; min-width: 0; position: relative; height: 100%; display: flex; flex-direction: column; overflow: hidden; margin-top: 0;"
          >
            <!-- Subtle Loading Line -->
            <div v-if="loading" class="right-panel-loading-bar"></div>
            <div class="card-header" style="padding-bottom: 16px; display: flex; flex-direction: column; gap: 12px; border-bottom: 1px solid #e2e8f0; margin-bottom: 16px;">
              <div style="display: flex; justify-content: space-between; align-items: center;">
                <div>
                  <h3 style="margin: 0; font-size: 15px; font-weight: 700; color: #0f172a;">Cross-Subsystem Dependencies</h3>
                  <p class="card-desc" style="margin: 4px 0 0 0; font-size: 11px; color: #64748b;">
                    References between <strong>{{ props.fromSubsystem }}</strong> and <strong>{{ props.toSubsystem }}</strong>.
                  </p>
                </div>
                <div class="badge-count" style="font-size: 11px; font-weight: 700; background: #eff6ff; color: #1d4ed8; padding: 4px 10px; border-radius: 9999px;">
                  {{ filteredCrossNodeLinks.length }} active edges
                </div>
              </div>

              <!-- Direction toggle selector -->
              <div class="direction-filter-container" style="display: flex; align-items: center; gap: 8px;">
                <span style="font-size: 11px; font-weight: 700; color: #64748b; text-transform: uppercase; letter-spacing: 0.05em;">Direction:</span>
                <div class="direction-toggle-group" style="display: inline-flex; background: #f1f5f9; border: 1px solid #cbd5e1; padding: 2px; border-radius: 6px;">
                  <button 
                    type="button"
                    class="direction-toggle-btn"
                    :class="{ 'active': activeDirection === 'all' }"
                    @click="activeDirection = 'all'"
                    style="border: none; background: none; font-size: 11px; font-weight: 600; padding: 4px 10px; border-radius: 4px; cursor: pointer; transition: all 0.15s ease;"
                  >
                    All
                  </button>
                  <button 
                    type="button"
                    class="direction-toggle-btn"
                    v-if="availableDirections.includes('outgoing')"
                    :class="{ 'active': activeDirection === 'outgoing' }"
                    @click="activeDirection = 'outgoing'"
                    style="border: none; background: none; font-size: 11px; font-weight: 600; padding: 4px 10px; border-radius: 4px; cursor: pointer; transition: all 0.15s ease;"
                  >
                    Outgoing (➔)
                  </button>
                  <button 
                    type="button"
                    class="direction-toggle-btn"
                    v-if="availableDirections.includes('incoming')"
                    :class="{ 'active': activeDirection === 'incoming' }"
                    @click="activeDirection = 'incoming'"
                    style="border: none; background: none; font-size: 11px; font-weight: 600; padding: 4px 10px; border-radius: 4px; cursor: pointer; transition: all 0.15s ease;"
                  >
                    Incoming (←)
                  </button>
                </div>
              </div>
            </div>

            <div class="table-wrapper" style="overflow-y: auto; flex: 1; min-height: 0;">
              <table class="boundary-table detailed-edges-table">
                <thead>
                  <tr>
                    <th style="text-align: left; width: 42%;">Source Node (Caller)</th>
                    <th style="text-align: center; width: 16%;">Relationship</th>
                    <th style="text-align: left; width: 42%;">Target Node (Callee)</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="link in filteredCrossNodeLinks" :key="link.sourceNodeName + '-' + link.targetNodeName + '-' + link.relationType">
                    <td class="node-cell">
                      <div class="node-name-wrapper" :title="link.sourceNodeName" style="display: flex; align-items: flex-start; gap: 8px;">
                        <!-- Method Icon -->
                        <svg v-if="link.sourceNodeName.includes('(')" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" style="color: #8b5cf6; flex-shrink: 0; vertical-align: middle; display: inline-block;">
                          <circle cx="12" cy="12" r="3" />
                          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
                        </svg>
                        <!-- Package Icon -->
                        <svg v-else-if="link.sourceNodeName.split('.').pop() === link.sourceNodeName.split('.').pop().toLowerCase()" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" style="color: #f59e0b; flex-shrink: 0; vertical-align: middle; display: inline-block;">
                          <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
                        </svg>
                        <!-- Class Icon -->
                        <svg v-else viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" style="color: #3b82f6; flex-shrink: 0; vertical-align: middle; display: inline-block;">
                          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                          <polyline points="14 2 14 8 20 8" />
                          <circle cx="9" cy="15" r="3" fill="#3b82f6" fill-opacity="0.15" stroke="none" />
                          <text x="7" y="17.5" font-size="7" font-weight="900" fill="#3b82f6" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" style="user-select: none;">C</text>
                        </svg>
                        <div style="display: flex; flex-direction: column; min-width: 0; flex: 1;">
                          <strong 
                            class="node-name clickable-node" 
                            style="font-size: 12px; line-height: 1.2; color: #2563eb; cursor: pointer; text-decoration: underline; word-break: break-all;"
                            @click="scrollToNode(link.sourceNodeName)"
                          >
                            {{ link.sourceNodeName.substring(link.sourceNodeName.lastIndexOf('.') + 1) }}
                          </strong>
                          <span class="node-type-label" style="font-size: 9px; color: #64748b; font-family: monospace; word-break: break-all; margin-top: 2px;">
                            {{ link.sourceNodeName }}
                          </span>
                        </div>
                        <button 
                          type="button" 
                          class="btn-copy-node" 
                          title="Copy qualified name"
                          @click="copyToClipboard(link.sourceNodeName, $event)"
                        >
                          <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.5">
                            <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                          </svg>
                        </button>
                      </div>
                    </td>
                    <td style="text-align: center; vertical-align: middle;">
                      <span class="relationship-pill" :class="link.relationType.toLowerCase()">
                        {{ link.relationType.replace('_', ' ') }} ➔
                      </span>
                    </td>
                    <td class="node-cell">
                      <div class="node-name-wrapper" :title="link.targetNodeName" style="display: flex; align-items: flex-start; gap: 8px;">
                        <!-- Method Icon -->
                        <svg v-if="link.targetNodeName.includes('(')" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" style="color: #8b5cf6; flex-shrink: 0; vertical-align: middle; display: inline-block;">
                          <circle cx="12" cy="12" r="3" />
                          <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
                        </svg>
                        <!-- Package Icon -->
                        <svg v-else-if="link.targetNodeName.split('.').pop() === link.targetNodeName.split('.').pop().toLowerCase()" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" style="color: #f59e0b; flex-shrink: 0; vertical-align: middle; display: inline-block;">
                          <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
                        </svg>
                        <!-- Class Icon -->
                        <svg v-else viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" style="color: #3b82f6; flex-shrink: 0; vertical-align: middle; display: inline-block;">
                          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                          <polyline points="14 2 14 8 20 8" />
                          <circle cx="9" cy="15" r="3" fill="#3b82f6" fill-opacity="0.15" stroke="none" />
                          <text x="7" y="17.5" font-size="7" font-weight="900" fill="#3b82f6" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif" style="user-select: none;">C</text>
                        </svg>
                        <div style="display: flex; flex-direction: column; min-width: 0; flex: 1;">
                          <strong 
                            class="node-name clickable-node" 
                            style="font-size: 12px; line-height: 1.2; color: #2563eb; cursor: pointer; text-decoration: underline; word-break: break-all;"
                            @click="scrollToNode(link.targetNodeName)"
                          >
                            {{ link.targetNodeName.substring(link.targetNodeName.lastIndexOf('.') + 1) }}
                          </strong>
                          <span class="node-type-label" style="font-size: 9px; color: #64748b; font-family: monospace; word-break: break-all; margin-top: 2px;">
                            {{ link.targetNodeName }}
                          </span>
                        </div>
                        <button 
                          type="button" 
                          class="btn-copy-node" 
                          title="Copy qualified name"
                          @click="copyToClipboard(link.targetNodeName, $event)"
                        >
                          <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2.5">
                            <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
                            <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
                          </svg>
                        </button>
                      </div>
                    </td>
                  </tr>
                  <tr v-if="!filteredCrossNodeLinks.length">
                    <td colspan="3" style="text-align: center; color: #64748b; padding: 32px;">
                      No direct internal relationships found between these subsystems.
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  boundaryData: { type: Object, default: null },
  discovery: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  nodeLimit: { type: Number, default: 20 },
  sortOrder: { type: String, default: 'MOST_CONNECTED' },
  nodeType: { type: String, default: 'ALL' },
  fromSubsystem: { type: String, default: null },
  toSubsystem: { type: String, default: null }
})

const emit = defineEmits([
  'update:nodeLimit', 
  'update:sortOrder', 
  'update:nodeType',
  'update:fromSubsystem',
  'update:toSubsystem'
])

// Resizable & Collapsible Sidebar logic
const isResizingInteractions = ref(false)
const interactionsWidth = ref(360)
const isInteractionsCollapsed = ref(false)
const sortBy = ref('boundaryScore')
const sortDir = ref('desc')

const isResizingMiddle = ref(false)
const middlePanelWidth = ref(550)

const activeDirection = ref('all') // 'all', 'outgoing', 'incoming'
const highlightedNodeId = ref(null)

watch(() => [props.fromSubsystem, props.toSubsystem], () => {
  activeDirection.value = 'all'
  highlightedNodeId.value = null
})

const isNodeTypeDropdownOpen = ref(false)
const nodeTypeOptions = [
  { value: 'ALL', label: 'All Node Types', icon: 'ALL' },
  { value: 'PACKAGE', label: 'Packages Only', icon: 'PACKAGE' },
  { value: 'CLASS', label: 'Classes Only', icon: 'CLASS' },
  { value: 'METHOD', label: 'Methods Only', icon: 'METHOD' }
]

const currentNodeTypeOption = computed(() => {
  return nodeTypeOptions.find(o => o.value === props.nodeType) || nodeTypeOptions[0]
})

function selectNodeType(value) {
  emit('update:nodeType', value)
  isNodeTypeDropdownOpen.value = false
}

function handleDocumentClick(e) {
  const container = document.querySelector('.node-type-dropdown-container')
  if (container && !container.contains(e.target)) {
    isNodeTypeDropdownOpen.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
})

onUnmounted(() => {
  document.removeEventListener('click', handleDocumentClick)
})

function scrollToNode(qualifiedName) {
  highlightedNodeId.value = qualifiedName
  setTimeout(() => {
    if (highlightedNodeId.value === qualifiedName) {
      highlightedNodeId.value = null
    }
  }, 2500)

  nextTick(() => {
    const escaped = qualifiedName.replace(/"/g, '\\"')
    const element = document.querySelector(`[data-node-id="${escaped}"]`)
    if (element) {
      element.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }
  })
}

const isCurrentInteractionMutual = computed(() => {
  const fromName = props.fromSubsystem
  const toName = props.toSubsystem
  if (!fromName || !toName) return false

  const hasForward = props.boundaryData?.subsystemInteractions?.some(
    i => i.fromSubsystem === fromName && i.toSubsystem === toName && i.interactionCount > 0
  )
  const hasBackward = props.boundaryData?.subsystemInteractions?.some(
    i => i.fromSubsystem === toName && i.toSubsystem === fromName && i.interactionCount > 0
  )
  return hasForward && hasBackward
})

const totalEdgesCount = computed(() => {
  if (!props.fromSubsystem || !props.toSubsystem) return 0
  const fromName = props.fromSubsystem
  const toName = props.toSubsystem

  const forward = props.boundaryData?.subsystemInteractions?.find(
    i => i.fromSubsystem === fromName && i.toSubsystem === toName
  )?.interactionCount || 0

  const backward = props.boundaryData?.subsystemInteractions?.find(
    i => i.fromSubsystem === toName && i.toSubsystem === fromName
  )?.interactionCount || 0

  return forward + backward
})

const availableDirections = computed(() => {
  const fromName = props.fromSubsystem
  const toName = props.toSubsystem
  if (!fromName || !toName) return ['all']

  const isMutual = isCurrentInteractionMutual.value
  const dirs = ['all', 'outgoing']
  if (isMutual) {
    dirs.push('incoming')
  }
  return dirs
})

function onResizerMouseDown(e) {
  e.preventDefault()
  isResizingInteractions.value = true
  document.addEventListener('mousemove', onResizerMouseMove)
  document.addEventListener('mouseup', onResizerMouseUp)
  document.body.style.cursor = 'col-resize'
  document.body.classList.add('resizing-active')
}

function onResizerMouseMove(e) {
  if (!isResizingInteractions.value) return
  const grid = document.querySelector('.explorer-grid')
  if (!grid) return
  const rect = grid.getBoundingClientRect()
  const relativeX = e.clientX - rect.left
  interactionsWidth.value = Math.max(220, Math.min(600, relativeX))
}

function onResizerMouseUp() {
  isResizingInteractions.value = false
  document.removeEventListener('mousemove', onResizerMouseMove)
  document.removeEventListener('mouseup', onResizerMouseUp)
  document.body.style.cursor = ''
  document.body.classList.remove('resizing-active')
}

function onMiddleResizerMouseDown(e) {
  e.preventDefault()
  isResizingMiddle.value = true
  document.addEventListener('mousemove', onMiddleResizerMouseMove)
  document.addEventListener('mouseup', onMiddleResizerMouseUp)
  document.body.style.cursor = 'col-resize'
  document.body.classList.add('resizing-active')
}

function onMiddleResizerMouseMove(e) {
  if (!isResizingMiddle.value) return
  const wrapper = document.querySelector('.explorer-detail-wrapper')
  if (!wrapper) return
  const rect = wrapper.getBoundingClientRect()
  const relativeX = e.clientX - rect.left
  const minMiddleWidth = 300
  const minRightWidth = 300
  const maxMiddleWidth = rect.width - minRightWidth
  middlePanelWidth.value = Math.max(minMiddleWidth, Math.min(maxMiddleWidth, relativeX))
}

function onMiddleResizerMouseUp() {
  isResizingMiddle.value = false
  document.removeEventListener('mousemove', onMiddleResizerMouseMove)
  document.removeEventListener('mouseup', onMiddleResizerMouseUp)
  document.body.style.cursor = ''
  document.body.classList.remove('resizing-active')
}

function formatNumber(value) {
  if (value === null || value === undefined || value === '') return '-'
  return new Intl.NumberFormat('en-US').format(value)
}

function toggleInteraction(interaction) {
  const fromName = interaction.subsystemA
  const toName = interaction.subsystemB
  if (props.fromSubsystem === fromName && props.toSubsystem === toName) {
    // Clicked already active interaction -> Clear the filter
    clearFilter()
  } else {
    // Select this interaction for drill-down
    emit('update:fromSubsystem', fromName)
    emit('update:toSubsystem', toName)
  }
}

function clearFilter() {
  emit('update:fromSubsystem', null)
  emit('update:toSubsystem', null)
}

// Subsystem search box query
const interactionSearchQuery = ref('')

const filteredCrossNodeLinks = computed(() => {
  if (!props.discovery || !props.discovery.crossNodeLinks) return []
  const fromName = props.fromSubsystem
  const toName = props.toSubsystem
  if (!fromName || !toName) return []

  const isMutual = isCurrentInteractionMutual.value

  return props.discovery.crossNodeLinks.filter(link => {
    if (activeDirection.value === 'outgoing') {
      return link.sourceSubsystemId === fromName && link.targetSubsystemId === toName
    } else if (activeDirection.value === 'incoming') {
      return isMutual && link.sourceSubsystemId === toName && link.targetSubsystemId === fromName
    } else {
      // 'all'
      if (isMutual) {
        return (link.sourceSubsystemId === fromName && link.targetSubsystemId === toName) ||
               (link.sourceSubsystemId === toName && link.targetSubsystemId === fromName)
      } else {
        return link.sourceSubsystemId === fromName && link.targetSubsystemId === toName
      }
    }
  }).sort((a, b) => b.weight - a.weight)
})

// Reset search and sort when API dataset or interactions change
watch(() => props.boundaryData, () => {
  interactionSearchQuery.value = ''
})

// Copy identifier helper
function copyToClipboard(text, event) {
  event.stopPropagation()
  if (navigator.clipboard) {
    navigator.clipboard.writeText(text).then(() => {
      const target = event.currentTarget
      const originalTitle = target.getAttribute('title')
      target.setAttribute('title', 'Copied!')
      target.classList.add('copied')
      setTimeout(() => {
        target.setAttribute('title', originalTitle)
        target.classList.remove('copied')
      }, 1500)
    }).catch(err => {
      console.error('Failed to copy: ', err)
    })
  } else {
    const textArea = document.createElement("textarea")
    textArea.value = text
    document.body.appendChild(textArea)
    textArea.select()
    try {
      document.execCommand('copy')
    } catch (err) {
      console.error('Fallback failed: ', err)
    }
    document.body.removeChild(textArea)
  }
}

// Visual scale relative to max score in view
const maxScoreInView = computed(() => {
  const nodes = props.boundaryData?.topBoundaryNodes || []
  if (!nodes.length) return 1.0
  const max = Math.max(...nodes.map(n => n.boundaryScore))
  return max > 0 ? max : 1.0
})

// Active subsystem highlighting
function isRelevantTag(nodeOwningSubsystem, tagSubsystem) {
  if (!props.fromSubsystem || !props.toSubsystem) return true
  if (nodeOwningSubsystem === props.fromSubsystem) {
    return tagSubsystem === props.toSubsystem
  }
  if (nodeOwningSubsystem === props.toSubsystem) {
    return tagSubsystem === props.fromSubsystem
  }
  return false
}

function getRelevantTags(node) {
  return node.connectedSubsystems.filter(c => isRelevantTag(node.owningSubsystem, c.subsystem))
}

function getNonRelevantTags(node) {
  return node.connectedSubsystems.filter(c => !isRelevantTag(node.owningSubsystem, c.subsystem))
}

function getNonRelevantTagsCount(node) {
  return getNonRelevantTags(node).length
}

// Helper to shorten subsystem names
function shorten(name, limit = 12) {
  if (!name) return ''
  if (name.length <= limit) return name
  return name.slice(0, limit) + '...'
}

// Interactive column sort handler
function handleHeaderClick(column) {
  if (sortBy.value === column) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortBy.value = column
    sortDir.value = 'desc'
  }
}

// Collapsed & Merged Mutual subsystem pairs
const collapsedInteractions = computed(() => {
  const rawList = props.boundaryData?.subsystemInteractions || []
  const result = []
  const processedKeys = new Set()
  
  for (const item of rawList) {
    const key = [item.fromSubsystem, item.toSubsystem].sort().join('|')
    if (processedKeys.has(key)) continue
    
    // 1. Identify intra-subsystem self-reference pairs
    if (item.fromSubsystem === item.toSubsystem) {
      result.push({
        isInternal: true,
        isMutual: false,
        subsystemA: item.fromSubsystem,
        subsystemB: item.toSubsystem,
        forwardCount: item.interactionCount,
        backwardCount: 0,
        forwardInteraction: item,
        backwardInteraction: null
      })
      processedKeys.add(key)
      continue
    }

    const reciprocal = rawList.find(other => 
      other.fromSubsystem === item.toSubsystem && 
      other.toSubsystem === item.fromSubsystem
    )
    
    if (reciprocal) {
      result.push({
        isInternal: false,
        isMutual: true,
        subsystemA: item.fromSubsystem,
        subsystemB: item.toSubsystem,
        forwardCount: item.interactionCount,
        backwardCount: reciprocal.interactionCount,
        forwardInteraction: item,
        backwardInteraction: reciprocal
      })
      processedKeys.add(key)
    } else {
      result.push({
        isInternal: false,
        isMutual: false,
        subsystemA: item.fromSubsystem,
        subsystemB: item.toSubsystem,
        forwardCount: item.interactionCount,
        backwardCount: 0,
        forwardInteraction: item,
        backwardInteraction: null
      })
      processedKeys.add(key)
    }
  }
  
  return result.sort((a, b) => {
    const maxA = Math.max(a.forwardCount, a.backwardCount)
    const maxB = Math.max(b.forwardCount, b.backwardCount)
    return maxB - maxA
  })
})

// Helper to get subsystem name from ID or name
function getSubsystemName(idOrName) {
  if (!idOrName) return ''
  const sub = props.discovery?.subsystems?.find(s => s.id === idOrName || s.name === idOrName)
  return sub ? sub.name : idOrName
}

// Helper to get subsystem ID from ID or name
function getSubsystemId(idOrName) {
  if (!idOrName) return ''
  const sub = props.discovery?.subsystems?.find(s => s.id === idOrName || s.name === idOrName)
  return sub ? sub.id : idOrName
}

const filteredCollapsedInteractions = computed(() => {
  const list = collapsedInteractions.value
  const query = interactionSearchQuery.value.trim().toLowerCase()
  if (!query) return list
  return list.filter(item => {
    const nameA = getSubsystemName(item.subsystemA).toLowerCase()
    const nameB = getSubsystemName(item.subsystemB).toLowerCase()
    return nameA.includes(query) || nameB.includes(query)
  })
})

const filteredBoundaryNodes = computed(() => {
  const rawNodes = props.boundaryData?.selectedInteraction?.boundaryNodes || props.boundaryData?.topBoundaryNodes || []
  let mapped = rawNodes.map(node => {
    const lastDot = node.nodeName.lastIndexOf('.')
    let name = node.nodeName
    if (lastDot !== -1) {
      name = node.nodeName.substring(lastDot + 1)
    }

    if (!node.nodeName) {
      console.warn('Boundary node is missing nodeName:', node)
    }
    if (!node.owningSubsystem) {
      console.warn('Boundary node is missing owningSubsystem:', node)
    }

    const subObj = props.discovery?.subsystems?.find(s => s.name === node.owningSubsystem || s.id === node.owningSubsystem)
    const stability = subObj ? subObj.stabilityScore : 'N/A'

    return {
      id: node.nodeName,
      name: name || 'unknown',
      qualifiedName: node.nodeName || 'unknown',
      type: node.nodeType || 'CLASS',
      subsystemId: node.owningSubsystem || 'unknown',
      subsystemStability: stability,
      connectedSubsystems: node.connectedSubsystems || [],
      outgoingCrossEdges: node.outgoingCrossEdges || 0,
      incomingCrossEdges: node.incomingCrossEdges || 0,
      boundaryScore: node.boundaryScore || 0.0
    }
  })

  console.log('DEBUG boundaryData selectedInteraction:', props.boundaryData?.selectedInteraction)
  console.log('DEBUG mapped nodes:', mapped.map(n => ({
    name: n.name,
    subsystemId: n.subsystemId,
    incoming: n.incomingCrossEdges,
    outgoing: n.outgoingCrossEdges
  })))

  // Apply direction filter to Participating Boundary Nodes
  if (props.fromSubsystem && props.toSubsystem) {
    const fromId = getSubsystemId(props.fromSubsystem)
    mapped = mapped.filter(node => {
      // The node MUST belong to the selected base subsystem (fromSubsystem)
      if (getSubsystemId(node.subsystemId).trim().toLowerCase() !== fromId.trim().toLowerCase()) {
        return false
      }
      
      // Filter based on direction
      if (activeDirection.value === 'outgoing') {
        return node.outgoingCrossEdges > 0
      } else if (activeDirection.value === 'incoming') {
        return node.incomingCrossEdges > 0
      }
      // 'all' direction: either outgoing or incoming to the target subsystem
      return node.outgoingCrossEdges > 0 || node.incomingCrossEdges > 0
    })
  }

  // Frontend Sorting
  return mapped.sort((a, b) => {
    let aVal = a[sortBy.value]
    let bVal = b[sortBy.value]

    if (sortBy.value === 'subsystemId') {
      aVal = getSubsystemName(a.subsystemId).toLowerCase()
      bVal = getSubsystemName(b.subsystemId).toLowerCase()
    } else if (sortBy.value === 'name') {
      aVal = (aVal || '').toLowerCase()
      bVal = (bVal || '').toLowerCase()
    }

    if (aVal < bVal) return sortDir.value === 'asc' ? -1 : 1
    if (aVal > bVal) return sortDir.value === 'asc' ? 1 : -1
    return 0
  })
})

function isRelevantNode(node) {
  const fromId = getSubsystemId(props.fromSubsystem)
  const toId = getSubsystemId(props.toSubsystem)
  const ownId = getSubsystemId(node.owningSubsystem)
  return ownId === fromId || ownId === toId
}

function isRelevantTargetSubsystem(subsystem) {
  const fromId = getSubsystemId(props.fromSubsystem)
  const toId = getSubsystemId(props.toSubsystem)
  const targetId = getSubsystemId(subsystem)
  return targetId === fromId || targetId === toId
}
</script>

<style scoped>
.boundary-panel {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.boundary-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
  height: 100%;
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

.explorer-grid {
  display: flex;
  gap: 0;
  align-items: stretch;
  width: 100%;
  min-height: 0;
  overflow: hidden;
  flex: 1;
  height: 100%;
}

/* Collapsed sidebar layout */
.collapsed-sidebar {
  width: 40px;
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.collapsed-sidebar:hover {
  background: #f1f5f9;
  border-color: #94a3b8;
}

.collapsed-sidebar-inner {
  writing-mode: vertical-rl;
  text-orientation: mixed;
  display: flex;
  align-items: center;
  gap: 12px;
  transform: rotate(180deg);
  color: #475569;
  font-weight: 700;
  font-size: 11px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  user-select: none;
}

.expand-arrow {
  font-size: 10px;
  color: #2563eb;
}

.dashboard-card {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.interactions-card {
  height: 100%;
  min-height: 250px;
  transition: width 0.2s cubic-bezier(0.25, 1, 0.5, 1);
}

.interactions-header {
  padding: 20px 20px 12px;
  border-bottom: 1px solid #e2e8f0;
  position: relative;
  z-index: 20;
}

.card-header {
  padding: 20px 20px 16px;
  position: relative;
  z-index: 20; /* Ensures dropdown overlays render above sticky th */
}

.interactions-header h3 {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.card-desc {
  font-size: 12px;
  color: #64748b;
  margin: 0;
}

.collapse-btn:hover {
  background: #f1f5f9 !important;
  color: #0f172a !important;
}

.interactions-list {
  overflow-y: auto;
  flex: 1;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.interaction-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-left: 3px solid #cbd5e1;
  border-radius: 0 6px 6px 0;
  cursor: pointer;
  transition: all 0.2s ease;
}

.interaction-item:hover {
  background: #f1f5f9;
  border-color: #cbd5e1;
  border-left-color: #94a3b8;
}

.interaction-item.active {
  background: rgba(37, 99, 235, 0.04);
  border-color: #bfdbfe;
  border-left-color: #2563eb;
}

.interaction-path {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: #334155;
  min-width: 0;
}

.interaction-item.active .interaction-path {
  color: #1d4ed8;
}

.sub-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

.path-arrow {
  color: #94a3b8;
  font-size: 11px;
  flex-shrink: 0;
}

.interaction-badge {
  font-size: 11px;
  font-weight: 700;
  color: #64748b;
  align-self: flex-start;
}

.interaction-item.active .interaction-badge {
  color: #2563eb;
}

.empty-list-text {
  text-align: center;
  color: #94a3b8;
  font-size: 12.5px;
  padding: 24px 12px;
}

/* Horizontal Drag Resizer */
.panel-resizer {
  width: 12px;
  flex: 0 0 12px;
  cursor: col-resize;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  z-index: 10;
  user-select: none;
}

.resizer-line {
  width: 2px;
  height: 100%;
  background: #cbd5e1;
  transition: background 0.15s ease;
}

.panel-resizer:hover .resizer-line,
.panel-resizer.resizing .resizer-line {
  background: #2563eb;
  width: 3px;
}

.table-card {
  height: 100%;
}

.drilldown-title-badge {
  display: inline-block;
  background: #eff6ff;
  color: #2563eb;
  border: 1px solid #bfdbfe;
  padding: 2px 8px;
  border-radius: 9999px;
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-right: 8px;
  vertical-align: middle;
}

.btn-clear-filter {
  background: none;
  border: none;
  color: #2563eb;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  margin-left: 6px;
  text-decoration: underline;
}

.btn-clear-filter:hover {
  color: #1d4ed8;
}

.table-wrapper {
  overflow-x: auto;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
  border-top: 1px solid #e2e8f0;
}

.boundary-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
  font-size: 13px;
}

.boundary-table th {
  position: sticky;
  top: 0;
  z-index: 10;
  background: #f8fafc;
  padding: 14px 16px;
  font-weight: 700;
  color: #475569;
  font-size: 11.5px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  border-bottom: 1px solid #cbd5e1;
  box-shadow: inset 0 -1px 0 #cbd5e1;
}

.boundary-table td {
  padding: 12px 16px;
  border-bottom: 1px solid #f1f5f9;
  vertical-align: middle;
}

.boundary-table tr:hover {
  background: rgba(37, 99, 235, 0.025);
}

.num-col {
  text-align: right;
}

.font-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
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
  font-size: 11px;
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

.connected-subsystems-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-width: 280px;
}

.target-subsystem {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #f1f5f9;
  color: #334155;
  border: 1px solid #e2e8f0;
  padding: 3px 8px;
  border-radius: 9999px;
  font-size: 11px;
  font-weight: 500;
  line-height: 1;
}

.badge-count {
  background: #cbd5e1;
  color: #1e293b;
  font-size: 9px;
  font-weight: 700;
  padding: 1px 5px;
  border-radius: 9999px;
  margin-left: 2px;
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
  background: linear-gradient(90deg, #2563eb, #6366f1);
  border-radius: 4px;
}

.score-val {
  font-weight: 700;
  color: #334155;
  font-variant-numeric: tabular-nums;
  width: 42px;
}

/* Node Name Copy Button styling */
.btn-copy-node {
  opacity: 0;
  background: none;
  border: none;
  cursor: pointer;
  color: #64748b;
  padding: 2.5px;
  border-radius: 4px;
  transition: all 0.2s ease;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-left: 6px;
  flex-shrink: 0;
  outline: none;
}

.node-name-wrapper:hover .btn-copy-node {
  opacity: 1;
}

.btn-copy-node:hover {
  background: #f1f5f9;
  color: #0f172a;
}

.btn-copy-node.copied {
  color: #10b981 !important;
  background: #ecfdf5;
}

/* De-emphasized subsystem tag styling */
.target-subsystem.de-emphasized {
  opacity: 0.4;
  border-style: dashed;
  background: #f8fafc;
  transform: scale(0.95);
  transition: all 0.2s ease;
}

/* Collapsed subsystems trigger & popover */
.collapsed-tags-trigger {
  position: relative;
  display: inline-flex;
  align-items: center;
  background: #f8fafc;
  color: #64748b;
  border: 1px dashed #cbd5e1;
  padding: 3px 8px;
  border-radius: 9999px;
  font-size: 11px;
  font-weight: 700;
  cursor: help;
  user-select: none;
}

.collapsed-tags-trigger:hover {
  background: #f1f5f9;
  color: #475569;
  border-color: #94a3b8;
}

.collapsed-tags-popover {
  display: none;
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  margin-bottom: 6px;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  padding: 8px;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
  z-index: 50;
  flex-wrap: wrap;
  gap: 6px;
  width: max-content;
  max-width: 250px;
}

.collapsed-tags-trigger:hover .collapsed-tags-popover {
  display: flex;
}

.active-row {
  border-color: #cbd5e1;
  border-left-color: #94a3b8;
  background: rgba(148, 163, 184, 0.04);
}

.sortable-header {
  transition: background-color 0.2s ease, color 0.2s ease;
}

.sortable-header:hover {
  background-color: #f1f5f9 !important;
  color: #0f172a !important;
}

.sort-direction {
  margin-left: 4px;
  font-size: 10px;
}

.animate-fade-in {
  animation: fadeIn 0.4s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}

.highlighted-row {
  background-color: rgba(254, 240, 138, 0.45) !important;
  outline: 2.5px solid #eab308;
  transition: all 0.3s ease;
}

.direction-toggle-btn {
  color: #475569;
}
.direction-toggle-btn:hover {
  color: #1e293b;
  background: rgba(0, 0, 0, 0.04);
}
.direction-toggle-btn.active {
  background: #ffffff !important;
  color: #2563eb !important;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}
</style>
