<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { AlertTriangle, Pencil, Plus, RefreshCw, Scale, Trash2 } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { useFieldErrors, type FieldErrors } from '@/lib/fieldErrors'
import { vPermission } from '@/lib/permission'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import UiButton from '@/components/UiButton.vue'
import UiCheckbox from '@/components/UiCheckbox.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiField from '@/components/UiField.vue'
import UiSelect from '@/components/UiSelect.vue'
import UiSwitch from '@/components/UiSwitch.vue'

type Constraint = components['schemas']['SodConstraintResponse']
type Conflict = components['schemas']['SodConflictResponse']
type Role = components['schemas']['RoleResponse']
type Mode = Constraint['mode']

// Separation of duties (D-73): roles nobody may hold together, and who holds them together now.
const { t } = useI18n(), toast = useToast()
const constraints = shallowRef<Constraint[]>([]), conflicts = shallowRef<Conflict[]>([]), roles = shallowRef<Role[]>([])
const loading = ref(false), error = ref('')
const editing = ref<'create' | 'edit' | null>(null), target = shallowRef<Constraint | null>(null), deleting = shallowRef<Constraint | null>(null)
const saving = ref(false), formError = ref(''), filter = ref('')
const form = ref({ code: '', name: '', description: '', roleIds: [] as string[], maxRoles: '1', mode: 'ENFORCE' as Mode, enabled: true })
const { errors: fieldErrors, invalid } = useFieldErrors(() => form.value, problems)
const modes = computed(() => [{ value: 'ENFORCE', label: t('sod.enforce'), description: t('sod.enforceText') },
  { value: 'REPORT', label: t('sod.report'), description: t('sod.reportText') }])
const shown = computed(() => {
  const query = filter.value.trim().toLowerCase()
  return roles.value.filter(role => !query || role.name.toLowerCase().includes(query) || role.code.toLowerCase().includes(query))
})

