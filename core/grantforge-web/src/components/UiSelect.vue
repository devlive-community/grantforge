<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, useId, useTemplateRef, watch, type CSSProperties } from 'vue'
import { Check, ChevronDown } from '@lucide/vue'

interface SelectOption { value: string; label: string; description?: string; disabled?: boolean }
const value = defineModel<string>({ required: true })
const { label, options, placeholder = '请选择', disabled = false, required = false, error = '', compact = false, hideLabel = false } = defineProps<{
  label: string; options: SelectOption[]; placeholder?: string; disabled?: boolean; required?: boolean; error?: string; compact?: boolean; hideLabel?: boolean
}>()
const id = useId(), trigger = useTemplateRef<HTMLButtonElement>('trigger'), panel = useTemplateRef<HTMLElement>('panel')
const open = ref(false), active = ref(-1), portal = shallowRef<HTMLElement>(), position = ref<CSSProperties>({})
const selected = computed(() => options.find(option => option.value === value.value))
const enabled = computed(() => options.map((option, index) => option.disabled ? -1 : index).filter(index => index >= 0))
let typed = '', typedAt = 0

function place() {
  if (!open.value || !trigger.value || !portal.value) return
  const rect = trigger.value.getBoundingClientRect(), margin = 12, gap = 6
  const below = window.innerHeight - rect.bottom - margin - gap, above = rect.top - margin - gap
  const upwards = below < 220 && above > below
  const height = Math.max(0, Math.min(280, upwards ? above : below))
  const width = Math.min(rect.width, window.innerWidth - margin * 2)
  const left = Math.max(margin, Math.min(rect.left, window.innerWidth - width - margin))
  const top = upwards ? rect.top - gap : rect.bottom + gap
  const parent = portal.value, modal = parent instanceof HTMLDialogElement
  const parentRect = parent.getBoundingClientRect()
  const scaleX = modal && parent.offsetWidth ? parentRect.width / parent.offsetWidth : 1
  const scaleY = modal && parent.offsetHeight ? parentRect.height / parent.offsetHeight : 1
  position.value = {
    position: modal ? 'absolute' : 'fixed',
    left: `${modal ? (left - parentRect.left) / scaleX - parent.clientLeft : left}px`,
    top: `${modal ? (top - parentRect.top) / scaleY - parent.clientTop : top}px`,
    width: `${width / scaleX}px`, maxHeight: `${height / scaleY}px`,
    transform: upwards ? 'translateY(-100%)' : undefined,
  }
}
function scrollActive() {
  void nextTick(() => panel.value?.querySelector<HTMLElement>(`[data-index="${active.value}"]`)?.scrollIntoView?.({ block: 'nearest' }))
}
function show(index = options.findIndex(option => option.value === value.value && !option.disabled)) {
  if (disabled) return
  portal.value = trigger.value?.closest('dialog') || document.body
  active.value = enabled.value.includes(index) ? index : enabled.value[0] ?? -1
  typed = ''; open.value = true; place(); scrollActive()
}
function close(commit = false) {
  const option = options[active.value]
  if (open.value && commit && option && !option.disabled) value.value = option.value
  open.value = false; typed = ''
}
function choose(index: number) {
  if (options[index]?.disabled) return
  active.value = index; close(true); trigger.value?.focus()
}
function move(distance: number) {
  const index = enabled.value.indexOf(active.value)
  active.value = enabled.value[Math.max(0, Math.min(enabled.value.length - 1, index + distance))] ?? -1
  scrollActive()
}
function typeahead(character: string) {
  const now = Date.now()
  typed = now - typedAt < 700 ? typed + character.toLocaleLowerCase() : character.toLocaleLowerCase()
  typedAt = now
  const repeat = [...typed].every(letter => letter === typed[0]), query = repeat ? character.toLocaleLowerCase() : typed
  const start = repeat ? enabled.value.indexOf(active.value) + 1 : 0
  for (let offset = 0; offset < enabled.value.length; offset++) {
    const index = enabled.value[(start + offset) % enabled.value.length]
    const option = index === undefined ? undefined : options[index]
    if (index !== undefined && option?.label.toLocaleLowerCase().startsWith(query)) { active.value = index; scrollActive(); break }
  }
}
function keydown(event: KeyboardEvent) {
  if (disabled) return
  if (event.key === 'Tab') { if (open.value) close(true); return }
  if (event.key === 'Escape') {
    if (open.value) { event.preventDefault(); event.stopPropagation(); close() }
    return
  }
  const navigation = ['ArrowDown', 'ArrowUp', 'Home', 'End', 'PageDown', 'PageUp']
  if (navigation.includes(event.key)) {
    event.preventDefault()
    const wasOpen = open.value
    if (!wasOpen) show()
    if (event.altKey && event.key === 'ArrowUp' && wasOpen) { close(true); return }
    if (event.key === 'Home' || (!wasOpen && event.key === 'ArrowUp')) active.value = enabled.value[0] ?? -1
    else if (event.key === 'End') active.value = enabled.value.at(-1) ?? -1
    else if (wasOpen) move(event.key === 'PageDown' ? 10 : event.key === 'PageUp' ? -10 : event.key === 'ArrowDown' ? 1 : -1)
    scrollActive(); return
  }
  if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); if (open.value) close(true); else show(); return }
  if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey && !event.isComposing) {
    event.preventDefault(); if (!open.value) show(); typeahead(event.key)
  }
}
function outside(event: PointerEvent) {
  if (event.target instanceof Node && !trigger.value?.contains(event.target) && !panel.value?.contains(event.target)) close(true)
}
function blur(event: FocusEvent) {
  if (open.value && (!(event.relatedTarget instanceof Node) || !panel.value?.contains(event.relatedTarget))) close(true)
}
function cleanup() {
  document.removeEventListener('pointerdown', outside, true)
  document.removeEventListener('scroll', place, true)
  document.removeEventListener('animationend', place, true)
  window.removeEventListener('resize', place)
}
watch(open, isOpen => {
  cleanup()
  if (isOpen) {
    document.addEventListener('pointerdown', outside, true)
    document.addEventListener('scroll', place, true)
    document.addEventListener('animationend', place, true)
    window.addEventListener('resize', place)
  }
}, { flush: 'sync' })
watch(() => disabled, isDisabled => { if (isDisabled) close() })
watch(() => options, () => {
  if (open.value && (!options[active.value] || options[active.value]?.disabled)) {
    active.value = enabled.value[0] ?? -1; scrollActive()
  }
})
watch(value, () => { if (open.value) { active.value = options.findIndex(option => option.value === value.value && !option.disabled); scrollActive() } })
onBeforeUnmount(cleanup)
</script>

