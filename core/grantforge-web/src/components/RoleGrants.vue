<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { Search } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { displayName, resourceTypeKeys, type Resource } from '@/lib/catalog'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import UiButton from './UiButton.vue'
import ImpactSummary from './ImpactSummary.vue'
import UiDialog from './UiDialog.vue'
import UiSelect from './UiSelect.vue'
import UiSwitch from './UiSwitch.vue'

type Application = components['schemas']['ApplicationResponse']
type Matrix = components['schemas']['GrantMatrixResponse']
type Effect = Matrix['grants'][number]['effect']
type Reason = Matrix['states'][number]['reasons'][number]
type Impact = components['schemas']['ImpactReportResponse']

const open = defineModel<boolean>({ required: true })
const { roleId, roleName } = defineProps<{ roleId: string; roleName: string }>()
const { t } = useI18n(), toast = useToast()
const applications = shallowRef<Application[]>([]), applicationId = ref(''), resources = shallowRef<Resource[]>([])
const matrix = shallowRef<Matrix | null>(null), loading = ref(false), error = ref(''), saving = ref(false)
const pending = ref(new Map<string, Effect | null>()), query = ref(''), onlyGranted = ref(false)
// What saving would do, shown before it is confirmed; any further choice makes it stale.
const impact = shallowRef<Impact | null>(null), checking = ref(false)

/** Only these types take grants; modules follow from what lies below them. */
const grantable = new Set<Resource['type']>(['MENU', 'PAGE', 'TAB', 'ACTION', 'API'])
// Literal keys, so the message checker sees every one in use.
const viaKeys = { ANCESTOR: 'grants.viaAncestor', DEPENDENCY: 'grants.viaDependency', DENIAL: 'grants.viaDenial', SYSTEM_ROLE: 'grants.viaSystemRole' } as const
const stateKeys = { ALLOWED: 'grants.stateAllowed', IMPLIED: 'grants.stateImplied', DENIED: 'grants.stateDenied' } as const
const choices = [{ value: null, key: 'grants.choiceNone' }, { value: 'ALLOW', key: 'grants.choiceAllow' }, { value: 'DENY', key: 'grants.choiceDeny' }] as const

const byId = computed(() => new Map(resources.value.map(resource => [resource.id, resource])))
const grants = computed(() => new Map((matrix.value?.grants ?? []).map(grant => [grant.resourceId, grant])))
const states = computed(() => new Map((matrix.value?.states ?? []).map(state => [state.resourceId, state])))
const readOnly = computed(() => matrix.value?.readOnly ?? false)
const applicationOptions = computed(() => applications.value.map(item => ({ value: item.id, label: item.name })))
/** Resources depth first, so every row comes right below its parent. */
const rows = computed(() => {
  const children = new Map<string | null, Resource[]>()
  for (const resource of resources.value) {
    const parent = resource.parentId ?? null
    children.set(parent, [...children.get(parent) ?? [], resource])
  }
  const ordered: Resource[] = []
  const visit = (parent: string | null) => {
    for (const resource of [...children.get(parent) ?? []].sort((a, b) => a.sortOrder - b.sortOrder)) { ordered.push(resource); visit(resource.id) }
  }
  visit(null)
  const text = query.value.trim().toLowerCase()
  return ordered.filter(resource => (!text || displayName(resource).toLowerCase().includes(text) || resource.code.toLowerCase().includes(text))
    && (!onlyGranted.value || states.value.has(resource.id)))
})

