<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, nextTick, ref, useTemplateRef, watch } from 'vue'
import { ChevronRight } from '@lucide/vue'
import { useI18n } from 'vue-i18n'

/** A node of the tree; children are shown in the given order. A badge is a short tag such as the node's kind. */
export interface TreeNode { id: string; label: string; hint?: string; badge?: string; children: TreeNode[] }
/** Where a dragged node lands relative to the node it is dropped on. */
export type DropPosition = 'before' | 'after' | 'inside'
interface Row { node: TreeNode; level: number; parent: string | null }

const { nodes, label, draggable = false, canDrop = () => true, filter = '' } = defineProps<{
  nodes: TreeNode[]; label: string; draggable?: boolean
  /**
   * Text to look for in each node's label and hint, ignoring case. Matching nodes are shown with every ancestor that
   * leads to them, expanded whatever was collapsed, with the matched text marked; dragging is off meanwhile, since a
   * filtered tree hides the neighbours a drop would land between.
   */
  filter?: string
  /** Whether `source` may land at `position` of `target`; drops it refuses show no marker and do nothing. */
  canDrop?: (source: string, target: string, position: DropPosition) => boolean
}>()
const emit = defineEmits<{ drop: [source: string, target: string, position: DropPosition] }>()
const selected = defineModel<string | null>('selected', { default: null })
const dragging = ref<string | null>(null), marker = ref<{ id: string; position: DropPosition } | null>(null)
const { t } = useI18n()
const collapsed = ref(new Set<string>()), focused = ref<string | null>(null)
const items = useTemplateRef<HTMLElement[]>('items')

const query = computed(() => filter.trim().toLowerCase())
const matches = (node: TreeNode) => [node.label, node.hint ?? ''].some(text => text.toLowerCase().includes(query.value))
/** While filtering: the nodes to show, which are the matches and the ancestors that lead to them. */
const shown = computed(() => {
  if (!query.value) return null
  const result = new Set<string>()
  const visit = (node: TreeNode): boolean => {
    // Every child is visited, so each match below a node is found, not only the first.
    const below = node.children.map(visit).some(Boolean)
    if (below || matches(node)) result.add(node.id)
    return result.has(node.id)
  }
  nodes.forEach(visit)
  return result
})
const canDrag = computed(() => draggable && !query.value)
/** Visible rows in display order: collapsed nodes hide their descendants, and a filter hides what does not lead to a match. */
const rows = computed(() => {
  const result: Row[] = [], only = shown.value
  const visit = (list: TreeNode[], level: number, parent: string | null) => {
    for (const node of list) {
      if (only && !only.has(node.id)) continue
      result.push({ node, level, parent })
      if (node.children.length && (only || !collapsed.value.has(node.id))) visit(node.children, level + 1, node.id)
    }
  }
  visit(nodes, 1, null)
  return result
})
/** Whether a row shows its children: always while filtering, since the filter decides what is shown. */
const open = (node: TreeNode) => node.children.length > 0 && (query.value !== '' || !collapsed.value.has(node.id))
/** A text cut around each occurrence of the filter, so the occurrences can be marked. */
function pieces(text: string): { text: string; hit: boolean }[] {
  const needle = query.value
  if (!needle) return [{ text, hit: false }]
  const result: { text: string; hit: boolean }[] = [], lower = text.toLowerCase()
  let from = 0
  for (let at = lower.indexOf(needle); at >= 0; at = lower.indexOf(needle, from)) {
    if (at > from) result.push({ text: text.slice(from, at), hit: false })
    result.push({ text: text.slice(at, at + needle.length), hit: true })
    from = at + needle.length
  }
  if (from < text.length) result.push({ text: text.slice(from), hit: false })
  return result
}
// Exactly one row is reachable with Tab: the focused one, else the selected one, else the first.
const tabStop = computed(() => [focused.value, selected.value, rows.value[0]?.node.id]
  .find(id => id != null && rows.value.some(row => row.node.id === id)) ?? null)

