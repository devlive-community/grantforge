<script setup lang="ts">
import { ChevronLeft, ChevronRight } from '@lucide/vue'
const page = defineModel<number>('page', { required: true })
const { size, total, pages, loading = false } = defineProps<{ size: number; total: number; pages: number; loading?: boolean }>()
const emit = defineEmits<{ size: [value: number] }>()
function changeSize(event: Event) { emit('size', Number((event.target as HTMLSelectElement).value)) }
</script>
<template>
  <div class="flex flex-wrap items-center justify-between gap-3 border-t border-line px-5 py-4 text-xs text-muted">
    <div class="flex items-center gap-3"><span>共 <strong class="font-medium text-ink">{{ total }}</strong> 条记录</span><select aria-label="每页记录数" :value="size" class="rounded-lg border border-line bg-surface p-1.5" @change="changeSize"><option :value="10">10 条 / 页</option><option :value="20">20 条 / 页</option><option :value="50">50 条 / 页</option></select></div>
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
