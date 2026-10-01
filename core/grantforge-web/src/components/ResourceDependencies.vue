<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { Network, Plus, Trash2 } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { useToast } from '@/stores/toast'
import { dependentTypes, displayName, resourceTypeKeys, targetTypes, type Edge, type Resource } from '@/lib/catalog'
import type { components } from '@/api/schema'
import UiButton from './UiButton.vue'
import UiDialog from './UiDialog.vue'
import UiSelect from './UiSelect.vue'
import DependencyGraph from './DependencyGraph.vue'

type Dependency = components['schemas']['DependencyResponse']
type Around = components['schemas']['ResourceDependenciesResponse']

const { resource, resources, canEdit } = defineProps<{ resource: Resource; resources: readonly Resource[]; canEdit: boolean }>()
const { t } = useI18n(), toast = useToast()
const around = shallowRef<Around>({ requires: [], requiredBy: [] }), loading = ref(false), error = ref('')
const adding = ref(false), target = ref(''), kind = ref<'REQUIRED' | 'OPTIONAL'>('REQUIRED'), saving = ref(false), formError = ref('')
const graphOpen = ref(false), edges = shallowRef<Edge[]>([])

const byId = computed(() => new Map(resources.map(item => [item.id, item])))
const canDepend = computed(() => dependentTypes.includes(resource.type))
// Literal keys, so the message checker sees every one in use.
const kindKeys = { REQUIRED: 'dependencies.required', OPTIONAL: 'dependencies.optional' } as const
/** Possible targets: allowed types, not the resource itself, not yet required, and not something that needs it. */
const targetOptions = computed(() => {
  const taken = new Set(around.value.requires.map(item => item.dependsOnId))
  return resources.filter(item => targetTypes.includes(item.type) && item.id !== resource.id && !taken.has(item.id)
    && !around.value.requiredBy.some(edge => edge.resourceId === item.id))
    .sort((a, b) => a.type.localeCompare(b.type) || a.code.localeCompare(b.code))
    .map(item => ({ value: item.id, label: `${t(resourceTypeKeys[item.type])} · ${displayName(item)}${displayName(item) === item.code ? '' : ` (${item.code})`}` }))
})
const kindOptions = computed(() => (['REQUIRED', 'OPTIONAL'] as const).map(value => ({ value, label: t(kindKeys[value]) })))

