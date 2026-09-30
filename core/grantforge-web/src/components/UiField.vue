<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, useId, useTemplateRef } from 'vue'
import { ChevronDown, ChevronUp } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
const value = defineModel<string>({ required: true })
const { label, type = 'text', placeholder = '', error = '', required = false, autocomplete = 'off', textarea = false, disabled = false, min = '' } = defineProps<{
  label: string; type?: string; placeholder?: string; error?: string; required?: boolean; autocomplete?: string; textarea?: boolean; disabled?: boolean; min?: string | number
}>()
const id = useId()
const { t } = useI18n()
const input = useTemplateRef<HTMLInputElement>('input')
const atMinimum = computed(() => min !== '' && value.value !== '' && Number(value.value) <= Number(min))
function updateValue(event: Event) { value.value = (event.target as HTMLInputElement).value }
function step(direction: number) {
  if (disabled || !input.value) return
  if (direction > 0) input.value.stepUp(); else input.value.stepDown()
  value.value = input.value.value
  input.value.focus()
}
</script>
<template>
  <div>
    <label :for="id" class="field-label">{{ label }} <span v-if="required" class="text-rose-500" aria-hidden="true">*</span></label>
    <textarea
      v-if="textarea"
      :id="id"
      v-model="value"
      :placeholder="placeholder"
      :required="required"
      :disabled="disabled"
      :aria-invalid="Boolean(error)"
      :aria-describedby="error ? `${id}-error` : undefined"
      class="field min-h-24 resize-y"
    ></textarea>
    <div v-else class="relative">
      <input
        :id="id"
        ref="input"
        :value="value"
        :type="type"
        :placeholder="placeholder"
        :required="required"
        :disabled="disabled"
        :autocomplete="autocomplete"
        :min="min"
        :aria-invalid="Boolean(error)"
        :aria-describedby="error ? `${id}-error` : undefined"
        class="field"
        :class="type === 'number' ? 'pr-12' : ''"
        @input="updateValue"
      />
      <div v-if="type === 'number'" class="absolute inset-y-1 right-1 flex w-8 flex-col border-l border-line pl-1">
        <button
          type="button"
          class="flex flex-1 items-center justify-center rounded-md text-muted transition hover:bg-brand-soft hover:text-brand disabled:opacity-30"
          :disabled="disabled"
          :aria-label="t('controls.increase', { label })"
          @click="step(1)"
        >
          <ChevronUp :size="13" aria-hidden="true" />
        </button>
        <button
          type="button"
          class="flex flex-1 items-center justify-center rounded-md text-muted transition hover:bg-brand-soft hover:text-brand disabled:opacity-30"
          :disabled="disabled || atMinimum"
          :aria-label="t('controls.decrease', { label })"
          @click="step(-1)"
        >
          <ChevronDown :size="13" aria-hidden="true" />
        </button>
      </div>
    </div>
    <p v-if="error" :id="`${id}-error`" class="mt-2 text-xs text-rose-600">{{ error }}</p>
  </div>
</template>
