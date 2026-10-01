<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onWatcherCleanup, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { AlertTriangle, LogOut, RefreshCw } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { agentLabel, dateLabel, initials } from '@/lib/format'
import { useAuth } from '@/stores/auth'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import DataTable from '@/components/DataTable.vue'
import PageControls from '@/components/PageControls.vue'
import UiButton from '@/components/UiButton.vue'
import UiDialog from '@/components/UiDialog.vue'

type Session = components['schemas']['SessionResponse']
type SessionPage = components['schemas']['PageResultSessionResponse']

const { t } = useI18n(), toast = useToast(), auth = useAuth(), router = useRouter()
const page = ref(1), size = ref(20), revision = ref(0)
const result = shallowRef<SessionPage>({ items: [], page: 1, size: 20, total: 0 })
const loading = ref(false), error = ref('')
const pages = computed(() => Math.ceil(result.value.total / size.value))
const target = shallowRef<Session | null>(null), confirmOpen = ref(false), saving = ref(false), formError = ref('')
const columns = computed(() => [{ key: 'user', label: t('sessions.columnUser') }, { key: 'client', label: t('sessions.columnClient') },
  { key: 'signedInAt', label: t('sessions.columnSignedIn') }, { key: 'lastSeenAt', label: t('sessions.columnLastSeen') },
  { key: 'actions', label: t('shared.actions'), class: 'text-right' }])
const name = (session: Session) => session.displayName || session.username

watch([page, size, revision], ([current, limit]) => {
  const controller = new AbortController()
  onWatcherCleanup(() => controller.abort())
  loading.value = true; error.value = ''
  void request<SessionPage>('/api/v1/sessions', { query: { page: current, size: limit }, signal: controller.signal }).then(found => {
    if (controller.signal.aborted) return
    // Ending sessions can empty the last page; step back to the new last page.
    const last = Math.max(1, Math.ceil(found.total / limit))
    if (current > last) { page.value = last; return }
    result.value = found
  }).catch(reason => { if (!controller.signal.aborted) error.value = errorMessage(reason) })
    .finally(() => { if (!controller.signal.aborted) loading.value = false })
}, { immediate: true })

function refresh() { revision.value++ }
function setSize(value: number) { page.value = 1; size.value = value }
function openEnd(session: Session) { target.value = session; formError.value = ''; confirmOpen.value = true }
async function end() {
  const session = target.value
  if (!session || saving.value) return
  saving.value = true; formError.value = ''
  try {
    await request<null>(`/api/v1/sessions/${encodeURIComponent(session.id)}`, { method: 'DELETE' })
    confirmOpen.value = false
    if (session.current) { auth.reset(); void router.replace('/auth/login'); return }
    toast.show(t('sessions.ended')); refresh()
  } catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
</script>
<template>
  <PageHeading :title="t('titles.sessions')" :description="t('sessions.description')" :badge="t('sessions.count', { count: result.total })"><UiButton variant="secondary" :disabled="loading" @click="refresh"><RefreshCw :size="15" />{{ t('shared.refresh') }}</UiButton></PageHeading>
  <section class="panel overflow-hidden">
    <div class="border-b border-line px-5 py-4"><h2 class="text-sm font-semibold">{{ t('sessions.all') }}</h2><p class="mt-1 text-[11px] text-muted">{{ t('sessions.allCaption') }}</p></div>
    <DataTable
      :rows="result.items"
      :columns="columns"
      :loading="loading"
      :error="error"
      :empty-title="t('sessions.emptyTitle')"
      :empty-description="t('sessions.emptyDescription')"
      @retry="refresh"
    >
      <template #user="{ row }"><div class="flex items-center gap-3"><span class="flex size-9 shrink-0 items-center justify-center rounded-xl bg-brand-soft text-[11px] font-semibold text-brand">{{ initials(name(row)) }}</span><div><p class="font-medium">{{ name(row) }}<span v-if="row.current" class="badge ml-2 bg-emerald-50 text-emerald-700">{{ t('sessions.current') }}</span></p><p class="mt-1 font-mono text-[10px] text-muted">{{ row.username }}</p></div></div></template>
      <template #client="{ row }"><p class="text-xs">{{ agentLabel(row.userAgent) }}</p><p class="mt-1 font-mono text-[10px] text-muted">{{ row.clientIp || '—' }}</p></template>
      <template #signedInAt="{ row }"><span class="text-xs text-muted">{{ dateLabel(row.signedInAt) }}</span></template>
      <template #lastSeenAt="{ row }"><span class="text-xs text-muted">{{ dateLabel(row.lastSeenAt) }}</span></template>
      <template #actions="{ row }"><div class="flex justify-end"><button type="button" class="table-action hover:text-rose-600" :aria-label="t('sessions.endNamed', { name: name(row) })" @click="openEnd(row)"><LogOut :size="14" />{{ t('sessions.end') }}</button></div></template>
    </DataTable><PageControls
      v-model:page="page"
      :size="size"
      :total="result.total"
      :pages="pages"
      :loading="loading"
      @size="setSize"
    />
  </section>
  <UiDialog v-model="confirmOpen" :title="t('sessions.end')" :busy="saving">
    <div class="flex gap-4"><span class="flex size-11 shrink-0 items-center justify-center rounded-xl bg-rose-50 text-rose-500"><AlertTriangle :size="22" /></span><div><p>{{ t('sessions.endConfirm', { name: target ? name(target) : '', client: agentLabel(target?.userAgent) }) }}</p><p class="mt-2 text-xs leading-6 text-muted">{{ target?.current ? t('sessions.endCurrentWarning') : t('sessions.endWarning') }}</p></div></div><p v-if="formError" class="mt-4 text-xs text-rose-600" role="alert">{{ formError }}</p>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="confirmOpen = false">{{ t('shared.cancel') }}</UiButton><UiButton variant="danger" :loading="saving" @click="end">{{ t('sessions.end') }}</UiButton></template>
  </UiDialog>
</template>
