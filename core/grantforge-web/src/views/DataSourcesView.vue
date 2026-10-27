<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { Pencil, Plus, RefreshCw, Trash2 } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { useFieldErrors, type FieldErrors } from '@/lib/fieldErrors'
import { vPermission } from '@/lib/permission'
import { dateLabel } from '@/lib/format'
import { orgOptions } from '@/lib/org'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import UiButton from '@/components/UiButton.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiField from '@/components/UiField.vue'
import UiSelect from '@/components/UiSelect.vue'
import UiSwitch from '@/components/UiSwitch.vue'

type Source = components['schemas']['DataSourceView']
type Report = components['schemas']['DirectoryReport']
type Identity = components['schemas']['IdentitySourceResponse']
type Unit = components['schemas']['OrgUnitResponse']
const { t } = useI18n(), toast = useToast()
const sources = shallowRef<Source[]>([]), identities = shallowRef<Identity[]>([]), units = shallowRef<Unit[]>([])
const loading = ref(false), error = ref(''), saving = ref(false), busy = ref(''), formError = ref(''), choicesError = ref(''), choicesLoading = ref(false)
const editing = ref(false), target = shallowRef<Source | null>(null), deleting = shallowRef<Source | null>(null)
const reports = ref<Record<string, Report>>({})
const blank = () => ({ code: '', name: '', type: 'DINGTALK', identitySourceId: '', enabled: true, loginUpdates: true,
  corpId: '', appKey: '', secret: '', rootDepartmentId: '1', parentUnitId: '', interval: '30' })
const form = ref(blank())
const { errors, invalid } = useFieldErrors(() => form.value, problems)
const identityOptions = computed(() => identities.value.filter(source => source.type === 'OIDC').map(source => ({ value: source.id, label: source.name,
  disabled: sources.value.some(data => data.id !== target.value?.id && data.identitySourceId === source.id) })))
const parents = computed(() => [{ value: '', label: t('dataSources.noMount') }, ...orgOptions(units.value.filter(unit => !unit.directoryManaged))])