async function load() {
  loading.value = true; error.value = ''
  try {
    ;[constraints.value, conflicts.value] = await Promise.all([request<Constraint[]>('/api/v1/sod-constraints'), request<Conflict[]>('/api/v1/sod-conflicts')])
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
async function loadRoles() {
  if (!roles.value.length) roles.value = await request<Role[]>('/api/v1/roles').catch(() => [] as Role[])
}
async function openCreate() {
  form.value = { code: '', name: '', description: '', roleIds: [], maxRoles: '1', mode: 'ENFORCE', enabled: true }
  target.value = null; formError.value = ''; fieldErrors.value = {}; filter.value = ''; editing.value = 'create'
  await loadRoles()
}
async function openEdit(constraint: Constraint) {
  form.value = { code: constraint.code, name: constraint.name, description: constraint.description ?? '', roleIds: constraint.roles.map(role => role.id),
    maxRoles: String(constraint.maxRoles), mode: constraint.mode, enabled: constraint.enabled }
  target.value = constraint; formError.value = ''; fieldErrors.value = {}; filter.value = ''; editing.value = 'edit'
  await loadRoles()
}
function toggle(id: string, on: boolean) {
  const ids = form.value.roleIds.filter(item => item !== id)
  form.value.roleIds = on ? [...ids, id] : ids
}
function problems(): FieldErrors {
  const value = form.value
  const found: FieldErrors = {}
  if (!value.code.trim()) found.code = t('sod.enterCode')
  if (!value.name.trim()) found.name = t('sod.enterName')
  if (value.roleIds.length < 2) found.roleIds = t('sod.pickRoles')
  else {
    // The limit is measured against the chosen roles, so it only means something once there are two of them.
    const max = Number(value.maxRoles)
    if (!Number.isInteger(max) || max < 1 || max >= value.roleIds.length) found.maxRoles = t('sod.enterMax', { count: value.roleIds.length - 1 })
  }
  return found
}
async function save() {
  if (saving.value) return
  formError.value = ''
  if (invalid()) return
  const value = form.value
  const body = { code: value.code.trim(), name: value.name.trim(), description: value.description.trim() || undefined, roleIds: value.roleIds,
    maxRoles: Number(value.maxRoles), mode: value.mode, enabled: value.enabled }
  saving.value = true
  try {
    const constraint = target.value
    if (constraint) await request<Constraint>(`/api/v1/sod-constraints/${encodeURIComponent(constraint.id)}`, { method: 'PUT', body })
    else await request<Constraint>('/api/v1/sod-constraints', { method: 'POST', body })
    editing.value = null; toast.show(t(constraint ? 'sod.saved' : 'sod.created')); await load()
  } catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
async function remove() {
  const constraint = deleting.value
  if (!constraint || saving.value) return
  saving.value = true
  try {
    await request<null>(`/api/v1/sod-constraints/${encodeURIComponent(constraint.id)}`, { method: 'DELETE' })
    deleting.value = null; toast.show(t('sod.deleted')); await load()
  } catch (reason) { toast.show(errorMessage(reason), 'error') } finally { saving.value = false }
}
onMounted(load)
</script>
<template>
  <PageHeading :title="t('titles.sod')" :description="t('sod.description')">
    <UiButton variant="secondary" :loading="loading" @click="load"><RefreshCw :size="15" />{{ t('shared.refresh') }}</UiButton>
    <UiButton v-permission="'system.sod.btn.create'" @click="openCreate"><Plus :size="16" />{{ t('sod.create') }}</UiButton>
  </PageHeading>
  <p v-if="error" class="panel p-6 text-center text-xs text-rose-600" role="alert">{{ error }}</p>
  <div v-else class="grid gap-6 xl:grid-cols-2">
    <section class="panel overflow-hidden" aria-labelledby="sod-constraints">
      <header class="flex items-center gap-3 border-b border-line px-6 py-5"><span class="flex size-9 items-center justify-center rounded-xl bg-brand-soft text-brand"><Scale :size="18" /></span><div><h2 id="sod-constraints" class="text-sm font-semibold">{{ t('sod.constraints') }}</h2><p class="mt-1 text-[11px] text-muted">{{ t('sod.constraintsCaption') }}</p></div></header>
      <p v-if="!loading && !constraints.length" class="px-6 py-8 text-center text-xs text-muted">{{ t('sod.noConstraints') }}</p>
      <ul class="divide-y divide-line">
        <li v-for="constraint in constraints" :key="constraint.id" class="px-6 py-4" :data-constraint="constraint.code">
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p class="flex items-center gap-2 text-sm font-medium">{{ constraint.name }}<span class="badge" :class="constraint.mode === 'ENFORCE' ? 'bg-rose-50 text-rose-700' : 'bg-amber-50 text-amber-700'">{{ constraint.mode === 'ENFORCE' ? t('sod.enforce') : t('sod.report') }}</span><StatusBadge :active="constraint.enabled" /></p>
              <p class="mt-1 text-[11px] text-muted">{{ t('sod.atMost', { count: constraint.maxRoles }) }}<template v-if="constraint.description"> · {{ constraint.description }}</template></p>
              <p class="mt-2 flex flex-wrap gap-1.5"><span v-for="role in constraint.roles" :key="role.id" class="badge bg-canvas text-ink">{{ role.name }}</span></p>
            </div>
            <div class="flex gap-1">
              <button
                v-permission="'system.sod.btn.update'"
                type="button"
                class="table-action"
                :aria-label="t('sod.editNamed', { name: constraint.name })"
                @click="openEdit(constraint)"
              >
                <Pencil :size="14" />
              </button><button
                v-permission="'system.sod.btn.delete'"
                type="button"
                class="table-action hover:text-rose-600"
                :aria-label="t('sod.deleteNamed', { name: constraint.name })"
                @click="deleting = constraint"
              >
                <Trash2 :size="14" />
              </button>
            </div>
          </div>
        </li>
      </ul>
    </section>
    <section class="panel overflow-hidden" aria-labelledby="sod-conflicts">
      <header class="flex items-center gap-3 border-b border-line px-6 py-5"><span class="flex size-9 items-center justify-center rounded-xl bg-amber-50 text-amber-600"><AlertTriangle :size="18" /></span><div><h2 id="sod-conflicts" class="text-sm font-semibold">{{ t('sod.conflicts', { count: conflicts.length }) }}</h2><p class="mt-1 text-[11px] text-muted">{{ t('sod.conflictsCaption') }}</p></div></header>
      <p v-if="!loading && !conflicts.length" class="px-6 py-8 text-center text-xs text-muted">{{ t('sod.noConflicts') }}</p>
      <ul class="divide-y divide-line">
        <li v-for="conflict in conflicts" :key="`${conflict.constraintId}-${conflict.accountId}`" class="px-6 py-3.5" data-conflict>
          <p class="text-xs font-medium">{{ conflict.accountName }}<span v-if="conflict.username" class="ml-1 font-mono text-[11px] text-muted">{{ conflict.username }}</span></p>
          <p class="mt-1 text-[11px] text-muted">{{ t('sod.conflictText', { constraint: conflict.constraintName, roles: conflict.roles.map(role => role.name).join('、'), count: conflict.maxRoles }) }}</p>
        </li>
      </ul>
    </section>
  </div>
  <UiDialog
    :model-value="editing !== null"
    :title="editing === 'create' ? t('sod.create') : t('sod.editTitle')"
    wide
    :busy="saving"
    @update:model-value="editing = null"
  >
    <form id="sod-constraint" class="space-y-5" novalidate @submit.prevent="save">
      <div class="grid gap-5 sm:grid-cols-2">
        <UiField
          v-model="form.code"
          :label="t('sod.code')"
          :placeholder="t('sod.codePlaceholder')"
          :disabled="editing === 'edit'"
          required
          :error="fieldErrors.code"
        /><UiField
          v-model="form.name"
          :label="t('sod.name')"
          :placeholder="t('sod.namePlaceholder')"
          required
          :error="fieldErrors.name"
        />
      </div>
      <UiField v-model="form.description" :label="t('sod.descriptionLabel')" />
      <fieldset>
        <legend class="field-label">{{ t('sod.roles', { count: form.roleIds.length }) }}</legend>
        <input v-model="filter" :aria-label="t('sod.filterRoles')" :placeholder="t('sod.filterRoles')" class="field mb-2" />
        <div class="max-h-48 space-y-1.5 overflow-y-auto rounded-xl border border-line p-3" data-role-list>
          <UiCheckbox
            v-for="role in shown"
            :key="role.id"
            :checked="form.roleIds.includes(role.id)"
            :label="role.name"
            @update:checked="value => toggle(role.id, value)"
          />
        </div>
        <p v-if="fieldErrors.roleIds" class="mt-1 text-[11px] text-rose-600">{{ fieldErrors.roleIds }}</p>
      </fieldset>
      <div class="grid items-end gap-5 sm:grid-cols-2">
        <UiField
          v-model="form.maxRoles"
          :label="t('sod.maxRoles')"
          type="number"
          min="1"
          required
          :error="fieldErrors.maxRoles"
        /><UiSelect v-model="form.mode" :label="t('sod.mode')" :options="modes" />
      </div>
      <UiSwitch v-model="form.enabled" :label="t('sod.enabled')" />
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="editing = null">{{ t('shared.cancel') }}</UiButton><UiButton type="submit" form="sod-constraint" :loading="saving">{{ editing === 'create' ? t('sod.create') : t('sod.save') }}</UiButton></template>
  </UiDialog>
  <UiDialog :model-value="deleting !== null" :title="t('sod.deleteTitle')" :busy="saving" @update:model-value="deleting = null">
    <p class="text-xs leading-6">{{ t('sod.deleteWarning', { name: deleting?.name }) }}</p>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="deleting = null">{{ t('shared.cancel') }}</UiButton><UiButton variant="danger" :loading="saving" @click="remove">{{ t('sod.delete') }}</UiButton></template>
  </UiDialog>
</template>
