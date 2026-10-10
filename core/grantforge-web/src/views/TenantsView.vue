<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onWatcherCleanup, ref, shallowRef, watch } from 'vue'
import { AlertTriangle, Pencil, Plus, Power, PowerOff, RefreshCw, Search } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { vPermission } from '@/lib/permission'
import { dateLabel, initials } from '@/lib/format'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import DataTable from '@/components/DataTable.vue'
import PageControls from '@/components/PageControls.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import UiButton from '@/components/UiButton.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiField from '@/components/UiField.vue'

type Tenant = components['schemas']['TenantResponse']
type TenantPage = components['schemas']['PageResultTenantResponse']

const { t } = useI18n(), toast = useToast()
const page = ref(1), size = ref(20), revision = ref(0), search = ref(''), text = ref('')
const result = shallowRef<TenantPage>({ items: [], page: 1, size: 20, total: 0 })
const loading = ref(false), error = ref('')
const pages = computed(() => Math.ceil(result.value.total / size.value))
const columns = computed(() => [{ key: 'tenant', label: t('tenants.columnTenant') }, { key: 'status', label: t('shared.status') },
  { key: 'accounts', label: t('tenants.columnAccounts') }, { key: 'createdAt', label: t('shared.createdAt') },
  { key: 'actions', label: t('shared.actions'), class: 'text-right' }])
const createOpen = ref(false), editOpen = ref(false), suspendOpen = ref(false), saving = ref(false), formError = ref(''), fieldErrors = ref<Record<string, string>>({})
const target = shallowRef<Tenant | null>(null)
const form = ref({ code: '', name: '', adminUsername: '', adminDisplayName: '', adminPassword: '', confirm: '' })
const newName = ref('')

watch([page, size, text, revision], ([current, limit, query]) => {
  const controller = new AbortController()
  onWatcherCleanup(() => controller.abort())
  loading.value = true; error.value = ''
  void request<TenantPage>('/api/v1/tenants', { query: { q: query || undefined, page: current, size: limit }, signal: controller.signal })
    .then(found => { if (!controller.signal.aborted) result.value = found })
    .catch(reason => { if (!controller.signal.aborted) error.value = errorMessage(reason) })
    .finally(() => { if (!controller.signal.aborted) loading.value = false })
}, { immediate: true })

// Searching waits for a pause in typing, so each keystroke does not query the server.
let pending: ReturnType<typeof setTimeout> | undefined
watch(search, value => { clearTimeout(pending); pending = setTimeout(() => { page.value = 1; text.value = value.trim() }, 300) })