function setCollapsed(id: string, value: boolean) {
  const next = new Set(collapsed.value)
  if (value) next.add(id); else next.delete(id)
  collapsed.value = next
}
function toggle(row: Row) { if (row.node.children.length && !query.value) setCollapsed(row.node.id, !collapsed.value.has(row.node.id)) }
async function focus(id: string | null | undefined) {
  if (!id) return
  focused.value = id
  await nextTick()
  items.value?.find(item => item.dataset.id === id)?.focus()
}
function select(id: string) { selected.value = id; void focus(id) }
function keydown(event: KeyboardEvent, row: Row, index: number) {
  const list = rows.value, expanded = open(row.node)
  const handlers: Record<string, () => void> = {
    ArrowDown: () => void focus(list[index + 1]?.node.id),
    ArrowUp: () => void focus(list[index - 1]?.node.id),
    Home: () => void focus(list[0]?.node.id),
    End: () => void focus(list.at(-1)?.node.id),
    ArrowRight: () => { if (!row.node.children.length) return; if (expanded) void focus(list[index + 1]?.node.id); else setCollapsed(row.node.id, false) },
    ArrowLeft: () => { if (expanded && !query.value) setCollapsed(row.node.id, true); else void focus(row.parent) },
    Enter: () => select(row.node.id),
    ' ': () => select(row.node.id),
  }
  const handler = handlers[event.key]
  if (!handler) return
  event.preventDefault()
  handler()
}
// Dragging (mouse only; keyboard users move nodes with the page's own buttons): the upper and lower quarter of
// a row place the node before or after it, the middle puts it inside.
function dragStart(event: DragEvent, row: Row) {
  if (!canDrag.value) return
  dragging.value = row.node.id
  event.dataTransfer?.setData('text/plain', row.node.id)
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}
function positionOf(event: DragEvent): DropPosition {
  const box = (event.currentTarget as HTMLElement).getBoundingClientRect(), offset = event.clientY - box.top
  return offset < box.height / 4 ? 'before' : offset > box.height * 3 / 4 ? 'after' : 'inside'
}
function dragOver(event: DragEvent, row: Row) {
  const source = dragging.value
  if (!source || source === row.node.id) return
  const position = positionOf(event)
  if (!canDrop(source, row.node.id, position)) { marker.value = null; return }
  event.preventDefault()
  marker.value = { id: row.node.id, position }
}
function dropOn(event: DragEvent, row: Row) {
  event.preventDefault()
  const source = dragging.value, target = marker.value
  dragEnd()
  if (source && target?.id === row.node.id) emit('drop', source, row.node.id, target.position)
}
function dragEnd() { dragging.value = null; marker.value = null }
// Keep the selection visible: expand its ancestors when it changes from outside.
watch([selected, () => nodes], ([id]) => {
  if (!id) return
  const path: string[] = []
  const find = (list: TreeNode[]): boolean => list.some(node => node.id === id || (find(node.children) && path.push(node.id) > 0))
  if (find(nodes) && path.some(ancestor => collapsed.value.has(ancestor))) {
    collapsed.value = new Set([...collapsed.value].filter(ancestor => !path.includes(ancestor)))
  }
  // The tree may scroll inside its panel, so bring the selection into view there too.
  void nextTick(() => items.value?.find(item => item.dataset.id === id)?.scrollIntoView?.({ block: 'nearest' }))
}, { immediate: true })
</script>
<template>
  <p v-if="query && !rows.length" class="px-2 py-10 text-center text-xs text-muted" role="status">{{ t('tree.noMatch', { query: filter.trim() }) }}</p>
  <ul v-else role="tree" :aria-label="label" class="space-y-0.5">
    <li
      v-for="(row, index) in rows"
      :key="row.node.id"
      ref="items"
      role="treeitem"
      :data-id="row.node.id"
      :aria-level="row.level"
      :aria-selected="selected === row.node.id"
      :aria-expanded="row.node.children.length ? open(row.node) : undefined"
      :tabindex="tabStop === row.node.id ? 0 : -1"
      class="flex cursor-pointer items-center gap-1.5 rounded-lg py-2 pr-3 text-[13px] outline-none transition"
      :class="[selected === row.node.id ? 'bg-brand-soft text-brand' : 'hover:bg-canvas', dragging === row.node.id ? 'opacity-50' : '',
               marker?.id === row.node.id ? { before: 'shadow-[inset_0_2px_0_var(--color-brand)]', after: 'shadow-[inset_0_-2px_0_var(--color-brand)]', inside: 'outline-2 outline-brand/50' }[marker.position] : '']"
      :style="{ paddingLeft: `${8 + (row.level - 1) * 18}px` }"
      :draggable="canDrag"
      :data-drop="marker?.id === row.node.id ? marker.position : undefined"
      @click="select(row.node.id)"
      @keydown="keydown($event, row, index)"
      @dragstart="dragStart($event, row)"
      @dragover="dragOver($event, row)"
      @dragleave="marker?.id === row.node.id ? marker = null : undefined"
      @drop="dropOn($event, row)"
      @dragend="dragEnd"
    >
      <button
        v-if="row.node.children.length"
        type="button"
        tabindex="-1"
        :disabled="query !== ''"
        class="flex size-5 shrink-0 items-center justify-center rounded text-muted hover:text-current"
        :aria-label="open(row.node) ? t('tree.collapse', { name: row.node.label }) : t('tree.expand', { name: row.node.label })"
        @click.stop="toggle(row)"
      >
        <ChevronRight :size="14" class="transition-transform" :class="open(row.node) ? 'rotate-90' : ''" />
      </button><span v-else class="size-5 shrink-0"></span>
      <span v-if="row.node.badge" class="shrink-0 rounded bg-canvas px-1.5 py-0.5 text-[10px] font-medium text-muted">{{ row.node.badge }}</span><span class="truncate"><template v-for="(piece, at) in pieces(row.node.label)" :key="at"><mark v-if="piece.hit" class="rounded-sm bg-amber-200/70 text-current dark:bg-amber-400/30">{{ piece.text }}</mark><template v-else>{{ piece.text }}</template></template></span><span v-if="row.node.hint" class="ml-auto shrink-0 font-mono text-[10px] text-muted"><template v-for="(piece, at) in pieces(row.node.hint)" :key="at"><mark v-if="piece.hit" class="rounded-sm bg-amber-200/70 text-current dark:bg-amber-400/30">{{ piece.text }}</mark><template v-else>{{ piece.text }}</template></template></span>
    </li>
  </ul>
</template>
