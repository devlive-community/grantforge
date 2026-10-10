<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Pencil, Plus, RefreshCw, ShieldCheck, Trash2 } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { ApiError, errorMessage, request } from '@/lib/api'
import { vPermission } from '@/lib/permission'
import { coverage, draftOf, emptyDraft, requestOf, type Policy, type PolicyDraft, type PolicyKind, type ServiceType } from '@/lib/policy'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import PolicyEditor from '@/components/PolicyEditor.vue'
import UiButton from '@/components/UiButton.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiSelect from '@/components/UiSelect.vue'

type Service = components['schemas']['ServiceResponse']

const { t } = useI18n(), toast = useToast(), route = useRoute(), router = useRouter()
const services = shallowRef<Service[]>([]), types = shallowRef<ServiceType[]>([]), policies = shallowRef<Policy[]>([])
const loading = ref(false), error = ref(''), saving = ref(false)
const kind = ref<PolicyKind>('ACCESS')
const editing = shallowRef<{ policy: Policy | null } | null>(null), draft = ref<PolicyDraft | null>(null)
const fieldErrors = ref<Record<string, string>>({}), formError = ref(''), removing = shallowRef<Policy | null>(null)

const serviceId = computed(() => {
  const asked = typeof route.query.service === 'string' ? route.query.service : ''
  return services.value.some(service => service.id === asked) ? asked : services.value[0]?.id ?? ''
})
const service = computed(() => services.value.find(candidate => candidate.id === serviceId.value) ?? null)
const type = computed(() => types.value.find(candidate => candidate.name === service.value?.serviceType) ?? null)
const serviceOptions = computed(() => services.value.map(entry => ({ value: entry.id, label: entry.label, description: entry.name })))
const kinds = computed(() => type.value?.policyTypes ?? [])

