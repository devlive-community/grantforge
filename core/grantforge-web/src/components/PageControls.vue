<script setup lang="ts">
import { ChevronLeft, ChevronRight } from '@lucide/vue'
import UiSelect from '@/components/UiSelect.vue'
const page = defineModel<number>('page', { required: true })
const { size, total, pages, loading = false } = defineProps<{ size: number; total: number; pages: number; loading?: boolean }>()
const emit = defineEmits<{ size: [value: number] }>()
const sizes = [10, 20, 50].map(value => ({ value: String(value), label: `${value} 条 / 页` }))
</script>
<template>
  <div class="flex flex-wrap items-center justify-between gap-3 border-t border-line px-5 py-4 text-xs text-muted">
    <div class="flex items-center gap-3">
      <span>共 <strong class="font-medium text-ink">{{ total }}</strong> 条记录</span><UiSelect
        :model-value="String(size)"
        label="每页记录数"
        :options="sizes"
        :disabled="loading"
        compact
        hide-label
        class="w-32"
        @update:model-value="emit('size', Number($event))"
      />
    </div>
    <div class="flex items-center gap-2">
      <span class="mr-2">{{ Math.min(page, Math.max(1, pages)) }} / {{ Math.max(1, pages) }}</span><button
        type="button"
        aria-label="上一页"
        class="icon-button size-8 border border-line disabled:opacity-30"
        :disabled="page <= 1 || loading"
        @click="page--"
      >
        <ChevronLeft :size="15" />
      </button><button
        type="button"
        aria-label="下一页"
        class="icon-button size-8 border border-line disabled:opacity-30"
        :disabled="page >= pages || loading"
        @click="page++"
      >
        <ChevronRight :size="15" />
      </button>
    </div>
  </div>
</template>
