<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { ChevronLeft, ChevronRight } from '@lucide/vue'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import UiSelect from '@/components/UiSelect.vue'
const page = defineModel<number>('page', { required: true })
const { size, total, pages, loading = false } = defineProps<{ size: number; total: number; pages: number; loading?: boolean }>()
const emit = defineEmits<{ size: [value: number] }>()
const { t } = useI18n()
const sizes = computed(() => [10, 20, 50].map(value => ({ value: String(value), label: t('pagination.perPageOption', { size: value }) })))
// aria-disabled instead of disabled: a disabled button drops keyboard focus while a page loads.
const atFirst = () => page.value <= 1 || loading
const atLast = () => page.value >= pages || loading
function previous() { if (!atFirst()) page.value-- }
function next() { if (!atLast()) page.value++ }
</script>
<template>
  <div class="flex flex-wrap items-center justify-between gap-3 border-t border-line px-5 py-4 text-xs text-muted">
    <div class="flex items-center gap-3">
      <i18n-t keypath="pagination.total" tag="span"><template #count><strong class="font-medium text-ink">{{ total }}</strong></template></i18n-t><UiSelect
        :model-value="String(size)"
        :label="t('pagination.perPage')"
        :options="sizes"
        compact
        hide-label
        class="w-32"
        @update:model-value="emit('size', Number($event))"
      />
    </div>
    <div class="flex items-center gap-2">
      <span class="mr-2">{{ Math.min(page, Math.max(1, pages)) }} / {{ Math.max(1, pages) }}</span><button
        type="button"
        :aria-label="t('pagination.previous')"
        class="icon-button size-8 border border-line aria-disabled:cursor-not-allowed aria-disabled:opacity-30"
        :aria-disabled="atFirst()"
        @click="previous"
      >
        <ChevronLeft :size="15" />
      </button><button
        type="button"
        :aria-label="t('pagination.next')"
        class="icon-button size-8 border border-line aria-disabled:cursor-not-allowed aria-disabled:opacity-30"
        :aria-disabled="atLast()"
        @click="next"
      >
        <ChevronRight :size="15" />
      </button>
    </div>
  </div>
</template>