function refresh() { revision.value++ }
function setSize(value: number) { page.value = 1; size.value = value }
function openCreate() {
  form.value = { code: '', name: '', adminUsername: '', adminDisplayName: '', adminPassword: '', confirm: '' }
  formError.value = ''; fieldErrors.value = {}; createOpen.value = true
}
function openEdit(tenant: Tenant) { target.value = tenant; newName.value = tenant.name; formError.value = ''; fieldErrors.value = {}; editOpen.value = true }
function openSuspend(tenant: Tenant) { target.value = tenant; formError.value = ''; suspendOpen.value = true }
function missing() {
  const value = form.value
  // Every failed field shows its message at once, rather than only the first.
  if (!value.code.trim()) fieldErrors.value.code = t('tenants.enterCode')
  if (!value.name.trim()) fieldErrors.value.name = t('tenants.enterName')
  if (!value.adminUsername.trim()) fieldErrors.value.adminUsername = t('tenants.enterAdmin')
  if (!value.adminPassword) fieldErrors.value.adminPassword = t('tenants.enterPassword')
  else if (value.adminPassword !== value.confirm) fieldErrors.value.confirm = t('tenants.passwordMismatch')
}
async function create() {
  if (saving.value) return
  formError.value = ''
  fieldErrors.value = {}
  missing()
  if (Object.keys(fieldErrors.value).length) return
  saving.value = true
  const { code, name, adminUsername, adminDisplayName, adminPassword } = form.value
  try {
    await request<Tenant>('/api/v1/tenants', { method: 'POST', body: { code, name, adminUsername, adminDisplayName, adminPassword } })
    createOpen.value = false; toast.show(t('tenants.created')); refresh()
  } catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
async function save() {
  const tenant = target.value
  if (!tenant || saving.value) return
  formError.value = ''
  fieldErrors.value = {}
  if (!newName.value.trim()) { fieldErrors.value.newName = t('tenants.enterName'); return }
  saving.value = true
  try {
    await request<Tenant>(`/api/v1/tenants/${encodeURIComponent(tenant.id)}`, { method: 'PUT', body: { name: newName.value } })
    editOpen.value = false; toast.show(t('tenants.saved')); refresh()
  } catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
async function suspend() {
  const tenant = target.value
  if (!tenant || saving.value) return
  saving.value = true; formError.value = ''
  try {
    await request<Tenant>(`/api/v1/tenants/${encodeURIComponent(tenant.id)}/suspend`, { method: 'POST' })
    suspendOpen.value = false; toast.show(t('tenants.suspended')); refresh()
  } catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
async function activate(tenant: Tenant) {
  try {
    await request<Tenant>(`/api/v1/tenants/${encodeURIComponent(tenant.id)}/activate`, { method: 'POST' })
    toast.show(t('tenants.activated')); refresh()
  } catch (reason) { toast.show(errorMessage(reason), 'error') }
}
</script>
<template>
  <PageHeading :title="t('titles.tenants')" :description="t('tenants.description')" :badge="t('tenants.count', { count: result.total })"><UiButton variant="secondary" :disabled="loading" @click="refresh"><RefreshCw :size="15" />{{ t('shared.refresh') }}</UiButton><UiButton v-permission="'platform.tenant.btn.create'" @click="openCreate"><Plus :size="16" />{{ t('tenants.create') }}</UiButton></PageHeading>
  <section class="panel overflow-hidden">
    <div class="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4"><div><h2 class="text-sm font-semibold">{{ t('tenants.all') }}</h2><p class="mt-1 text-[11px] text-muted">{{ t('tenants.allCaption') }}</p></div><div class="flex w-full items-center gap-2 rounded-xl border border-line bg-canvas/40 px-3 sm:w-64"><Search :size="15" class="text-muted" /><input v-model="search" :aria-label="t('tenants.search')" :placeholder="t('tenants.searchPlaceholder')" class="w-full bg-transparent py-2.5 text-xs outline-none" /></div></div>
    <DataTable
      :rows="result.items"
      :columns="columns"
      :loading="loading"
      :error="error"
      :empty-title="t('tenants.emptyTitle')"
      :empty-description="t('tenants.emptyDescription')"
      @retry="refresh"
    >
      <template #tenant="{ row }"><div class="flex items-center gap-3"><span class="flex size-9 shrink-0 items-center justify-center rounded-xl bg-brand-soft text-[11px] font-semibold text-brand">{{ initials(row.name) }}</span><div><p class="font-medium">{{ row.name }}<span v-if="row.platform" class="badge ml-2 bg-brand-soft text-brand">{{ t('tenants.platform') }}</span></p><p class="mt-1 font-mono text-[10px] text-muted">{{ row.code }}</p></div></div></template>
      <template #status="{ row }"><StatusBadge :active="row.status === 'ACTIVE'" /></template>
      <template #accounts="{ row }"><span class="text-xs">{{ row.accounts }}</span></template>
      <template #createdAt="{ row }"><span class="text-xs text-muted">{{ dateLabel(row.createdAt) }}</span></template>
      <template #actions="{ row }">
        <div class="flex justify-end gap-1">
          <button
            v-permission="'platform.tenant.btn.edit'"
            type="button"
            class="table-action"
            :aria-label="t('tenants.editNamed', { name: row.name })"
            @click="openEdit(row)"
          >
            <Pencil :size="14" />{{ t('tenants.edit') }}
          </button><button
            v-if="row.status === 'ACTIVE' && !row.platform"
            v-permission="'platform.tenant.btn.status'"
            type="button"
            class="table-action hover:text-rose-600"
            :aria-label="t('tenants.suspendNamed', { name: row.name })"
            @click="openSuspend(row)"
          >
            <PowerOff :size="14" />{{ t('tenants.suspend') }}
          </button><button
            v-else-if="row.status !== 'ACTIVE'"
            v-permission="'platform.tenant.btn.status'"
            type="button"
            class="table-action"
            :aria-label="t('tenants.activateNamed', { name: row.name })"
            @click="activate(row)"
          >
            <Power :size="14" />{{ t('tenants.activate') }}
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
  <UiDialog v-model="createOpen" :title="t('tenants.create')" :description="t('tenants.createDescription')" :busy="saving">
    <form id="create-tenant" class="space-y-5" novalidate @submit.prevent="create">
      <div class="grid gap-5 sm:grid-cols-2">
        <UiField
          v-model="form.code"
          :label="t('tenants.code')"
          :placeholder="t('tenants.codePlaceholder')"
          required
          :error="fieldErrors.code"
        /><UiField
          v-model="form.name"
          :label="t('tenants.name')"
          :placeholder="t('tenants.namePlaceholder')"
          required
          :error="fieldErrors.name"
        />
      </div>
      <p class="text-[11px] text-muted">{{ t('tenants.codeHint') }}</p>
      <fieldset class="space-y-5 border-t border-line pt-5">
        <legend class="field-label">{{ t('tenants.adminSection') }}</legend>
        <div class="grid gap-5 sm:grid-cols-2">
          <UiField
            v-model="form.adminUsername"
            :label="t('tenants.adminUsername')"
            autocomplete="off"
            required
            :error="fieldErrors.adminUsername"
          /><UiField v-model="form.adminDisplayName" :label="t('tenants.adminDisplayName')" autocomplete="off" />
        </div>
        <div class="grid gap-5 sm:grid-cols-2">
          <UiField
            v-model="form.adminPassword"
            :label="t('tenants.adminPassword')"
            type="password"
            autocomplete="new-password"
            required
            :error="fieldErrors.adminPassword"
          /><UiField
            v-model="form.confirm"
            :label="t('tenants.adminPasswordConfirm')"
            type="password"
            autocomplete="new-password"
            required
            :error="fieldErrors.confirm"
          />
        </div>
      </fieldset>
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="createOpen = false">{{ t('shared.cancel') }}</UiButton><UiButton type="submit" form="create-tenant" :loading="saving">{{ t('tenants.create') }}</UiButton></template>
  </UiDialog>
  <UiDialog v-model="editOpen" :title="t('tenants.editTitle')" :busy="saving">
    <form id="edit-tenant" class="space-y-5" novalidate @submit.prevent="save">
      <dl><dt class="field-label">{{ t('tenants.code') }}</dt><dd class="font-mono text-sm">{{ target?.code }}</dd></dl>
      <UiField v-model="newName" :label="t('tenants.name')" required :error="fieldErrors.newName" />
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="editOpen = false">{{ t('shared.cancel') }}</UiButton><UiButton type="submit" form="edit-tenant" :loading="saving">{{ t('tenants.save') }}</UiButton></template>
  </UiDialog>
  <UiDialog v-model="suspendOpen" :title="t('tenants.suspendTitle')" :busy="saving">
    <div class="flex gap-4"><span class="flex size-11 shrink-0 items-center justify-center rounded-xl bg-rose-50 text-rose-500"><AlertTriangle :size="22" /></span><div><p>{{ t('tenants.suspendConfirm', { name: target?.name }) }}</p><p class="mt-2 text-xs leading-6 text-muted">{{ t('tenants.suspendWarning') }}</p></div></div><p v-if="formError" class="mt-4 text-xs text-rose-600" role="alert">{{ formError }}</p>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="suspendOpen = false">{{ t('shared.cancel') }}</UiButton><UiButton variant="danger" :loading="saving" @click="suspend">{{ t('tenants.suspend') }}</UiButton></template>
  </UiDialog>
</template>