async function load() {
  loading.value = true; error.value = ''
  try { sources.value = await request<Source[]>('/api/v1/data-sources') }
  catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
async function open(source: Source | null = null) {
  target.value = source; formError.value = ''; choicesError.value = ''; errors.value = {}
  form.value = source ? { code: source.code, name: source.name, type: source.type, identitySourceId: source.identitySourceId,
    enabled: source.enabled, loginUpdates: source.loginUpdates, corpId: source.settings?.corpId ?? '', appKey: source.settings?.appKey ?? '', secret: '',
    rootDepartmentId: source.settings?.rootDepartmentId ?? '1', parentUnitId: source.parentUnitId ?? '', interval: source.syncIntervalMinutes?.toString() ?? '' } : blank()
  editing.value = true; choicesLoading.value = true
  try {
    const [auth, org] = await Promise.all([request<Identity[]>('/api/v1/identity-sources'), request<Unit[]>('/api/v1/org-units')])
    identities.value = auth; units.value = org
  } catch (reason) { choicesError.value = errorMessage(reason) } finally { choicesLoading.value = false }
}
function problems(): FieldErrors {
  const result: FieldErrors = {}, value = form.value
  if (!/^[a-z][a-z0-9-]{1,63}$/.test(value.code.trim())) result.code = t('dataSources.invalidCode')
  if (!value.name.trim()) result.name = t('dataSources.required')
  if (!value.identitySourceId) result.identitySourceId = t('dataSources.required')
  if (!value.corpId.trim()) result.corpId = t('dataSources.required')
  if (!value.appKey.trim()) result.appKey = t('dataSources.required')
  if (!target.value?.secretSet && !value.secret.trim()) result.secret = t('dataSources.required')
  if (!/^[1-9][0-9]*$/.test(value.rootDepartmentId)) result.rootDepartmentId = t('dataSources.invalidRoot')
  if (value.interval && (!/^\d+$/.test(value.interval) || Number(value.interval) < 15 || Number(value.interval) > 10080)) result.interval = t('dataSources.invalidInterval')
  return result
}
async function save() {
  if (saving.value || choicesLoading.value || choicesError.value || invalid()) return
  saving.value = true; formError.value = ''
  const value = form.value, source = target.value
  try {
    await request<Source>(source ? `/api/v1/data-sources/${encodeURIComponent(source.id)}` : '/api/v1/data-sources', {
      method: source ? 'PUT' : 'POST', body: { code: value.code.trim(), name: value.name.trim(), type: value.type, identitySourceId: value.identitySourceId,
        enabled: value.enabled, loginUpdates: value.loginUpdates, settings: { corpId: value.corpId.trim(), appKey: value.appKey.trim(), rootDepartmentId: value.rootDepartmentId },
        secret: value.secret || undefined, parentUnitId: value.parentUnitId || undefined, syncIntervalMinutes: value.interval ? Number(value.interval) : undefined } })
    editing.value = false; toast.show(t('dataSources.saved')); await load()
  } catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
async function run(source: Source, action: 'test' | 'sync') {
  if (busy.value) return
  busy.value = `${action}-${source.id}`
  try {
    const report = await request<Report | null>(`/api/v1/data-sources/${encodeURIComponent(source.id)}/${action}`, { method: 'POST' })
    if (report) reports.value[source.id] = report
    toast.show(t(action === 'test' ? 'dataSources.tested' : 'dataSources.synced')); await load()
  } catch (reason) { toast.show(errorMessage(reason), 'error'); await load() } finally { busy.value = '' }
}
async function remove() {
  const source = deleting.value
  if (!source || saving.value) return
  saving.value = true; formError.value = ''
  try {
    await request<null>(`/api/v1/data-sources/${encodeURIComponent(source.id)}`, { method: 'DELETE' })
    deleting.value = null; toast.show(t('dataSources.deleted')); await load()
  } catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <PageHeading :title="t('titles.dataSources')" :description="t('dataSources.description')">
    <UiButton variant="secondary" :loading="loading" @click="load"><RefreshCw :size="15" />{{ t('shared.refresh') }}</UiButton>
    <UiButton v-permission="'system.data-source.btn.create'" @click="open()"><Plus :size="16" />{{ t('dataSources.create') }}</UiButton>
  </PageHeading>
  <p v-if="error" class="panel p-6 text-center text-xs text-rose-600" role="alert">{{ error }}</p>
  <div v-else-if="!loading && !sources.length" class="panel p-10 text-center">
    <p class="text-sm font-semibold">{{ t('dataSources.emptyTitle') }}</p><p class="mt-2 text-xs text-muted">{{ t('dataSources.emptyText') }}</p>
  </div>
  <div v-else class="grid gap-5 xl:grid-cols-2">
    <section v-for="source in sources" :key="source.id" class="panel p-5" :data-source="source.code">
      <header class="flex items-start justify-between gap-3">
        <div><h2 class="flex items-center gap-2 text-sm font-semibold">{{ source.name }}<span class="badge bg-brand-soft text-brand">{{ t('dataSources.dingtalk') }}</span></h2><p class="mt-1 font-mono text-[11px] text-muted">{{ source.code }}</p></div>
        <StatusBadge :active="source.enabled" />
      </header>
      <dl class="mt-4 grid gap-3 text-xs sm:grid-cols-2">
        <div><dt class="text-muted">{{ t('dataSources.identity') }}</dt><dd class="mt-1">{{ source.identitySourceName || t('dataSources.identityRemoved') }}</dd></div>
        <div><dt class="text-muted">{{ t('dataSources.loginUpdates') }}</dt><dd class="mt-1">{{ source.loginUpdates ? t('dataSources.on') : t('dataSources.off') }}</dd></div>
        <div><dt class="text-muted">{{ t('dataSources.root') }}</dt><dd class="mt-1 font-mono">{{ source.settings?.rootDepartmentId }}</dd></div>
        <div><dt class="text-muted">{{ t('dataSources.interval') }}</dt><dd class="mt-1">{{ source.syncIntervalMinutes ? t('dataSources.every', { minutes: source.syncIntervalMinutes }) : t('dataSources.manual') }}</dd></div>
        <div class="sm:col-span-2"><dt class="text-muted">{{ t('dataSources.lastSync') }}</dt><dd class="mt-1 break-words">{{ source.lastSyncedAt ? dateLabel(source.lastSyncedAt) : t('dataSources.neverSynced') }}<p v-if="source.lastSyncSummary" class="mt-1 text-muted">{{ source.lastSyncSummary }}</p></dd></div>
      </dl>
      <div v-if="reports[source.id]" class="mt-4 rounded-lg bg-brand-soft p-3 text-xs" data-report>
        {{ t('dataSources.counts', reports[source.id]!) }}
        <ul v-if="reports[source.id]?.problems?.length" class="mt-2 space-y-1 text-amber-700"><li v-for="problem in reports[source.id]?.problems" :key="problem">{{ problem }}</li></ul>
      </div>
      <div class="mt-4 flex flex-wrap justify-end gap-2 border-t border-line pt-3">
        <UiButton
          v-permission="'system.data-source.btn.update'"
          variant="secondary"
          :disabled="!!busy"
          :loading="busy === `test-${source.id}`"
          @click="run(source, 'test')"
        >
          {{ t('dataSources.test') }}
        </UiButton>
        <UiButton
          v-permission="'system.data-source.btn.sync'"
          variant="secondary"
          :disabled="!!busy || !source.enabled"
          :loading="busy === `sync-${source.id}`"
          @click="run(source, 'sync')"
        >
          {{ t('dataSources.sync') }}
        </UiButton>
        <button
          v-permission="'system.data-source.btn.update'"
          type="button"
          class="table-action"
          :aria-label="t('dataSources.editNamed', { name: source.name })"
          @click="open(source)"
        >
          <Pencil :size="14" />
        </button>
        <button
          v-permission="'system.data-source.btn.delete'"
          type="button"
          class="table-action hover:text-rose-600"

          :aria-label="t('dataSources.deleteNamed', { name: source.name })"
          @click="deleting = source; formError = ''"
        >
          <Trash2 :size="14" />
        </button>
      </div>
    </section>
  </div>
  <UiDialog v-model="editing" :title="target ? t('dataSources.edit') : t('dataSources.create')" :busy="saving" wide>
    <form id="data-source" class="space-y-5" novalidate @submit.prevent="save">
      <p v-if="choicesError" role="alert" class="text-xs text-rose-600">{{ choicesError }}</p>
      <p v-else-if="choicesLoading" class="text-xs text-muted">{{ t('controls.suggestLoading') }}</p>
      <p class="text-xs text-muted">{{ t('dataSources.matchingHint') }}</p>
      <p v-if="target?.initialized" class="rounded-lg bg-brand-soft p-3 text-xs">{{ t('dataSources.frozenHint') }}</p>
      <div class="grid gap-5 sm:grid-cols-2">
        <UiField
          v-model="form.code"
          :label="t('dataSources.code')"
          :disabled="!!target"
          required
          :error="errors.code"
        />
        <UiField v-model="form.name" :label="t('dataSources.name')" required :error="errors.name" />
        <UiSelect v-model="form.type" :label="t('dataSources.type')" :options="[{ value: 'DINGTALK', label: t('dataSources.dingtalk') }]" :disabled="!!target" />
        <UiSelect
          v-model="form.identitySourceId"
          :label="t('dataSources.identity')"
          :options="identityOptions"
          :disabled="target?.initialized || choicesLoading"
          required
          :error="errors.identitySourceId"
        />
      </div>
      <p v-if="!choicesLoading && !choicesError && !identityOptions.length" class="text-xs text-amber-700">{{ t('dataSources.noOidc') }}</p>
      <div class="grid gap-5 sm:grid-cols-2">
        <UiField
          v-model="form.corpId"
          :label="t('dataSources.corpId')"
          :disabled="target?.initialized"
          required
          :error="errors.corpId"
        />
        <UiField
          v-model="form.appKey"
          label="AppKey"
          :disabled="target?.initialized"
          required
          :error="errors.appKey"
        />
        <UiField
          v-model="form.secret"
          label="AppSecret"
          type="password"
          autocomplete="new-password"
          :placeholder="target?.secretSet ? t('dataSources.keepSecret') : ''"
          :required="!target?.secretSet"
          :error="errors.secret"
        />
        <UiField
          v-model="form.rootDepartmentId"
          :label="t('dataSources.root')"
          :disabled="target?.initialized"
          required
          :error="errors.rootDepartmentId"
        />
        <UiSelect v-model="form.parentUnitId" :label="t('dataSources.mount')" :options="parents" :disabled="target?.initialized || choicesLoading" />
        <UiField v-model="form.interval" :label="t('dataSources.interval')" :placeholder="t('dataSources.manual')" :error="errors.interval" />
      </div>
      <UiSwitch v-model="form.enabled" :label="t('dataSources.enabled')" />
      <UiSwitch v-model="form.loginUpdates" :label="t('dataSources.loginUpdates')" />
      <p class="text-xs text-muted">{{ t('dataSources.loginHint') }}</p>
      <p v-if="formError" role="alert" class="text-xs text-rose-600">{{ formError }}</p>
    </form>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="editing = false">{{ t('shared.cancel') }}</UiButton><UiButton type="submit" form="data-source" :loading="saving" :disabled="choicesLoading || !!choicesError">{{ t('identitySources.save') }}</UiButton></template>
  </UiDialog>
  <UiDialog :model-value="!!deleting" :title="t('dataSources.delete')" :busy="saving" @update:model-value="deleting = null">
    <p class="text-sm">{{ t('dataSources.deleteConfirm', { name: deleting?.name }) }}</p><p v-if="formError" role="alert" class="mt-3 text-xs text-rose-600">{{ formError }}</p>
    <template #footer><UiButton variant="secondary" @click="deleting = null">{{ t('shared.cancel') }}</UiButton><UiButton variant="danger" :loading="saving" @click="remove">{{ t('dataSources.delete') }}</UiButton></template>
  </UiDialog>
</template>
