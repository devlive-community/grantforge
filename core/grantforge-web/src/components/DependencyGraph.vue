<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { layoutDependencies, type Edge, type Resource } from '@/lib/catalog'

const { root, edges, resources } = defineProps<{ root: string; edges: readonly Edge[]; resources: readonly Resource[] }>()
const { t } = useI18n()
const WIDTH = 176, HEIGHT = 36, COLUMN = 232, ROW = 48

const byId = computed(() => new Map(resources.map(resource => [resource.id, resource])))
const nodes = computed(() => {
  const placed = layoutDependencies(root, edges)
  const first = Math.min(...placed.map(node => node.column))
  return placed.map(node => ({ ...node, x: (node.column - first) * COLUMN, y: node.row * ROW, resource: byId.value.get(node.id) }))
})
const position = computed(() => new Map(nodes.value.map(node => [node.id, node])))
const lines = computed(() => edges.flatMap(edge => {
  const from = position.value.get(edge.resourceId), to = position.value.get(edge.dependsOnId)
  if (!from || !to) return []
  return [{ key: `${edge.resourceId}-${edge.dependsOnId}`, optional: edge.kind === 'OPTIONAL',
    x1: from.x + WIDTH, y1: from.y + HEIGHT / 2, x2: to.x, y2: to.y + HEIGHT / 2 }]
}))
const width = computed(() => Math.max(...nodes.value.map(node => node.x)) + WIDTH)
const height = computed(() => Math.max(...nodes.value.map(node => node.y)) + HEIGHT)
</script>
<template>
  <div class="overflow-x-auto">
    <p v-if="nodes.length === 1" class="py-8 text-center text-xs text-muted">{{ t('dependencies.graphEmpty') }}</p>
    <div
      v-else
      class="relative"
      :style="{ width: `${width}px`, height: `${height}px` }"
      role="img"
      :aria-label="t('dependencies.graphLabel', { count: nodes.length - 1 })"
    >
      <svg class="absolute inset-0" :width="width" :height="height" aria-hidden="true">
        <path
          v-for="line in lines"
          :key="line.key"
          :d="`M ${line.x1} ${line.y1} C ${line.x1 + 28} ${line.y1}, ${line.x2 - 28} ${line.y2}, ${line.x2} ${line.y2}`"
          fill="none"
          stroke="currentColor"
          class="text-line"
          :stroke-width="1.5"
          :stroke-dasharray="line.optional ? '4 4' : undefined"
          :data-edge="line.key"
        />
      </svg>
      <div
        v-for="node in nodes"
        :key="node.id"
        class="absolute flex items-center gap-2 rounded-lg border px-2.5 text-[11px]"
        :class="node.id === root ? 'border-brand bg-brand-soft font-semibold text-brand' : 'border-line bg-surface'"
        :style="{ left: `${node.x}px`, top: `${node.y}px`, width: `${WIDTH}px`, height: `${HEIGHT}px` }"
        :data-node="node.id"
        :data-column="node.column"
        :title="node.resource?.code"
      >
        <span class="truncate">{{ node.resource?.name ?? node.id }}</span>
      </div>
    </div>
  </div>
</template>