async function load() {
  loading.value = true; error.value = ''
  try {
    around.value = await request<Around>(`/api/v1/resources/${encodeURIComponent(resource.id)}/dependencies`)
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
watch(() => resource.id, load, { immediate: true })

function describe(id: string) {
  const other = byId.value.get(id)
  return other ? { name: displayName(other), code: other.code, type: t(resourceTypeKeys[other.type]) } : { name: id, code: '', type: '' }
}
function openAdd() { formError.value = ''; target.value = ''; kind.value = 'REQUIRED'; adding.value = true }
async function run(action: () => Promise<unknown>, done: string) {
  if (saving.value) return
  saving.value = true; formError.value = ''
  try { await action(); adding.value = false; toast.show(done); await load() } catch (reason) {
    if (adding.value) formError.value = errorMessage(reason); else toast.show(errorMessage(reason), 'error')
  } finally { saving.value = false }
}
function add() {
  if (!target.value) { formError.value = t('dependencies.chooseTarget'); return }
  void run(() => request(`/api/v1/resources/${encodeURIComponent(resource.id)}/dependencies`, { method: 'POST', body: { dependsOnId: target.value, kind: kind.value } }), t('dependencies.added'))
}
function toggle(dependency: Dependency) {
  const next = dependency.kind === 'REQUIRED' ? 'OPTIONAL' : 'REQUIRED'
  void run(() => request(`/api/v1/resource-dependencies/${encodeURIComponent(dependency.id)}`, { method: 'PUT', body: { kind: next } }), t('dependencies.changed'))
}
function remove(dependency: Dependency) {
  void run(() => request(`/api/v1/resource-dependencies/${encodeURIComponent(dependency.id)}`, { method: 'DELETE' }), t('dependencies.removed'))
}
async function openGraph() {
  try {
    edges.value = await request<Edge[]>(`/api/v1/applications/${encodeURIComponent(resource.applicationId)}/dependencies`)
    graphOpen.value = true
  } catch (reason) { toast.show(errorMessage(reason), 'error') }
}
</script>
<template>
  <section class="mt-8 border-t border-line pt-5" :aria-label="t('dependencies.title')">
    <header class="mb-3 flex flex-wrap items-center justify-between gap-2">
      <div><h3 class="text-sm font-semibold">{{ t('dependencies.title') }}</h3><p class="mt-1 text-[11px] text-muted">{{ canDepend ? t('dependencies.caption') : t('dependencies.captionTarget') }}</p></div>
      <div class="flex gap-2">
        <UiButton variant="secondary" @click="openGraph"><Network :size="15" />{{ t('dependencies.graph') }}</UiButton>
        <UiButton v-if="canEdit && canDepend" variant="secondary" @click="openAdd"><Plus :size="15" />{{ t('dependencies.add') }}</UiButton>
      </div>
    </header>
    <p v-if="error" class="text-xs text-rose-600" role="alert">{{ error }}</p>
    <div v-else-if="loading" class="h-10 animate-pulse rounded-lg bg-line"></div>
    <template v-else>
      <template v-if="canDepend">
        <p class="field-label">{{ t('dependencies.requires') }}</p>
        <p v-if="!around.requires.length" class="mb-4 text-xs text-muted">{{ t('dependencies.requiresNone') }}</p>
        <ul v-else class="mb-4 divide-y divide-line rounded-lg border border-line">
          <li v-for="dependency in around.requires" :key="dependency.id" class="flex flex-wrap items-center gap-3 px-3 py-2.5 text-xs" :data-dependency="dependency.id">
            <span class="badge">{{ describe(dependency.dependsOnId).type }}</span>
            <span class="min-w-0 flex-1"><span class="font-medium">{{ describe(dependency.dependsOnId).name }}</span><span class="ml-2 font-mono text-[10px] text-muted">{{ describe(dependency.dependsOnId).code }}</span></span>
            <span class="badge" :class="dependency.kind === 'REQUIRED' ? 'bg-brand-soft text-brand' : ''">{{ t(kindKeys[dependency.kind]) }}</span>
            <span v-if="dependency.source === 'DECLARED'" class="badge">{{ t('dependencies.declared') }}</span>
            <template v-if="canEdit">
              <button type="button" class="table-action" :disabled="saving" @click="toggle(dependency)">{{ dependency.kind === 'REQUIRED' ? t('dependencies.makeOptional') : t('dependencies.makeRequired') }}</button>
              <button
                v-if="dependency.source !== 'DECLARED'"
                type="button"
                class="table-action hover:text-rose-600"
                :aria-label="t('dependencies.removeNamed', { name: describe(dependency.dependsOnId).name })"
                :disabled="saving"
                @click="remove(dependency)"
              >
                <Trash2 :size="14" />
              </button>
            </template>
          </li>
        </ul>
      </template>
      <p class="field-label">{{ t('dependencies.requiredBy') }}</p>
      <p v-if="!around.requiredBy.length" class="text-xs text-muted">{{ t('dependencies.requiredByNone') }}</p>
      <ul v-else class="divide-y divide-line rounded-lg border border-line">
        <li v-for="dependency in around.requiredBy" :key="dependency.id" class="flex items-center gap-3 px-3 py-2.5 text-xs">
          <span class="badge">{{ describe(dependency.resourceId).type }}</span><span class="flex-1 font-medium">{{ describe(dependency.resourceId).name }}</span><span class="badge">{{ t(kindKeys[dependency.kind]) }}</span>
        </li>
      </ul>
    </template>
  </section>
  <UiDialog v-model="adding" :title="t('dependencies.addTitle')" :description="t('dependencies.addDescription', { name: displayName(resource) })" :busy="saving">
    <form id="resource-dependency" class="space-y-5" novalidate @submit.prevent="add">
      <UiSelect
        v-model="target"
        :label="t('dependencies.target')"
        :options="targetOptions"
        :placeholder="t('dependencies.targetPlaceholder')"
        required
      />
      <UiSelect v-model="kind" :label="t('dependencies.kind')" :options="kindOptions" />
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="adding = false">{{ t('shared.cancel') }}</UiButton><UiButton type="submit" form="resource-dependency" :loading="saving">{{ t('dependencies.add') }}</UiButton></template>
  </UiDialog>
  <UiDialog v-model="graphOpen" :title="t('dependencies.graphTitle', { name: displayName(resource) })" :description="t('dependencies.graphDescription')" wide>
    <DependencyGraph :root="resource.id" :edges="edges" :resources="resources" />
  </UiDialog>
</template>
