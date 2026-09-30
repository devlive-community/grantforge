<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { useId } from 'vue'
import { Check, Minus } from '@lucide/vue'

const checked = defineModel<boolean>('checked', { required: true })
const { label, indeterminate = false, disabled = false } = defineProps<{
  label: string
  indeterminate?: boolean
  disabled?: boolean
}>()
const id = useId()
</script>

<template>
  <label
    :for="id"
    class="flex items-center gap-3 text-sm transition"
    :class="disabled ? 'cursor-not-allowed text-muted opacity-50' : 'cursor-pointer text-ink'"
  >
    <span class="relative flex size-4.5 shrink-0 items-center justify-center">
      <input
        :id="id"
        v-model="checked"
        type="checkbox"
        :aria-label="label"
        :aria-checked="indeterminate ? 'mixed' : checked"
        :indeterminate="indeterminate"
        :disabled="disabled"
        class="peer m-0 size-full appearance-none rounded-[5px] border shadow-xs transition focus-visible:ring-3 focus-visible:ring-brand/25 enabled:hover:border-brand disabled:cursor-not-allowed"
        :class="checked || indeterminate ? 'border-brand bg-brand' : 'border-line bg-surface'"
      />
      <Minus
        v-if="indeterminate"
        :size="13"
        :stroke-width="3"
        class="pointer-events-none absolute text-white"
        aria-hidden="true"
      />
      <Check
        v-else-if="checked"
        :size="13"
        :stroke-width="3"
        class="pointer-events-none absolute text-white"
        aria-hidden="true"
      />
    </span>
    <slot><span>{{ label }}</span></slot>
  </label>
</template>
