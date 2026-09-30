<script setup lang="ts">
import type { MenuTree } from '@/types/api'
import { flattenTree } from '@/lib/tree'
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
    <label class="flex cursor-pointer items-center gap-3 rounded-lg px-3 py-2.5 transition hover:bg-canvas" :style="{ paddingLeft: `${12 + depth * 20}px` }">
      <input
        type="checkbox"
        :checked="state(node).checked"
        :indeterminate="state(node).partial"
        class="size-4 accent-brand"
        @change="$emit('toggle', node.id, ($event.target as HTMLInputElement).checked)"
      />
      <span class="text-[13px]" :class="node.children?.length ? 'font-medium' : 'text-muted'">{{ node.title }}</span><span class="ml-auto font-mono text-[10px] text-muted">#{{ node.id }}</span>
    </label>
    <TreeChoices
      v-if="node.children?.length"
      :nodes="node.children"
      :selected="selected"
      :depth="depth + 1"
      @toggle="(id, checked) => $emit('toggle', id, checked)"
    />
  </div>
</template>
