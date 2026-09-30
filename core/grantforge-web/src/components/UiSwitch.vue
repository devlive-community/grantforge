<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { useId } from 'vue'
const checked = defineModel<boolean>({ required: true })
const { label, disabled = false } = defineProps<{ label: string; disabled?: boolean }>()
const id = useId()
</script>
<template>
  <div class="flex items-center gap-3">
    <button
      :id="id"
      type="button"
      role="switch"
      :aria-checked="checked"
      :aria-labelledby="`${id}-label`"
      :disabled="disabled"
      class="inline-flex h-6 w-11 shrink-0 items-center rounded-full border p-0.5 transition-colors focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-brand/20 disabled:opacity-50"
      :class="checked ? 'border-brand bg-brand' : 'border-line bg-muted/25'"
      @click="checked = !checked"
    >
      <span aria-hidden="true" class="size-[18px] rounded-full bg-white shadow-sm transition-transform" :class="checked ? 'translate-x-5' : 'translate-x-0'"></span>
    </button>
    <label :id="`${id}-label`" :for="id" class="cursor-pointer text-xs font-medium text-ink">{{ label }}</label>
  </div>
</template>
