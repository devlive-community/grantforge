<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import type { MenuTree } from '@/types/api'
import { flattenTree } from '@/lib/tree'
import UiCheckbox from '@/components/UiCheckbox.vue'
const { nodes, selected, depth = 0 } = defineProps<{ nodes: MenuTree[]; selected: number[]; depth?: number }>()
defineEmits<{ toggle: [id: number, checked: boolean] }>()
function state(node: MenuTree) {
  const ids = flattenTree([node]).map(item => item.id)
  const count = ids.filter(id => selected.includes(id)).length
  return { checked: count === ids.length, partial: count > 0 && count < ids.length }
}
</script>
<template>
  <div v-for="node in nodes" :key="node.id">
    <UiCheckbox
      :label="node.title"
      :checked="state(node).checked"
      :indeterminate="state(node).partial"
      class="rounded-lg px-3 py-2.5 hover:bg-canvas"
      :style="{ paddingLeft: `${12 + depth * 20}px` }"
      @update:checked="$emit('toggle', node.id, $event)"
    >
      <span class="text-[13px]" :class="node.children?.length ? 'font-medium' : 'text-muted'">{{ node.title }}</span><span class="ml-auto font-mono text-[10px] text-muted">#{{ node.id }}</span>
    </UiCheckbox>
    <TreeChoices
      v-if="node.children?.length"
      :nodes="node.children"
      :selected="selected"
      :depth="depth + 1"
      @toggle="(id, checked) => $emit('toggle', id, checked)"
    />
  </div>
</template>
