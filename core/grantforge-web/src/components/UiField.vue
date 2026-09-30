<script setup lang="ts">
import { useId } from 'vue'
const value = defineModel<string>({ required: true })
const { label, type = 'text', placeholder = '', error = '', required = false, autocomplete = 'off', textarea = false, disabled = false, min = '' } = defineProps<{
  label: string; type?: string; placeholder?: string; error?: string; required?: boolean; autocomplete?: string; textarea?: boolean; disabled?: boolean; min?: string | number
}>()
const id = useId()
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
    <input
      v-else
      :id="id"
      v-model="value"
      :type="type"
      :placeholder="placeholder"
      :required="required"
      :disabled="disabled"
      :autocomplete="autocomplete"
      :min="min"
      :aria-invalid="Boolean(error)"
      :aria-describedby="error ? `${id}-error` : undefined"
      class="field"
    />
    <p v-if="error" :id="`${id}-error`" class="mt-2 text-xs text-rose-600">{{ error }}</p>
  </div>
</template>