<template>
  <div class="min-w-0">
    <label :id="`${id}-label`" :for="id" :class="hideLabel ? 'sr-only' : 'field-label'">{{ label }} <span v-if="required" aria-hidden="true" class="text-rose-500">*</span></label>
    <button
      :id="id"
      ref="trigger"
      type="button"
      role="combobox"
      :aria-labelledby="`${id}-label`"
      aria-haspopup="listbox"
      :aria-expanded="open"
      :aria-controls="open ? `${id}-list` : undefined"
      :aria-activedescendant="open && active >= 0 ? `${id}-option-${active}` : undefined"
      :aria-required="required || undefined"
      :aria-invalid="Boolean(error)"
      :aria-describedby="error ? `${id}-error` : undefined"
      :disabled="disabled"
      class="flex w-full items-center justify-between gap-3 rounded-xl border bg-surface text-left text-ink transition hover:border-brand/40 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-brand/15 disabled:cursor-not-allowed disabled:opacity-50"
      :class="[compact ? 'px-3 py-2 text-xs' : 'px-3.5 py-2.5 text-sm', open ? 'border-brand ring-3 ring-brand/10' : error ? 'border-rose-400' : 'border-line']"
      @click="open ? close() : show()"
      @keydown="keydown"
      @blur="blur"
    >
      <span class="truncate" :class="selected ? '' : 'text-muted'">{{ selected?.label || placeholder }}</span>
      <ChevronDown :size="compact ? 14 : 16" aria-hidden="true" class="shrink-0 text-muted transition-transform" :class="open ? 'rotate-180 text-brand' : ''" />
    </button>
    <p v-if="error" :id="`${id}-error`" class="mt-2 text-xs text-rose-600">{{ error }}</p>
    <Teleport v-if="open && portal" :to="portal">
      <div
        :id="`${id}-list`"
        ref="panel"
        role="listbox"
        :aria-labelledby="`${id}-label`"
        :style="position"
        class="z-[100] overflow-y-auto overscroll-contain rounded-xl border border-line bg-surface p-1.5 text-sm text-ink shadow-[0_12px_40px_-8px_#0a112b40]"
      >
        <div
          v-for="(option, index) in options"
          :id="`${id}-option-${index}`"
          :key="option.value"
          role="option"
          :data-index="index"
          :aria-selected="value === option.value"
          :aria-disabled="option.disabled || undefined"
          class="flex items-center gap-3 rounded-lg px-3 py-2.5 transition-colors"
          :class="option.disabled ? 'cursor-not-allowed opacity-40' : index === active ? 'cursor-pointer bg-brand-soft text-brand' : 'cursor-pointer hover:bg-canvas'"
          @pointerdown.prevent
          @pointermove="!option.disabled && (active = index)"
          @click="choose(index)"
        >
          <span class="min-w-0 flex-1"><span class="block truncate text-[13px] font-medium">{{ option.label }}</span><span v-if="option.description" class="mt-0.5 block truncate text-[11px] text-muted">{{ option.description }}</span></span>
          <Check v-if="value === option.value" :size="15" aria-hidden="true" class="shrink-0 text-brand" />
        </div>
        <p v-if="!options.length" class="px-3 py-4 text-center text-xs text-muted">暂无可选项</p>
      </div>
    </Teleport>
  </div>
</template>