async function load() {
  loading.value = true; error.value = ''
  try {
    const [found, available] = await Promise.all([request<Service[]>('/api/v1/services'), request<ServiceType[]>('/api/v1/service-types')])
    services.value = found; types.value = available
    await loadPolicies()
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
async function loadPolicies() {
  if (!serviceId.value) { policies.value = []; return }
  if (!kinds.value.includes(kind.value)) kind.value = 'ACCESS'
  policies.value = await request<Policy[]>(`/api/v1/services/${encodeURIComponent(serviceId.value)}/policies?type=${kind.value}`)
}
async function reload() {
  try { await loadPolicies() } catch (reason) { error.value = errorMessage(reason) }
}
function chooseService(id: string) { void router.replace({ query: { ...route.query, service: id } }) }
watch(serviceId, (now, before) => { if (before !== undefined && now !== before) { editing.value = null; void reload() } })
function chooseKind(next: PolicyKind) { kind.value = next; editing.value = null; void reload() }

function edit(policy: Policy | null) {
  const current = type.value
  if (!current) return
  editing.value = { policy }; fieldErrors.value = {}; formError.value = ''
  draft.value = policy ? draftOf(policy, current) : emptyDraft(current)
}
async function save() {
  const value = draft.value, target = editing.value
  if (!value || !target || saving.value) return
  saving.value = true; fieldErrors.value = {}; formError.value = ''
  try {
    const body = requestOf(value, kind.value, target.policy?.version)
    if (target.policy) await request(`/api/v1/policies/${encodeURIComponent(target.policy.id)}`, { method: 'PUT', body })
    else await request(`/api/v1/services/${encodeURIComponent(serviceId.value)}/policies`, { method: 'POST', body })
    toast.show(target.policy ? t('policies.saved') : t('policies.created'))
    editing.value = null
    await loadPolicies()
  } catch (reason) {
    const problems = reason instanceof ApiError ? reason.problem?.errors ?? [] : []
    fieldErrors.value = Object.fromEntries(problems.map(problem => [problem.field, problem.message]))
    formError.value = reason instanceof ApiError && reason.status === 409 && !problems.length && target.policy
      ? t('policies.changedMeanwhile') : errorMessage(reason)
  } finally { saving.value = false }
}
async function remove() {
  const policy = removing.value
  if (!policy || saving.value) return
  saving.value = true
  try {
    await request(`/api/v1/policies/${encodeURIComponent(policy.id)}`, { method: 'DELETE' })
    removing.value = null; toast.show(t('policies.deleted')); await loadPolicies()
  } catch (reason) { toast.show(errorMessage(reason), 'error') } finally { saving.value = false }
}
function kindLabel(entry: PolicyKind) {
  if (entry === 'DATA_MASK') return t('policies.kinds.DATA_MASK')
  return entry === 'ROW_FILTER' ? t('policies.kinds.ROW_FILTER') : t('policies.kinds.ACCESS')
}
function items(policy: Policy) {
  const { document } = policy
  return kind.value === 'ACCESS'
    ? t('policies.accessSummary', { allow: document.allow.length, deny: document.deny.length })
    : t('policies.itemSummary', { count: document.allow.length })
}
function validity(policy: Policy) {
  const periods = policy.document.validity
  return periods.length ? t('policies.periods', { count: periods.length }) : ''
}
onMounted(load)
</script>
<template>
  <PageHeading :title="t('titles.policies')" :description="t('policies.description')">
    <UiButton variant="secondary" :loading="loading" @click="load"><RefreshCw :size="15" />{{ t('shared.refresh') }}</UiButton>
    <UiButton v-if="!editing" v-permission="'data.policy.btn.create'" :disabled="!type" @click="edit(null)"><Plus :size="16" />{{ t('policies.create') }}</UiButton>
  </PageHeading>
  <p v-if="error" class="panel p-6 text-center text-xs text-rose-600" role="alert">{{ error }}</p>
  <section v-else-if="!loading && !services.length" class="panel flex flex-col items-center p-10 text-center">
    <ShieldCheck :size="28" class="text-muted" />
    <p class="mt-3 font-medium">{{ t('policies.noServices') }}</p>
    <RouterLink to="/data/services" class="mt-2 text-xs text-brand hover:underline">{{ t('policies.toServices') }}</RouterLink>
  </section>
  <template v-else-if="service">
    <div class="mb-5 flex flex-wrap items-end gap-4">
      <div class="w-full max-w-xs">
        <UiSelect
          :model-value="serviceId"
          :label="t('policies.service')"
          :options="serviceOptions"
          :disabled="Boolean(editing)"
          @update:model-value="chooseService"
        />
      </div>
      <div class="flex gap-1 rounded-xl bg-surface p-1" role="group" :aria-label="t('policies.kind')">
        <button
          v-for="entry in kinds"
          :key="entry"
          type="button"
          class="rounded-lg px-3 py-1.5 text-xs font-medium transition disabled:opacity-50"
          :class="kind === entry ? 'bg-brand text-white' : 'text-muted hover:text-ink'"
          :aria-pressed="kind === entry"
          :disabled="Boolean(editing)"
          @click="chooseKind(entry)"
        >
          {{ kindLabel(entry) }}
        </button>
      </div>
    </div>
    <p v-if="!type" class="panel p-6 text-center text-xs text-amber-700" role="note">{{ t('policies.typeUnavailable', { type: service.serviceType }) }}</p>

    <form v-if="editing && draft && type" class="panel p-6" novalidate @submit.prevent="save">
      <h2 class="mb-6 text-base font-semibold">{{ editing.policy ? t('policies.editTitle', { name: editing.policy.name }) : t('policies.createTitle', { kind: kindLabel(kind) }) }}</h2>
      <PolicyEditor
        v-model="draft"
        :type="type"
        :kind="kind"
        :service-id="serviceId"
        :errors="fieldErrors"
      />
      <p v-if="formError" class="mt-6 rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
      <div class="mt-6 flex justify-end gap-2 border-t border-line pt-5">
        <UiButton variant="secondary" :disabled="saving" @click="editing = null">{{ t('shared.cancel') }}</UiButton>
        <UiButton type="submit" :loading="saving">{{ editing.policy ? t('policies.save') : t('policies.create') }}</UiButton>
      </div>
    </form>
    <section v-else-if="!policies.length" class="panel flex flex-col items-center p-10 text-center">
      <ShieldCheck :size="28" class="text-muted" />
      <p class="mt-3 font-medium">{{ t('policies.empty') }}</p>
      <p class="mt-1 text-xs text-muted">{{ t('policies.emptyHint') }}</p>
    </section>
    <section v-else class="panel divide-y divide-line">
      <div v-for="policy in policies" :key="policy.id" class="flex flex-wrap items-center gap-3 px-5 py-4" :data-policy="policy.name">
        <div class="min-w-0 flex-1">
          <p class="flex flex-wrap items-center gap-1.5 font-medium">
            {{ policy.name }}
            <span v-for="label in policy.labels" :key="label" class="badge py-0.5 text-[10px]">{{ label }}</span>
          </p>
          <p class="mt-1 truncate font-mono text-[11px] text-muted">{{ coverage(policy, type ?? undefined) }}</p>
        </div>
        <span class="text-[11px] text-muted">{{ items(policy) }}</span>
        <span v-if="validity(policy)" class="badge">{{ validity(policy) }}</span>
        <span v-if="policy.priority === 'OVERRIDE'" class="badge bg-amber-50 text-amber-700">{{ t('policies.override') }}</span>
        <span class="badge" :class="policy.enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-canvas text-muted'">{{ policy.enabled ? t('policies.enabled') : t('policies.disabled') }}</span>
        <div class="flex gap-0.5">
          <button
            v-permission="'data.policy.btn.edit'"
            type="button"
            class="table-action"
            :disabled="!type"
            :aria-label="t('policies.editNamed', { name: policy.name })"
            @click="edit(policy)"
          >
            <Pencil :size="14" />
          </button>
          <button
            v-permission="'data.policy.btn.delete'"
            type="button"
            class="table-action hover:text-rose-600"
            :aria-label="t('policies.deleteNamed', { name: policy.name })"
            @click="removing = policy"
          >
            <Trash2 :size="14" />
          </button>
        </div>
      </div>
    </section>
  </template>
  <UiDialog :model-value="Boolean(removing)" :title="t('policies.deleteTitle')" :busy="saving" @update:model-value="removing = null">
    <p class="text-xs">{{ t('policies.deleteConfirm', { name: removing?.name ?? '' }) }}</p>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="removing = null">{{ t('shared.cancel') }}</UiButton><UiButton variant="danger" :loading="saving" @click="remove">{{ t('policies.delete') }}</UiButton></template>
  </UiDialog>
</template>