async function load() {
  loading.value = true; error.value = ''
  try {
    if (!applications.value.length) {
      applications.value = await request<Application[]>('/api/v1/applications')
      applicationId.value = applications.value[0]?.id ?? ''
    }
    if (!applicationId.value) return
    const [tree, current] = await Promise.all([
      request<Resource[]>(`/api/v1/applications/${encodeURIComponent(applicationId.value)}/resources`),
      request<Matrix>(`/api/v1/roles/${encodeURIComponent(roleId)}/grants`, { query: { applicationId: applicationId.value } }),
    ])
    resources.value = tree; matrix.value = current; pending.value = new Map(); impact.value = null
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
watch([open, () => roleId], ([visible]) => { if (visible) { applications.value = []; void load() } }, { immediate: true })
watch(applicationId, (current, previous) => { if (previous && current !== previous) void load() })

function changes() {
  return [...pending.value].map(([resourceId, effect]) => ({ resourceId, effect }))
}
/** What the row shows as chosen: a pending change, else the stored grant. */
function chosen(resource: Resource): Effect | null {
  return pending.value.has(resource.id) ? pending.value.get(resource.id) ?? null : grants.value.get(resource.id)?.effect ?? null
}
// Each choice is previewed by the server, so the derived states always follow the server's rules.
let previewing: ReturnType<typeof setTimeout> | undefined
function choose(resource: Resource, effect: Effect | null) {
  const next = new Map(pending.value)
  // Choosing what is stored again drops the change.
  if ((grants.value.get(resource.id)?.effect ?? null) === effect) next.delete(resource.id); else next.set(resource.id, effect)
  pending.value = next; impact.value = null
  clearTimeout(previewing)
  previewing = setTimeout(() => void preview(), 200)
}
async function preview() {
  try {
    const result = await request<Matrix>(`/api/v1/roles/${encodeURIComponent(roleId)}/grants/preview`, { method: 'POST',
      body: { applicationId: applicationId.value, changes: changes() } })
    matrix.value = { ...result, grants: matrix.value?.grants ?? result.grants }
  } catch (reason) { toast.show(errorMessage(reason), 'error') }
}
/** First works out what saving would do; saving happens once that is confirmed. */
async function check() {
  if (checking.value || !pending.value.size) return
  checking.value = true
  try {
    impact.value = await request<Impact>(`/api/v1/roles/${encodeURIComponent(roleId)}/grants/impact`, { method: 'POST',
      body: { applicationId: applicationId.value, changes: changes() } })
  } catch (reason) { toast.show(errorMessage(reason), 'error') } finally { checking.value = false }
}
async function save() {
  if (saving.value || !pending.value.size) return
  saving.value = true
  try {
    clearTimeout(previewing)
    matrix.value = await request<Matrix>(`/api/v1/roles/${encodeURIComponent(roleId)}/grants`, { method: 'PUT',
      body: { applicationId: applicationId.value, changes: changes() } })
    pending.value = new Map(); impact.value = null
    toast.show(t('grants.saved'))
  } catch (reason) { toast.show(errorMessage(reason), 'error') } finally { saving.value = false }
}
function reasonText(reason: Reason): string {
  const source = byId.value.get(reason.resourceId)
  return t(viaKeys[reason.via], { name: source ? displayName(source) : reason.resourceId })
}
function reasonsOf(resourceId: string): string {
  const reasons = states.value.get(resourceId)?.reasons ?? []
  const shown = reasons.slice(0, 2).map(reasonText).join(' · ')
  return reasons.length > 2 ? t('grants.moreReasons', { reasons: shown, count: reasons.length - 2 }) : shown
}
</script>
<template>
  <UiDialog v-model="open" :title="t('grants.title', { name: roleName })" :description="t('grants.description')" wide>
    <p v-if="readOnly" class="mb-4 rounded-lg bg-brand-soft p-3 text-xs text-brand" role="note">{{ t('grants.readOnly') }}</p>
    <div class="mb-4 flex flex-wrap items-end gap-3">
      <div class="w-56"><UiSelect v-model="applicationId" :label="t('grants.application')" :options="applicationOptions" /></div>
      <div class="flex min-w-48 flex-1 items-center gap-2 rounded-xl border border-line bg-canvas/40 px-3"><Search :size="15" class="text-muted" /><input v-model="query" :aria-label="t('grants.search')" :placeholder="t('grants.searchPlaceholder')" class="w-full bg-transparent py-2.5 text-xs outline-none" /></div>
      <UiSwitch v-model="onlyGranted" :label="t('grants.onlyGranted')" />
    </div>
    <ImpactSummary v-if="impact" :impact="impact" class="mb-4" />
    <p v-if="error" class="text-xs text-rose-600" role="alert">{{ error }}</p>
    <div v-else-if="loading" class="h-40 animate-pulse rounded-lg bg-line"></div>
    <div v-else class="max-h-[55vh] overflow-auto rounded-lg border border-line">
      <table class="w-full min-w-[640px] text-left text-xs">
        <thead class="sticky top-0 bg-surface"><tr class="border-b border-line text-[11px] text-muted"><th class="px-3 py-2">{{ t('grants.columnResource') }}</th><th class="px-3 py-2">{{ t('grants.columnGrant') }}</th><th class="px-3 py-2">{{ t('grants.columnEffect') }}</th></tr></thead>
        <tbody>
          <tr
            v-for="resource in rows"
            :key="resource.id"
            class="border-b border-line last:border-0"
            :data-resource="resource.code"
            :class="pending.has(resource.id) ? 'bg-amber-50/60' : ''"
          >
            <td class="px-3 py-2" :style="{ paddingLeft: `${12 + resource.depth * 18}px` }"><span class="badge mr-2">{{ t(resourceTypeKeys[resource.type]) }}</span><span class="font-medium">{{ displayName(resource) }}</span><span class="ml-2 font-mono text-[10px] text-muted">{{ resource.code }}</span></td>
            <td class="px-3 py-2">
              <div v-if="grantable.has(resource.type)" class="inline-flex rounded-lg border border-line p-0.5" role="group" :aria-label="t('grants.grantOf', { name: displayName(resource) })">
                <button
                  v-for="choice in choices"
                  :key="choice.key"
                  type="button"
                  class="whitespace-nowrap rounded-md px-2 py-1 text-[11px] transition disabled:opacity-50"
                  :class="chosen(resource) === choice.value ? (choice.value === 'DENY' ? 'bg-rose-500 text-white' : choice.value === 'ALLOW' ? 'bg-brand text-white' : 'bg-canvas') : 'text-muted hover:text-current'"
                  :aria-pressed="chosen(resource) === choice.value"
                  :disabled="readOnly || saving"
                  @click="choose(resource, choice.value)"
                >
                  {{ t(choice.key) }}
                </button>
              </div>
              <span v-if="grants.get(resource.id) && !grants.get(resource.id)?.applies" class="ml-2 text-[10px] text-amber-600">{{ t('grants.expired') }}</span>
            </td>
            <td class="px-3 py-2">
              <template v-if="states.get(resource.id)">
                <span class="badge" :class="states.get(resource.id)?.state === 'DENIED' ? 'bg-rose-50 text-rose-700' : states.get(resource.id)?.state === 'ALLOWED' ? 'bg-emerald-50 text-emerald-700' : 'bg-brand-soft text-brand'">{{ t(stateKeys[states.get(resource.id)?.state ?? 'IMPLIED']) }}</span>
                <span v-if="!states.get(resource.id)?.explicit" class="ml-2 text-[10px] text-muted">{{ reasonsOf(resource.id) }}</span>
              </template>
              <span v-else class="text-[10px] text-muted">—</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <template #footer>
      <span class="mr-auto text-[11px] text-muted">{{ t('grants.pending', { count: pending.size }) }}</span>
      <UiButton variant="secondary" :disabled="saving || !pending.size" @click="load">{{ t('grants.reset') }}</UiButton>
      <UiButton v-if="impact" variant="secondary" :disabled="saving" @click="impact = null">{{ t('grants.backToChanges') }}</UiButton>
      <UiButton v-if="impact" :loading="saving" @click="save">{{ t('grants.confirmSave') }}</UiButton>
      <UiButton v-else :loading="checking" :disabled="readOnly || !pending.size" @click="check">{{ t('grants.save') }}</UiButton>
    </template>
  </UiDialog>
</template>
