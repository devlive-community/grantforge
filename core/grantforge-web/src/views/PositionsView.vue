<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onWatcherCleanup, ref, shallowRef, watch } from 'vue'
import { AlertTriangle, Pencil, Plus, RefreshCw, Search, Trash2, UsersRound } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { useFieldErrors, type FieldErrors } from '@/lib/fieldErrors'
import { vPermission } from '@/lib/permission'
import { initials } from '@/lib/format'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import DataTable from '@/components/DataTable.vue'
import PageControls from '@/components/PageControls.vue'
import UiButton from '@/components/UiButton.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiField from '@/components/UiField.vue'

type Position = components['schemas']['PositionResponse']
type PositionPage = components['schemas']['PageResultPositionResponse']
type HolderPage = components['schemas']['PageResultMemberResponse']

/** How many holders the holder dialog lists. */
const LIST_SIZE = 50
const { t } = useI18n(), toast = useToast()
const page = ref(1), size = ref(20), revision = ref(0), search = ref(''), text = ref('')
const result = shallowRef<PositionPage>({ items: [], page: 1, size: 20, total: 0 })
const loading = ref(false), error = ref('')
const pages = computed(() => Math.ceil(result.value.total / size.value))
const columns = computed(() => [{ key: 'position', label: t('positions.columnPosition') }, { key: 'description', label: t('positions.descriptionLabel') },
  { key: 'sortOrder', label: t('positions.columnOrder') }, { key: 'holders', label: t('positions.columnHolders') },
  { key: 'actions', label: t('shared.actions'), class: 'text-right' }])
const dialog = ref<'create' | 'edit' | 'delete' | 'holders' | null>(null), target = shallowRef<Position | null>(null)
const saving = ref(false), formError = ref(''), form = ref({ code: '', name: '', description: '', sortOrder: '0' })
const { errors: fieldErrors, invalid } = useFieldErrors(() => form.value, problems)
const holders = shallowRef<HolderPage>({ items: [], page: 1, size: LIST_SIZE, total: 0 })

watch([page, size, text, revision], ([current, limit, query]) => {
  const controller = new AbortController()
  onWatcherCleanup(() => controller.abort())
  loading.value = true; error.value = ''
  void request<PositionPage>('/api/v1/positions', { query: { q: query || undefined, page: current, size: limit }, signal: controller.signal })
    .then(found => { if (!controller.signal.aborted) result.value = found })
    .catch(reason => { if (!controller.signal.aborted) error.value = errorMessage(reason) })
    .finally(() => { if (!controller.signal.aborted) loading.value = false })
}, { immediate: true })
// Searching waits for a pause in typing, so each keystroke does not query the server.
let pending: ReturnType<typeof setTimeout> | undefined
watch(search, value => { clearTimeout(pending); pending = setTimeout(() => { page.value = 1; text.value = value.trim() }, 300) })

