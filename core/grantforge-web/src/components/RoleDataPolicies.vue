<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { Eye, Pencil, Plus, Trash2 } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { ApiError, errorMessage, request } from '@/lib/api'
import { emptyGroup, fromCondition, toCondition, type DataEntity, type DataVariable, type GroupNode } from '@/lib/dataCondition'
import { dataLabels } from '@/lib/dataLabels'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import ConditionBuilder from '@/components/ConditionBuilder.vue'
import UiButton from '@/components/UiButton.vue'
import UiCheckbox from '@/components/UiCheckbox.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiField from '@/components/UiField.vue'
import UiSelect from '@/components/UiSelect.vue'

type Policy = components['schemas']['DataPolicyResponse']
type Entities = components['schemas']['DataEntitiesResponse']
type Unit = components['schemas']['OrgUnitResponse']
type Users = components['schemas']['PageResultUserResponse']
type Preview = components['schemas']['DataPreviewResponse']
type Scope = Policy['scope']
type Action = Policy['action']
interface Draft { id: string | null; entityCode: string; action: Action; effect: Policy['effect']; scope: Scope; condition: GroupNode; orgUnitIds: string[] }

/**
 * The data permissions of a role: which rows of each secured entity it may read, change, delete or export. Policies are
 * edited in place, conditions with the condition builder, and the preview counts what a user would see with only this
 * role, its inherited roles included.
 */
const open = defineModel<boolean>({ required: true })
const { roleId, roleName } = defineProps<{ roleId: string; roleName: string }>()
const { t } = useI18n(), toast = useToast()
const labels = dataLabels(t)
const ACTIONS: Action[] = ['READ', 'UPDATE', 'DELETE', 'EXPORT']

const policies = shallowRef<Policy[]>([]), entities = shallowRef<DataEntity[]>([]), variables = shallowRef<DataVariable[]>([])
const units = shallowRef<Unit[]>([]), loading = ref(false), error = ref(''), saving = ref(false)
const draft = ref<Draft | null>(null), fieldErrors = ref<Record<string, string>>({}), formError = ref(''), removing = shallowRef<Policy | null>(null)
const previewUser = ref(''), previewEntity = ref(''), previewAction = ref<Action>('READ'), previewing = ref(false)
const preview = shallowRef<Preview | null>(null), previewError = ref('')

const entity = computed(() => entities.value.find(candidate => candidate.code === draft.value?.entityCode))
const entityOptions = computed(() => entities.value.map(candidate => ({ value: candidate.code, label: labels.entity(candidate.code, candidate.name) })))
const scopeOptions = computed(() => (entity.value?.scopes ?? []).map(scope => ({ value: scope, label: labels.scope(scope) })))
const actionOptions = computed(() => ACTIONS.map(action => ({ value: action, label: labels.action(action) })))
const effectOptions = computed(() => [{ value: 'ALLOW', label: labels.effect('ALLOW') }, { value: 'DENY', label: labels.effect('DENY') }])

