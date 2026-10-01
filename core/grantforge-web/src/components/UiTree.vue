<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, nextTick, ref, useTemplateRef, watch } from 'vue'
import { ChevronRight } from '@lucide/vue'
import { useI18n } from 'vue-i18n'

/** A node of the tree; children are shown in the given order. */
export interface TreeNode { id: string; label: string; hint?: string; children: TreeNode[] }
interface Row { node: TreeNode; level: number; parent: string | null }

const { nodes, label } = defineProps<{ nodes: TreeNode[]; label: string }>()
const selected = defineModel<string | null>('selected', { default: null })
const { t } = useI18n()
const collapsed = ref(new Set<string>()), focused = ref<string | null>(null)
const items = useTemplateRef<HTMLElement[]>('items')

/** Visible rows in display order: collapsed nodes hide their descendants. */
const rows = computed(() => {
  const result: Row[] = []
  const visit = (list: TreeNode[], level: number, parent: string | null) => {
    for (const node of list) {
      result.push({ node, level, parent })
      if (node.children.length && !collapsed.value.has(node.id)) visit(node.children, level + 1, node.id)
    }
  }
  visit(nodes, 1, null)
  return result
})
// Exactly one row is reachable with Tab: the focused one, else the selected one, else the first.
const tabStop = computed(() => [focused.value, selected.value, rows.value[0]?.node.id]
  .find(id => id != null && rows.value.some(row => row.node.id === id)) ?? null)

function setCollapsed(id: string, value: boolean) {
  const next = new Set(collapsed.value)
  if (value) next.add(id); else next.delete(id)
  collapsed.value = next
}
function toggle(row: Row) { if (row.node.children.length) setCollapsed(row.node.id, !collapsed.value.has(row.node.id)) }
async function focus(id: string | null | undefined) {
  if (!id) return
  focused.value = id
  await nextTick()
  items.value?.find(item => item.dataset.id === id)?.focus()
}
function select(id: string) { selected.value = id; void focus(id) }
function keydown(event: KeyboardEvent, row: Row, index: number) {
  const list = rows.value, expanded = row.node.children.length > 0 && !collapsed.value.has(row.node.id)
  const handlers: Record<string, () => void> = {
    ArrowDown: () => void focus(list[index + 1]?.node.id),
    ArrowUp: () => void focus(list[index - 1]?.node.id),
    Home: () => void focus(list[0]?.node.id),
    End: () => void focus(list.at(-1)?.node.id),
    ArrowRight: () => { if (!row.node.children.length) return; if (expanded) void focus(row.node.children[0]?.id); else setCollapsed(row.node.id, false) },
    ArrowLeft: () => { if (expanded) setCollapsed(row.node.id, true); else void focus(row.parent) },
    Enter: () => select(row.node.id),
    ' ': () => select(row.node.id),
  }
  const handler = handlers[event.key]
  if (!handler) return
  event.preventDefault()
  handler()
}
// Keep the selection visible: expand its ancestors when it changes from outside.
watch([selected, () => nodes], ([id]) => {
  if (!id) return
  const path: string[] = []
  const find = (list: TreeNode[]): boolean => list.some(node => node.id === id || (find(node.children) && path.push(node.id) > 0))
  if (find(nodes) && path.some(ancestor => collapsed.value.has(ancestor))) {
    collapsed.value = new Set([...collapsed.value].filter(ancestor => !path.includes(ancestor)))
  }
}, { immediate: true })
</script>
<template>
  <ul role="tree" :aria-label="label" class="space-y-0.5">
    <li
      v-for="(row, index) in rows"
      :key="row.node.id"
      ref="items"
      role="treeitem"
      :data-id="row.node.id"
      :aria-level="row.level"
      :aria-selected="selected === row.node.id"
      :aria-expanded="row.node.children.length ? !collapsed.has(row.node.id) : undefined"
      :tabindex="tabStop === row.node.id ? 0 : -1"
      class="flex cursor-pointer items-center gap-1.5 rounded-lg py-2 pr-3 text-[13px] outline-none transition focus-visible:ring-2 focus-visible:ring-brand/40"
      :class="selected === row.node.id ? 'bg-brand-soft text-brand' : 'hover:bg-canvas'"
      :style="{ paddingLeft: `${8 + (row.level - 1) * 18}px` }"
      @click="select(row.node.id)"
      @keydown="keydown($event, row, index)"
    >
      <button
        v-if="row.node.children.length"
        type="button"
        tabindex="-1"
        class="flex size-5 shrink-0 items-center justify-center rounded text-muted hover:text-current"
        :aria-label="collapsed.has(row.node.id) ? t('tree.expand', { name: row.node.label }) : t('tree.collapse', { name: row.node.label })"
        @click.stop="toggle(row)"
      >
        <ChevronRight :size="14" class="transition-transform" :class="collapsed.has(row.node.id) ? '' : 'rotate-90'" />
      </button><span v-else class="size-5 shrink-0"></span>
      <span class="truncate">{{ row.node.label }}</span><span v-if="row.node.hint" class="ml-auto shrink-0 font-mono text-[10px] text-muted">{{ row.node.hint }}</span>
    </li>
  </ul>
</template>