function refresh() { revision.value++ }
function setSize(value: number) { page.value = 1; size.value = value }
function open(kind: 'create' | 'edit' | 'delete', position: Position | null = null) {
  target.value = position; formError.value = ''; fieldErrors.value = {}; dialog.value = kind
  form.value = { code: position?.code ?? '', name: position?.name ?? '', description: position?.description ?? '', sortOrder: String(position?.sortOrder ?? 0) }
}
async function openHolders(position: Position) {
  target.value = position; formError.value = ''; fieldErrors.value = {}; holders.value = { items: [], page: 1, size: LIST_SIZE, total: 0 }; dialog.value = 'holders'
  try { holders.value = await request<HolderPage>(`/api/v1/positions/${encodeURIComponent(position.id)}/holders`, { query: { page: 1, size: LIST_SIZE } }) }
  catch (reason) { formError.value = errorMessage(reason) }
}
async function run(action: () => Promise<unknown>, done: string) {
  if (saving.value) return
  saving.value = true; formError.value = ''; fieldErrors.value = {}
  try { await action(); dialog.value = null; toast.show(done); refresh() }
  catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
function problems(): FieldErrors {
  const found: FieldErrors = {}
  const order = Number(form.value.sortOrder)
  if (!form.value.code.trim()) found.code = t('positions.enterCode')
  if (!form.value.name.trim()) found.name = t('positions.enterName')
  if (!Number.isInteger(order) || order < 0) found.sortOrder = t('positions.invalidOrder')
  return found
}
function save() {
  formError.value = ''
  if (invalid()) return
  const body = { code: form.value.code, name: form.value.name, description: form.value.description, sortOrder: Number(form.value.sortOrder) }
  if (dialog.value === 'create') void run(() => request<Position>('/api/v1/positions', { method: 'POST', body }), t('positions.created'))
  else if (target.value) {
    const id = target.value.id
    void run(() => request<Position>(`/api/v1/positions/${encodeURIComponent(id)}`, { method: 'PUT', body }), t('positions.saved'))
  }
}
function remove() {
  const position = target.value
  if (position) void run(() => request<null>(`/api/v1/positions/${encodeURIComponent(position.id)}`, { method: 'DELETE' }), t('positions.deleted'))
}
</script>
<template>
  <PageHeading :title="t('titles.positions')" :description="t('positions.description')" :badge="t('positions.count', { count: result.total })"><UiButton variant="secondary" :disabled="loading" @click="refresh"><RefreshCw :size="15" />{{ t('shared.refresh') }}</UiButton><UiButton v-permission="'system.position.btn.create'" @click="open('create')"><Plus :size="16" />{{ t('positions.create') }}</UiButton></PageHeading>
  <section class="panel overflow-hidden">
    <div class="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4"><div><h2 class="text-sm font-semibold">{{ t('positions.all') }}</h2><p class="mt-1 text-[11px] text-muted">{{ t('positions.allCaption') }}</p></div><div class="flex w-full items-center gap-2 rounded-xl border border-line bg-canvas/40 px-3 sm:w-64"><Search :size="15" class="text-muted" /><input v-model="search" :aria-label="t('positions.search')" :placeholder="t('positions.searchPlaceholder')" class="w-full bg-transparent py-2.5 text-xs outline-none" /></div></div>
    <DataTable
      :rows="result.items"
      :columns="columns"
      :loading="loading"
      :error="error"
      :empty-title="t('positions.emptyTitle')"
      :empty-description="t('positions.emptyDescription')"
      @retry="refresh"
    >
      <template #position="{ row }"><div class="flex items-center gap-3"><span class="flex size-9 shrink-0 items-center justify-center rounded-xl bg-brand-soft text-[11px] font-semibold text-brand">{{ initials(row.name) }}</span><div><p class="font-medium">{{ row.name }}</p><p class="mt-1 font-mono text-[10px] text-muted">{{ row.code }}</p></div></div></template>
      <template #description="{ row }"><span class="text-xs text-muted">{{ row.description || '—' }}</span></template>
      <template #sortOrder="{ row }"><span class="font-mono text-xs">{{ row.sortOrder }}</span></template>
      <template #holders="{ row }"><span class="text-xs">{{ row.holders }}</span></template>
      <template #actions="{ row }">
        <div class="flex justify-end gap-0.5">
          <button
            type="button"
            class="table-action"
            :aria-label="t('positions.holdersNamed', { name: row.name })"
            :data-tooltip="t('positions.holders')"
            @click="openHolders(row)"
          >
            <UsersRound :size="14" />
          </button>
          <button
            v-permission="'system.position.btn.edit'"
            type="button"
            class="table-action"
            :aria-label="t('positions.editNamed', { name: row.name })"
            @click="open('edit', row)"
          >
            <Pencil :size="14" />
          </button>
          <button
            v-permission="'system.position.btn.delete'"
            type="button"
            class="table-action hover:text-rose-600"
            :aria-label="t('positions.deleteNamed', { name: row.name })"
            @click="open('delete', row)"
          >
            <Trash2 :size="14" />
          </button>
        </div>
      </template>
    </DataTable><PageControls
      v-model:page="page"
      :size="size"
      :total="result.total"
      :pages="pages"
      :loading="loading"
      @size="setSize"
    />
  </section>
  <UiDialog :model-value="dialog === 'create' || dialog === 'edit'" :title="dialog === 'edit' ? t('positions.editTitle') : t('positions.create')" :busy="saving" @update:model-value="dialog = null">
    <form id="position-form" class="space-y-5" novalidate @submit.prevent="save">
      <div class="grid gap-5 sm:grid-cols-2">
        <UiField
          v-model="form.name"
          :label="t('positions.name')"
          :placeholder="t('positions.namePlaceholder')"
          required
          :error="fieldErrors.name"
        /><UiField
          v-model="form.code"
          :label="t('positions.code')"
          :placeholder="t('positions.codePlaceholder')"
          required
          :error="fieldErrors.code"
        />
      </div>
      <UiField
        v-model="form.sortOrder"
        :label="t('positions.sortOrder')"
        type="number"
        min="0"
        :error="fieldErrors.sortOrder"
      />
      <UiField v-model="form.description" :label="t('positions.descriptionLabel')" textarea />
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="dialog = null">{{ t('shared.cancel') }}</UiButton><UiButton type="submit" form="position-form" :loading="saving">{{ dialog === 'edit' ? t('tenants.save') : t('positions.create') }}</UiButton></template>
  </UiDialog>
  <UiDialog :model-value="dialog === 'delete'" :title="t('positions.deleteTitle')" :busy="saving" @update:model-value="dialog = null">
    <div class="flex gap-4"><span class="flex size-11 shrink-0 items-center justify-center rounded-xl bg-rose-50 text-rose-500"><AlertTriangle :size="22" /></span><p class="text-sm leading-6">{{ t('positions.deleteWarning', { name: target?.name, count: target?.holders ?? 0 }) }}</p></div><p v-if="formError" class="mt-4 text-xs text-rose-600" role="alert">{{ formError }}</p>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="dialog = null">{{ t('shared.cancel') }}</UiButton><UiButton variant="danger" :loading="saving" @click="remove">{{ t('positions.deleteTitle') }}</UiButton></template>
  </UiDialog>
  <UiDialog :model-value="dialog === 'holders'" :title="t('positions.holdersTitle', { name: target?.name })" @update:model-value="dialog = null">
    <p v-if="formError" class="text-xs text-rose-600" role="alert">{{ formError }}</p>
    <ul v-else-if="holders.items.length" class="max-h-72 divide-y divide-line overflow-y-auto rounded-xl border border-line">
      <li v-for="holder in holders.items" :key="holder.accountId" class="flex items-center justify-between gap-3 px-4 py-2.5"><span class="text-xs font-medium">{{ holder.displayName || holder.username }}</span><span class="font-mono text-[10px] text-muted">{{ holder.username }}</span></li>
    </ul>
    <p v-else class="py-6 text-center text-xs text-muted">{{ t('positions.noHolders') }}</p>
    <p v-if="holders.total > holders.items.length" class="mt-2 text-[11px] text-muted">{{ t('positions.holdersLimited', { total: holders.total, shown: holders.items.length }) }}</p>
  </UiDialog>
</template>
