<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { CheckCheck, RefreshCw, Search } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { vPermission } from '@/lib/permission'
import { dateLabel } from '@/lib/format'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import DataTable from '@/components/DataTable.vue'
import UiButton from '@/components/UiButton.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiField from '@/components/UiField.vue'
import UiSelect from '@/components/UiSelect.vue'

type Endpoint = components['schemas']['ApiEndpointResponse']

const { t } = useI18n(), toast = useToast()
const endpoints = shallowRef<Endpoint[]>([]), loading = ref(false), error = ref('')
const query = ref(''), access = ref(''), state = ref('active'), confirming = ref(false), saving = ref(false)
// Changes are confirmed by platform administrators, who maintain the shared catalog.

// Literal keys, so the message checker sees every one in use.
const accessKeys = { PUBLIC: 'apis.accessPublic', AUTHENTICATED: 'apis.accessAuthenticated', PERMISSION: 'apis.accessPermission' } as const
const changeKeys = { ADDED: 'apis.changeAdded', CHANGED: 'apis.changeChanged', REMOVED: 'apis.changeRemoved' } as const
const changeColors = { ADDED: 'bg-emerald-50 text-emerald-700', CHANGED: 'bg-amber-50 text-amber-700', REMOVED: 'bg-rose-50 text-rose-700' } as const
const methodColors: Record<string, string> = { GET: 'text-sky-600', POST: 'text-emerald-600', PUT: 'text-amber-600', DELETE: 'text-rose-600' }
const accessOptions = computed(() => [{ value: '', label: t('apis.accessAll') },
  ...(['PUBLIC', 'AUTHENTICATED', 'PERMISSION'] as const).map(value => ({ value, label: t(accessKeys[value]) }))])
const stateOptions = computed(() => [{ value: 'active', label: t('apis.stateActive') }, { value: 'pending', label: t('apis.statePending') },
  { value: 'removed', label: t('apis.stateRemoved') }, { value: '', label: t('apis.stateAll') }])
const columns = computed(() => [
  { key: 'route', label: t('apis.columnRoute') }, { key: 'access', label: t('apis.columnAccess') },
  { key: 'handler', label: t('apis.columnHandler') }, { key: 'change', label: t('apis.columnChange') },
])

const pending = computed(() => endpoints.value.filter(endpoint => endpoint.change))
const permissions = computed(() => new Set(endpoints.value.filter(endpoint => endpoint.active && endpoint.permission).map(endpoint => endpoint.permission)).size)
const rows = computed(() => {
  const text = query.value.trim().toLowerCase()
  return endpoints.value.filter(endpoint =>
    (!text || [endpoint.pathPattern, endpoint.permission ?? '', endpoint.handler].some(value => value.toLowerCase().includes(text)))
    && (!access.value || endpoint.access === access.value)
    && (state.value === '' || (state.value === 'active' && endpoint.active) || (state.value === 'pending' && !!endpoint.change)
      || (state.value === 'removed' && !endpoint.active)))
})
const pendingShown = computed(() => rows.value.filter(endpoint => endpoint.change))

async function load() {
  loading.value = true; error.value = ''
  try { endpoints.value = await request<Endpoint[]>('/api/v1/api-endpoints') } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
async function review() {
  if (saving.value) return
  saving.value = true
  try {
    const result = await request<{ reviewed: number }>('/api/v1/api-endpoints/review', { method: 'POST', body: { endpointIds: pendingShown.value.map(endpoint => endpoint.id) } })
    confirming.value = false
    toast.show(t('apis.reviewed', { count: result.reviewed }))
    await load()
  } catch (reason) { toast.show(errorMessage(reason), 'error') } finally { saving.value = false }
}
onMounted(load)
</script>
<template>
  <PageHeading :title="t('titles.apis')" :description="t('apis.description')">
    <UiButton variant="secondary" :loading="loading" @click="load"><RefreshCw :size="15" />{{ t('apis.refresh') }}</UiButton>
    <UiButton v-permission="'platform.api.btn.review'" :disabled="!pendingShown.length" @click="confirming = true"><CheckCheck :size="16" />{{ t('apis.review', { count: pendingShown.length }) }}</UiButton>
  </PageHeading>
  <div class="mb-6 grid gap-4 sm:grid-cols-3">
    <div class="panel p-5"><p class="text-xs text-muted">{{ t('apis.totalEndpoints') }}</p><p class="mt-2 text-[26px] font-semibold">{{ endpoints.filter(endpoint => endpoint.active).length }}</p></div>
    <div class="panel p-5"><p class="text-xs text-muted">{{ t('apis.totalPermissions') }}</p><p class="mt-2 text-[26px] font-semibold">{{ permissions }}</p></div>
    <div class="panel p-5"><p class="text-xs text-muted">{{ t('apis.totalPending') }}</p><p class="mt-2 text-[26px] font-semibold" :class="pending.length ? 'text-amber-600' : ''">{{ pending.length }}</p></div>
  </div>
  <section class="panel overflow-hidden">
    <div class="flex flex-wrap items-end gap-3 border-b border-line p-4">
      <div class="relative min-w-60 flex-1"><Search :size="15" class="pointer-events-none absolute bottom-3 left-3 text-muted" /><UiField v-model="query" :label="t('apis.search')" :placeholder="t('apis.searchPlaceholder')" class="[&_input]:pl-9" /></div>
      <div class="w-44"><UiSelect v-model="access" :label="t('apis.access')" :options="accessOptions" /></div>
      <div class="w-44"><UiSelect v-model="state" :label="t('apis.state')" :options="stateOptions" /></div>
    </div>
    <DataTable
      :rows="rows"
      :columns="columns"
      :loading="loading"
      :error="error"
      :empty-title="t('apis.emptyTitle')"
      :empty-description="t('apis.emptyDescription')"
      @retry="load"
    >
      <template #route="{ row }"><p class="font-mono text-xs"><span class="mr-2 inline-block w-12 font-semibold" :class="methodColors[row.httpMethod] ?? 'text-muted'">{{ row.httpMethod }}</span><span :class="row.active ? '' : 'text-muted line-through'">{{ row.pathPattern }}</span></p></template>
      <template #access="{ row }"><p class="text-xs">{{ t(accessKeys[row.access]) }}</p><p v-if="row.permission" class="mt-1 font-mono text-[10px] text-brand">{{ row.permission }}</p></template>
      <template #handler="{ row }"><span class="font-mono text-[11px] text-muted">{{ row.handler }}</span></template>
      <template #change="{ row }"><span v-if="row.change" class="badge" :class="changeColors[row.change]">{{ t(changeKeys[row.change]) }}</span><span v-else class="text-[11px] text-muted">{{ t('apis.seen', { time: dateLabel(row.lastSeenAt) }) }}</span><p v-if="row.changedAt" class="mt-1 text-[10px] text-muted">{{ dateLabel(row.changedAt) }}</p></template>
    </DataTable>
  </section>
  <UiDialog v-model="confirming" :title="t('apis.reviewTitle')" :busy="saving">
    <p>{{ t('apis.reviewConfirm', { count: pendingShown.length }) }}</p><p class="mt-2 text-xs leading-6 text-muted">{{ t('apis.reviewHint') }}</p>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="confirming = false">{{ t('shared.cancel') }}</UiButton><UiButton :loading="saving" @click="review">{{ t('apis.reviewAction') }}</UiButton></template>
  </UiDialog>
</template>
