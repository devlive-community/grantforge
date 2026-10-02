<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, ref, shallowRef, useId } from 'vue'
import { X } from '@lucide/vue'
import { useI18n } from 'vue-i18n'

/**
 * A list of short values typed one by one, such as names or patterns. Enter or a comma adds what was typed; Backspace in
 * an empty box removes the last value. With `suggest`, values matching what is typed are offered to pick from.
 */
const values = defineModel<string[]>({ required: true })
const { label, placeholder = '', error = '', hint = '', disabled = false, suggest = undefined } = defineProps<{
  label: string; placeholder?: string; error?: string; hint?: string; disabled?: boolean; suggest?: (text: string) => Promise<string[]>
}>()
const id = useId(), { t } = useI18n()
const text = ref(''), offered = shallowRef<string[]>([]), active = ref(-1), open = ref(false)
const options = computed(() => offered.value.filter(option => !values.value.includes(option)))
let asked = 0, timer: ReturnType<typeof setTimeout> | undefined

function add(value: string) {
  const parts = value.split(',').map(part => part.trim()).filter(part => part && !values.value.includes(part))
  if (parts.length) values.value = [...values.value, ...new Set(parts)]
  text.value = ''; open.value = false; active.value = -1
}
function remove(value: string) { values.value = values.value.filter(other => other !== value) }
function ask() {
  if (!suggest) return
  clearTimeout(timer)
  const query = text.value.trim(), ticket = ++asked
  timer = setTimeout(async () => {
    try {
      const found = await suggest(query)
      if (ticket !== asked) return
      offered.value = found; open.value = found.length > 0; active.value = -1
    } catch { offered.value = []; open.value = false }
  }, 200)
}
function typed(event: Event) {
  const value = (event.target as HTMLInputElement).value
  if (value.includes(',')) add(value); else { text.value = value; ask() }
}
function key(event: KeyboardEvent) {
  if (event.key === 'Enter') {
    event.preventDefault()
    const option = options.value[active.value]
    add(open.value && option ? option : text.value)
  } else if (event.key === 'Backspace' && !text.value && values.value.length) {
    values.value = values.value.slice(0, -1)
  } else if (event.key === 'ArrowDown' && options.value.length) {
    event.preventDefault(); open.value = true; active.value = (active.value + 1) % options.value.length
  } else if (event.key === 'ArrowUp' && options.value.length) {
    event.preventDefault(); open.value = true; active.value = (active.value - 1 + options.value.length) % options.value.length
  } else if (event.key === 'Escape' && open.value) {
    event.preventDefault(); event.stopPropagation(); open.value = false
  }
}
function leave() {
  // Picking an option blurs the box first; let the click land before closing.
  setTimeout(() => { if (text.value.trim()) add(text.value); open.value = false }, 150)
}
</script>
<template>
  <div class="relative">
    <label :for="id" class="field-label">{{ label }}</label>
    <div
      class="field flex min-h-11 flex-wrap items-center gap-1.5 py-1.5"
      :class="[error ? 'border-rose-400' : '', disabled ? 'opacity-50' : '']"
    >
      <span v-for="value in values" :key="value" class="inline-flex max-w-full items-center gap-1 rounded-lg bg-brand-soft py-0.5 pl-2 pr-1 text-xs text-brand">
        <span class="truncate font-mono">{{ value }}</span>
        <button
          type="button"
          class="rounded p-0.5 hover:bg-brand/10"
          :disabled="disabled"
          :aria-label="t('controls.removeValue', { value })"
          @click="remove(value)"
        >
          <X :size="12" aria-hidden="true" />
        </button>
      </span>
      <input
        :id="id"
        :value="text"
        :placeholder="values.length ? '' : placeholder"
        :disabled="disabled"
        role="combobox"
        autocomplete="off"
        :aria-expanded="open"
        :aria-controls="`${id}-options`"
        :aria-activedescendant="open && active >= 0 ? `${id}-option-${active}` : undefined"
        :aria-invalid="Boolean(error)"
        :aria-describedby="error ? `${id}-error` : hint ? `${id}-hint` : undefined"
        class="min-w-24 flex-1 border-0 bg-transparent p-0.5 text-sm outline-none"
        @input="typed"
        @keydown="key"
        @focus="ask"
        @blur="leave"
      />
    </div>
    <ul
      v-show="open && options.length"
      :id="`${id}-options`"
      role="listbox"
      :aria-label="label"
      class="absolute inset-x-0 z-20 mt-1 max-h-56 overflow-y-auto rounded-xl border border-line bg-surface p-1 shadow-lg"
    >
      <li
        v-for="(option, index) in options"
        :id="`${id}-option-${index}`"
        :key="option"
        role="option"
        :aria-selected="index === active"
        class="cursor-pointer rounded-lg px-3 py-1.5 font-mono text-xs"
        :class="index === active ? 'bg-brand-soft text-brand' : 'hover:bg-canvas'"
        @mousedown.prevent="add(option)"
      >
        {{ option }}
      </li>
    </ul>
    <p v-if="error" :id="`${id}-error`" class="mt-2 text-xs text-rose-600">{{ error }}</p>
    <p v-else-if="hint" :id="`${id}-hint`" class="mt-1.5 text-[11px] text-muted">{{ hint }}</p>
  </div>
</template>
