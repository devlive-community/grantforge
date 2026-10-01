<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts" generic="T extends { id: number | string }">
import { Inbox, RefreshCw } from '@lucide/vue'
import UiButton from './UiButton.vue'
import type { TableColumn } from '@/types/api'
import { cellLabel } from '@/lib/format'
import { useI18n } from 'vue-i18n'
const { rows, columns, loading = false, error = '', emptyTitle = '', emptyDescription = '' } = defineProps<{
  rows: T[]; columns: TableColumn[]; loading?: boolean; error?: string; emptyTitle?: string; emptyDescription?: string
}>()
defineEmits<{ retry: [] }>()
const { t } = useI18n()
function value(row: T, key: string) { return cellLabel((row as unknown as Record<string, unknown>)[key]) }
</script>
<template>
  <div v-if="error" class="flex min-h-64 flex-col items-center justify-center gap-3 p-6 text-center" role="alert">
    <div class="rounded-full bg-rose-50 p-3 text-rose-500"><Inbox :size="24" /></div><p class="font-medium">{{ t('common.loadFailed') }}</p><p class="max-w-md text-sm text-muted">{{ error }}</p><UiButton variant="secondary" @click="$emit('retry')"><RefreshCw :size="15" />{{ t('common.reload') }}</UiButton>
  </div>
  <div v-else class="overflow-x-auto" :aria-busy="loading">
    <table class="w-full min-w-[650px] border-collapse text-left">
      <thead>
        <tr class="border-b border-line bg-canvas/45">
          <th
            v-for="column in columns"
            :key="column.key"
            scope="col"
            class="px-5 py-3.5 text-[11px] font-semibold tracking-wide text-muted"
            :class="column.class"
          >
            {{ column.label }}
          </th>
        </tr>
      </thead>
      <tbody v-if="loading"><tr v-for="index in 5" :key="index" class="border-b border-line last:border-0"><td v-for="column in columns" :key="column.key" class="px-5 py-5"><span class="block h-4 w-3/4 animate-pulse rounded bg-line"></span></td></tr></tbody>
      <tbody v-else><tr v-for="row in rows" :key="row.id" class="border-b border-line transition last:border-0 hover:bg-canvas/50"><td v-for="column in columns" :key="column.key" class="px-5 py-4" :class="column.class"><slot :name="column.key" :row="row">{{ value(row, column.key) }}</slot></td></tr></tbody>
    </table>
    <div v-if="!loading && !rows.length" class="flex min-h-64 flex-col items-center justify-center px-6 text-center"><div class="mb-4 rounded-2xl bg-canvas p-4 text-muted"><Inbox :size="28" /></div><p class="font-medium">{{ emptyTitle || t('common.emptyTitle') }}</p><p class="mt-2 text-xs text-muted">{{ emptyDescription || t('common.emptyDescription') }}</p></div>
  </div>
</template>