async function load() {
  loading.value = true; error.value = ''; draft.value = null; preview.value = null
  try {
    const [found, described, departments] = await Promise.all([
      request<Policy[]>(`/api/v1/roles/${encodeURIComponent(roleId)}/data-policies`),
      request<Entities>('/api/v1/data-entities'),
      request<Unit[]>('/api/v1/org-units').catch(() => [] as Unit[]),
    ])
    policies.value = found; entities.value = described.entities; variables.value = described.variables; units.value = departments
    previewEntity.value = previewEntity.value || described.entities[0]?.code || ''
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
watch([open, () => roleId], ([visible]) => { if (visible) void load() }, { immediate: true })

function entityName(code: string) {
  const found = entities.value.find(candidate => candidate.code === code)
  return labels.entity(code, found?.name ?? code)
}
function startNew() {
  const first = entities.value[0]
  fieldErrors.value = {}; formError.value = ''
  draft.value = { id: null, entityCode: first?.code ?? '', action: 'READ', effect: 'ALLOW', scope: first?.scopes.includes('SELF') ? 'SELF' : 'TENANT',
    condition: emptyGroup(), orgUnitIds: [] }
}
function startEdit(policy: Policy) {
  fieldErrors.value = {}; formError.value = ''
  const fields = entities.value.find(candidate => candidate.code === policy.entityCode)?.fields ?? []
  draft.value = { id: policy.id, entityCode: policy.entityCode, action: policy.action, effect: policy.effect, scope: policy.scope,
    condition: policy.condition ? fromCondition(policy.condition, fields) : emptyGroup(), orgUnitIds: [...policy.orgUnitIds] }
}
function changeEntity(code: string) {
  const next = entities.value.find(candidate => candidate.code === code)
  if (!draft.value || !next) return
  draft.value = { ...draft.value, entityCode: code, scope: next.scopes.includes(draft.value.scope) ? draft.value.scope : 'TENANT',
    condition: emptyGroup() }
}
function toggleUnit(id: string, on: boolean) {
  if (!draft.value) return
  draft.value = { ...draft.value, orgUnitIds: on ? [...draft.value.orgUnitIds, id] : draft.value.orgUnitIds.filter(other => other !== id) }
}
async function save() {
  const value = draft.value
  if (!value || saving.value) return
  saving.value = true; fieldErrors.value = {}; formError.value = ''
  const body = { entityCode: value.entityCode, action: value.action, effect: value.effect, scope: value.scope,
    condition: value.scope === 'CONDITION' ? toCondition(value.condition, entity.value?.fields ?? []) : undefined,
    orgUnitIds: value.scope === 'CUSTOM_ORGS' ? value.orgUnitIds : [] }
  try {
    if (value.id) await request(`/api/v1/data-policies/${encodeURIComponent(value.id)}`, { method: 'PUT', body })
    else await request(`/api/v1/roles/${encodeURIComponent(roleId)}/data-policies`, { method: 'POST', body })
    toast.show(value.id ? t('dataPolicies.saved') : t('dataPolicies.created'))
    draft.value = null
    policies.value = await request<Policy[]>(`/api/v1/roles/${encodeURIComponent(roleId)}/data-policies`)
  } catch (reason) {
    const problems = reason instanceof ApiError ? reason.problem?.errors ?? [] : []
    fieldErrors.value = Object.fromEntries(problems.map(problem => [problem.field, problem.message]))
    formError.value = errorMessage(reason)
  } finally { saving.value = false }
}
async function remove() {
  const policy = removing.value
  if (!policy || saving.value) return
  saving.value = true
  try {
    await request(`/api/v1/data-policies/${encodeURIComponent(policy.id)}`, { method: 'DELETE' })
    removing.value = null; toast.show(t('dataPolicies.deleted'))
    policies.value = await request<Policy[]>(`/api/v1/roles/${encodeURIComponent(roleId)}/data-policies`)
  } catch (reason) { toast.show(errorMessage(reason), 'error') } finally { saving.value = false }
}
async function runPreview() {
  const username = previewUser.value.trim()
  if (!username || previewing.value) return
  previewing.value = true; previewError.value = ''; preview.value = null
  try {
    const found = await request<Users>('/api/v1/users', { query: { q: username, size: 20 } })
    const user = found.items.find(item => item.username.toLowerCase() === username.toLowerCase())
    if (!user) { previewError.value = t('dataPolicies.previewNoUser', { name: username }); return }
    preview.value = await request<Preview>(`/api/v1/roles/${encodeURIComponent(roleId)}/data-policies/preview`, { method: 'POST',
      body: { accountId: user.id, entityCode: previewEntity.value, action: previewAction.value } })
  } catch (reason) { previewError.value = errorMessage(reason) } finally { previewing.value = false }
}
function summary(policy: Policy) {
  if (policy.scope === 'CUSTOM_ORGS') {
    return policy.orgUnitIds.map(id => units.value.find(unit => unit.id === id)?.name ?? id).join('、')
  }
  return policy.scope === 'CONDITION' ? t('dataPolicies.hasCondition') : ''
}
</script>
<template>
  <UiDialog
    v-model="open"
    :title="t('dataPolicies.title', { name: roleName })"
    :description="t('dataPolicies.description')"
    :busy="saving"
    wide
  >
    <p v-if="error" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ error }}</p>
    <div v-else-if="loading" class="h-24 animate-pulse rounded-xl bg-line"></div>
    <form
      v-else-if="draft"
      id="data-policy-form"
      class="space-y-5"
      novalidate
      @submit.prevent="save"
    >
      <div class="grid gap-4 sm:grid-cols-2">
        <UiSelect
          :model-value="draft.entityCode"
          :label="t('dataPolicies.entity')"
          :options="entityOptions"
          :disabled="Boolean(draft.id)"
          :error="fieldErrors.entityCode"
          @update:model-value="changeEntity"
        />
        <UiSelect v-model="draft.action" :label="t('dataPolicies.action')" :options="actionOptions" />
        <UiSelect v-model="draft.scope" :label="t('dataPolicies.scope')" :options="scopeOptions" :error="fieldErrors.scope" />
        <UiSelect v-model="draft.effect" :label="t('dataPolicies.effect')" :options="effectOptions" />
      </div>
      <div v-if="draft.scope === 'CONDITION' && entity">
        <p class="field-label">{{ t('dataPolicies.condition') }}</p>
        <ConditionBuilder v-model="draft.condition" :fields="entity.fields" :variables="variables" :errors="fieldErrors" />
      </div>
      <fieldset v-if="draft.scope === 'CUSTOM_ORGS'">
        <legend class="field-label">{{ t('dataPolicies.departments') }}</legend>
        <p v-if="!units.length" class="text-xs text-muted">{{ t('dataPolicies.noDepartments') }}</p>
        <div class="max-h-48 space-y-1.5 overflow-y-auto">
          <div v-for="unit in units" :key="unit.id" :style="{ paddingLeft: `${unit.depth * 16}px` }">
            <UiCheckbox :checked="draft.orgUnitIds.includes(unit.id)" :label="unit.name" @update:checked="toggleUnit(unit.id, $event)" />
          </div>
        </div>
        <p v-if="fieldErrors.orgUnitIds" class="mt-2 text-xs text-rose-600">{{ fieldErrors.orgUnitIds }}</p>
      </fieldset>
      <p v-if="fieldErrors.condition" class="text-xs text-rose-600">{{ fieldErrors.condition }}</p>
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form>
    <div v-else class="space-y-6">
      <section>
        <p v-if="!policies.length" class="rounded-xl border border-dashed border-line p-6 text-center text-xs text-muted">{{ t('dataPolicies.empty') }}</p>
        <ul v-else class="divide-y divide-line rounded-xl border border-line">
          <li v-for="policy in policies" :key="policy.id" class="flex flex-wrap items-center gap-2 px-4 py-3 text-xs" :data-policy="policy.entityCode + ':' + policy.action">
            <span class="font-medium">{{ entityName(policy.entityCode) }}</span>
            <span class="badge">{{ labels.action(policy.action) }}</span>
            <span>{{ labels.scope(policy.scope) }}</span>
            <span v-if="summary(policy)" class="text-muted">{{ summary(policy) }}</span>
            <span class="badge" :class="policy.effect === 'DENY' ? 'bg-rose-50 text-rose-700' : 'bg-emerald-50 text-emerald-700'">{{ labels.effect(policy.effect) }}</span>
            <span class="flex-1"></span>
            <button type="button" class="table-action" :aria-label="t('dataPolicies.editNamed', { name: entityName(policy.entityCode) })" @click="startEdit(policy)"><Pencil :size="13" /></button>
            <button type="button" class="table-action hover:text-rose-600" :aria-label="t('dataPolicies.deleteNamed', { name: entityName(policy.entityCode) })" @click="removing = policy"><Trash2 :size="13" /></button>
          </li>
        </ul>
      </section>
      <section class="rounded-xl bg-canvas/60 p-4" aria-labelledby="data-preview">
        <h3 id="data-preview" class="flex items-center gap-1.5 text-xs font-semibold"><Eye :size="13" />{{ t('dataPolicies.previewTitle') }}</h3>
        <p class="mt-1 text-[11px] text-muted">{{ t('dataPolicies.previewHint') }}</p>
        <form class="mt-3 grid items-end gap-3 sm:grid-cols-[1fr_9rem_8rem_auto]" novalidate @submit.prevent="runPreview">
          <UiField v-model="previewUser" :label="t('dataPolicies.previewUser')" :placeholder="t('dataPolicies.previewUserPlaceholder')" />
          <UiSelect v-model="previewEntity" :label="t('dataPolicies.entity')" :options="entityOptions" />
          <UiSelect v-model="previewAction" :label="t('dataPolicies.action')" :options="actionOptions" />
          <UiButton type="submit" variant="secondary" :loading="previewing" :disabled="!previewUser.trim()">{{ t('dataPolicies.preview') }}</UiButton>
        </form>
        <p v-if="preview" class="mt-3 text-xs" role="status">{{ t('dataPolicies.previewResult', { withRole: preview.withRole, now: preview.now }) }}</p>
        <p v-if="previewError" class="mt-3 text-xs text-rose-600" role="alert">{{ previewError }}</p>
      </section>
    </div>
    <template #footer>
      <template v-if="draft">
        <UiButton variant="secondary" :disabled="saving" @click="draft = null">{{ t('shared.cancel') }}</UiButton>
        <UiButton type="submit" form="data-policy-form" :loading="saving">{{ draft.id ? t('dataPolicies.save') : t('dataPolicies.add') }}</UiButton>
      </template>
      <template v-else>
        <UiButton variant="secondary" @click="open = false">{{ t('dataPolicies.close') }}</UiButton>
        <UiButton :disabled="loading || !entities.length" @click="startNew"><Plus :size="15" />{{ t('dataPolicies.add') }}</UiButton>
      </template>
    </template>
  </UiDialog>
  <UiDialog :model-value="Boolean(removing)" :title="t('dataPolicies.deleteTitle')" :busy="saving" @update:model-value="removing = null">
    <p class="text-xs">{{ t('dataPolicies.deleteConfirm', { name: removing ? entityName(removing.entityCode) : '' }) }}</p>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="removing = null">{{ t('shared.cancel') }}</UiButton><UiButton variant="danger" :loading="saving" @click="remove">{{ t('dataPolicies.delete') }}</UiButton></template>
  </UiDialog>
</template>
