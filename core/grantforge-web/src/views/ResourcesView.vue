<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { AlertTriangle, AppWindow, ArrowDown, ArrowUp, Boxes, KeyRound, MoveRight, Pencil, Plus, Trash2 } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { vPermission } from '@/lib/permission'
import { useAuth } from '@/stores/auth'
import { useToast } from '@/stores/toast'
import { allowsParent, childTypes, dependentTypes, displayName, hasDenyMode, hasRoute, isWithin, resourceTypeKeys, targetTypes, type Resource, type ResourceType } from '@/lib/catalog'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import UiButton from '@/components/UiButton.vue'
import ImpactSummary from '@/components/ImpactSummary.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiField from '@/components/UiField.vue'
import UiSelect from '@/components/UiSelect.vue'
import UiSwitch from '@/components/UiSwitch.vue'
import UiTree, { type DropPosition, type TreeNode } from '@/components/UiTree.vue'
import FieldUsages from '@/components/FieldUsages.vue'
import ResourceDependencies from '@/components/ResourceDependencies.vue'
import ApplicationClients from '@/components/ApplicationClients.vue'

type Application = components['schemas']['ApplicationResponse']
type DenyMode = Resource['denyMode']
type Impact = components['schemas']['ImpactReportResponse']
type Dialog = 'app-create' | 'app-edit' | 'app-delete' | 'create' | 'edit' | 'move' | 'delete'

const { t } = useI18n(), auth = useAuth(), toast = useToast()
const applications = shallowRef<Application[]>([]), applicationId = ref('')
const resources = shallowRef<Resource[]>([]), loading = ref(false), error = ref(''), selectedId = ref<string | null>(null)
const dialog = ref<Dialog | null>(null), saving = ref(false), formError = ref(''), fieldErrors = ref<Record<string, string>>({}), appFieldErrors = ref<Record<string, string>>({})
const form = ref({ type: 'MODULE' as ResourceType, code: '', name: '', description: '', route: '', visible: true, enabled: true, denyMode: 'HIDE' as DenyMode })
const appForm = ref({ code: '', name: '', description: '' })
const createParent = ref<string | null>(null), moveParent = ref(''), clientsOpen = ref(false)
// The catalog is shared by every tenant, so only platform administrators change it.
// Hides the button rows when none of their buttons is permitted; each button checks its own permission.
const canEdit = computed(() => ['platform.resource.btn.create', 'platform.resource.btn.edit', 'platform.resource.btn.move',
  'platform.resource.btn.delete', 'platform.resource.btn.app-create', 'platform.resource.btn.app-edit',
  'platform.resource.btn.app-delete', 'platform.resource.btn.app-clients'].some(auth.can))

const application = computed(() => applications.value.find(item => item.id === applicationId.value) ?? null)
const applicationOptions = computed(() => applications.value.map(item => ({ value: item.id, label: item.name })))
const byId = computed(() => new Map(resources.value.map(resource => [resource.id, resource])))
const selected = computed(() => selectedId.value ? byId.value.get(selectedId.value) ?? null : null)
const childrenOf = (parentId: string | null | undefined) => resources.value
  .filter(resource => (resource.parentId ?? null) === (parentId ?? null)).sort((a, b) => a.sortOrder - b.sortOrder)
// Literal keys, so the message checker sees every one in use.
const denyKeys = { HIDE: 'catalog.denyHide', DISABLE: 'catalog.denyDisable' } as const
const typeLabel = (type: ResourceType) => t(resourceTypeKeys[type])
const nodes = computed<TreeNode[]>(() => {
  const build = (parentId: string | null): TreeNode[] => childrenOf(parentId)
    .map(resource => ({ id: resource.id, label: displayName(resource), hint: resource.code, badge: typeLabel(resource.type), children: build(resource.id) }))
  return build(null)
})
const ancestors = computed(() => {
  const result: Resource[] = []
  for (let resource = selected.value?.parentId ? byId.value.get(selected.value.parentId) : undefined; resource;
    resource = resource.parentId ? byId.value.get(resource.parentId) : undefined) result.unshift(resource)
  return result
})
const siblings = computed(() => selected.value ? childrenOf(selected.value.parentId) : [])
const position = computed(() => siblings.value.findIndex(resource => resource.id === selectedId.value))
const parentType = (parentId: string | null) => parentId ? byId.value.get(parentId)?.type ?? null : null
const createParentName = computed(() => {
  const parent = createParent.value ? byId.value.get(createParent.value) : undefined
  return parent ? displayName(parent) : ''
})
const createTypes = computed(() => childTypes(parentType(createParent.value)).map(type => ({ value: type, label: typeLabel(type) })))
/** Possible new parents of the selection: outside its own subtree and allowed for its type. */
const parentOptions = computed(() => {
  const moving = selected.value
  if (!moving) return []
  const options = allowsParent(moving.type, null) ? [{ value: '', label: t('catalog.topLevel') }] : []
  const visit = (parentId: string | null) => {
    for (const resource of childrenOf(parentId)) {
      if (resource.id === moving.id) continue
      if (allowsParent(moving.type, resource.type)) options.push({ value: resource.id, label: `${'— '.repeat(resource.depth)}${displayName(resource)}` })
      visit(resource.id)
    }
  }
  visit(null)
  return options
})
const denyModes = computed(() => (['HIDE', 'DISABLE'] as const).map(mode => ({ value: mode, label: t(denyKeys[mode]) })))

async function loadApplications() {
  try {
    applications.value = await request<Application[]>('/api/v1/applications')
    if (!applications.value.some(item => item.id === applicationId.value)) applicationId.value = applications.value[0]?.id ?? ''
  } catch (reason) { error.value = errorMessage(reason) }
}
async function loadResources() {
  if (!applicationId.value) { resources.value = []; return }
  loading.value = true; error.value = ''
  try {
    resources.value = await request<Resource[]>(`/api/v1/applications/${encodeURIComponent(applicationId.value)}/resources`)
    if (selectedId.value && !byId.value.has(selectedId.value)) selectedId.value = null
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
watch(applicationId, () => { selectedId.value = null; void loadResources() })

function openResource(kind: 'create' | 'edit' | 'move' | 'delete', parent: string | null = null) {
  formError.value = ''; fieldErrors.value = {}; dialog.value = kind; createParent.value = parent; impact.value = null
  const current = kind === 'edit' ? selected.value : null
  form.value = {
    type: current?.type ?? childTypes(parentType(parent))[0] ?? 'MODULE', code: current?.code ?? '', name: current?.name ?? '',
    description: current?.description ?? '', route: current?.route ?? '', visible: current?.visible ?? true,
    enabled: current?.enabled ?? true, denyMode: current?.denyMode ?? 'HIDE',
  }
  moveParent.value = selected.value?.parentId ?? ''
}
function openApplication(kind: 'app-create' | 'app-edit' | 'app-delete') {
  formError.value = ''; appFieldErrors.value = {}; dialog.value = kind
  const current = kind === 'app-edit' ? application.value : null
  appForm.value = { code: current?.code ?? '', name: current?.name ?? '', description: current?.description ?? '' }
}
async function run(action: () => Promise<{ id: string } | null>, done: string, after: () => Promise<void> = loadResources) {
  if (saving.value) return
  saving.value = true; formError.value = ''; fieldErrors.value = {}; appFieldErrors.value = {}
  try {
    const result = await action()
    dialog.value = null; toast.show(done)
    await after()
    if (result && byId.value.has(result.id)) selectedId.value = result.id
  } catch (reason) {
    if (dialog.value) formError.value = errorMessage(reason); else toast.show(errorMessage(reason), 'error')
  } finally { saving.value = false }
}
function settings() {
  const routed = hasRoute(form.value.type)
  return { name: form.value.name, description: form.value.description, route: routed ? form.value.route : null,
    visible: routed ? form.value.visible : true, enabled: form.value.enabled, denyMode: hasDenyMode(form.value.type) ? form.value.denyMode : 'HIDE' }
}
// Enabling or disabling a resource changes what roles allow in every tenant: that is shown before it is saved.
const impact = shallowRef<Impact | null>(null), checking = ref(false)
watch(() => form.value.enabled, () => { impact.value = null })
async function checkEnabled(id: string) {
  checking.value = true; formError.value = ''
  try {
    impact.value = await request<Impact>(`/api/v1/resources/${encodeURIComponent(id)}/impact`, { query: { enabled: form.value.enabled } })
  } catch (reason) { formError.value = errorMessage(reason) } finally { checking.value = false }
}
function saveResource() {
  formError.value = ''
  fieldErrors.value = {}
  // Every failed field shows its message at once, rather than only the first.
  if (!form.value.name.trim()) fieldErrors.value.name = t('catalog.enterName')
  if (!form.value.code.trim()) fieldErrors.value.code = t('catalog.enterCode')
  if (Object.keys(fieldErrors.value).length) return
  if (dialog.value === 'edit' && selected.value && selected.value.enabled !== form.value.enabled && !impact.value) {
    void checkEnabled(selected.value.id)
    return
  }
  if (dialog.value === 'create') {
    const body = { parentId: createParent.value, type: form.value.type, code: form.value.code, ...settings() }
    void run(() => request<Resource>(`/api/v1/applications/${encodeURIComponent(applicationId.value)}/resources`, { method: 'POST', body }),
      t('catalog.created'), async () => { await loadResources(); await loadApplications() })
  } else if (selected.value) {
    const id = selected.value.id
    void run(() => request<Resource>(`/api/v1/resources/${encodeURIComponent(id)}`, { method: 'PUT', body: { code: form.value.code, ...settings() } }), t('catalog.saved'))
  }
}
function move(id: string, parentId: string | null, index: number) {
  void run(() => request<Resource>(`/api/v1/resources/${encodeURIComponent(id)}/move`, { method: 'POST', body: { parentId, position: index } }), t('catalog.moved'))
}
function removeResource() {
  const resource = selected.value
  if (!resource) return
  void run(async () => {
    await request<null>(`/api/v1/resources/${encodeURIComponent(resource.id)}`, { method: 'DELETE' })
    selectedId.value = resource.parentId ?? null
    return null
  }, t('catalog.deleted'), async () => { await loadResources(); await loadApplications() })
}
/** Where a drop lands: the parent the dragged resource gets and its position among the new siblings. */
function dropTarget(source: string, target: string, where: DropPosition): { parentId: string | null; index: number } | null {
  const dragged = byId.value.get(source), onto = byId.value.get(target)
  if (!dragged || !onto || isWithin(resources.value, target, source)) return null
  const parentId = where === 'inside' ? onto.id : onto.parentId ?? null
  if (!allowsParent(dragged.type, parentType(parentId))) return null
  if (where === 'inside') return { parentId, index: childrenOf(onto.id).filter(item => item.id !== source).length }
  const index = childrenOf(parentId).filter(item => item.id !== source).findIndex(item => item.id === target)
  return { parentId, index: where === 'before' ? index : index + 1 }
}
function dropped(source: string, target: string, where: DropPosition) {
  const landing = dropTarget(source, target, where)
  if (landing) { selectedId.value = source; move(source, landing.parentId, landing.index) }
}
function saveApplication() {
  formError.value = ''
  appFieldErrors.value = {}
  // Every failed field shows its message at once, rather than only the first.
  if (!appForm.value.name.trim()) appFieldErrors.value.name = t('catalog.enterAppName')
  if (dialog.value === 'app-create' && !appForm.value.code.trim()) appFieldErrors.value.code = t('catalog.enterAppCode')
  if (Object.keys(appFieldErrors.value).length) return
  const { code, name, description } = appForm.value
  if (dialog.value === 'app-create') {
    void run(async () => {
      const created = await request<Application>('/api/v1/applications', { method: 'POST', body: { code, name, description } })
      applicationId.value = created.id
      return null
    }, t('catalog.appCreated'), loadApplications)
  } else if (application.value) {
    const id = application.value.id
    void run(async () => { await request<Application>(`/api/v1/applications/${encodeURIComponent(id)}`, { method: 'PUT', body: { name, description } }); return null },
      t('catalog.appSaved'), loadApplications)
  }
}
function removeApplication() {
  const current = application.value
  if (!current) return
  void run(async () => { await request<null>(`/api/v1/applications/${encodeURIComponent(current.id)}`, { method: 'DELETE' }); return null },
    t('catalog.appDeleted'), loadApplications)
}
onMounted(async () => { await loadApplications(); await loadResources() })
</script>
<template>
  <PageHeading :title="t('titles.resources')" :description="t('catalog.description')">
    <UiButton v-if="canEdit" v-permission="'platform.resource.btn.app-create'" variant="secondary" @click="openApplication('app-create')"><AppWindow :size="16" />{{ t('catalog.createApp') }}</UiButton>
    <UiButton v-if="canEdit && application" v-permission="'platform.resource.btn.create'" @click="openResource('create')"><Plus :size="16" />{{ t('catalog.createTop') }}</UiButton>
  </PageHeading>
  <section class="panel mb-6 flex flex-wrap items-end gap-4 p-4">
    <div class="w-full sm:w-72"><UiSelect v-model="applicationId" :label="t('catalog.application')" :options="applicationOptions" :placeholder="t('catalog.noApplications')" /></div>
    <p v-if="application" class="flex-1 pb-2 text-xs text-muted">{{ application.description || t('catalog.noDescription') }} · {{ t('catalog.resourceCount', { count: application.resources }) }}<span v-if="application.builtin" class="ml-2 rounded bg-brand-soft px-1.5 py-0.5 text-[10px] text-brand">{{ t('catalog.builtin') }}</span></p>
    <div v-if="canEdit && application" class="flex gap-2 pb-0.5">
      <UiButton v-permission="'platform.resource.btn.app-edit'" variant="secondary" @click="openApplication('app-edit')"><Pencil :size="15" />{{ t('catalog.editApp') }}</UiButton>
      <UiButton v-if="!application.builtin" v-permission="'platform.resource.btn.app-clients'" variant="secondary" @click="clientsOpen = true"><KeyRound :size="15" />{{ t('catalog.clients') }}</UiButton>
      <UiButton v-permission="'platform.resource.btn.app-delete'" variant="danger" :disabled="application.builtin" @click="openApplication('app-delete')"><Trash2 :size="15" />{{ t('catalog.deleteApp') }}</UiButton>
    </div>
  </section>
  <div class="grid gap-6 lg:grid-cols-[minmax(0,420px)_1fr]">
    <section class="panel p-4">
      <header class="mb-3 flex items-center gap-3 px-2"><span class="flex size-8 items-center justify-center rounded-lg bg-brand-soft text-brand"><Boxes :size="16" /></span><div><h2 class="text-sm font-semibold">{{ t('catalog.tree') }}</h2><p class="mt-0.5 text-[11px] text-muted">{{ canEdit ? t('catalog.treeCaptionEdit') : t('catalog.treeCaption') }}</p></div></header>
      <p v-if="error" class="px-2 py-8 text-center text-xs text-rose-600" role="alert">{{ error }}</p>
      <div v-else-if="loading && !resources.length" class="space-y-3 p-2"><div v-for="index in 4" :key="index" class="h-7 animate-pulse rounded-lg bg-line"></div></div>
      <div v-else-if="!resources.length" class="px-2 py-10 text-center"><p class="font-medium">{{ t('catalog.empty') }}</p><p class="mt-2 text-xs text-muted">{{ t('catalog.emptyHint') }}</p></div>
      <UiTree
        v-else
        v-model:selected="selectedId"
        :nodes="nodes"
        :label="t('catalog.tree')"
        :draggable="auth.can('platform.resource.btn.move') && !saving"
        :can-drop="(source, target, where) => dropTarget(source, target, where) !== null"
        @drop="dropped"
      />
    </section>
    <section class="panel p-6">
      <p v-if="!selected" class="py-16 text-center text-xs text-muted">{{ t('catalog.nothingSelected') }}</p>
      <template v-else>
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div><p class="text-[11px] font-medium text-brand">{{ typeLabel(selected.type) }}<span v-if="selected.builtin" class="ml-2 rounded bg-brand-soft px-1.5 py-0.5 text-[10px]">{{ t('catalog.builtin') }}</span></p><h2 class="mt-1 text-lg font-semibold">{{ displayName(selected) }}</h2><p class="mt-1 break-all font-mono text-xs text-muted">{{ selected.code }}</p></div>
          <div v-if="canEdit" class="flex flex-wrap gap-2">
            <UiButton v-if="childTypes(selected.type).length" v-permission="'platform.resource.btn.create'" variant="secondary" @click="openResource('create', selected.id)"><Plus :size="15" />{{ t('catalog.addChild') }}</UiButton>
            <UiButton v-permission="'platform.resource.btn.edit'" variant="secondary" @click="openResource('edit')"><Pencil :size="15" />{{ t('catalog.edit') }}</UiButton>
          </div>
        </div>
        <dl class="mt-6 grid gap-5 sm:grid-cols-3">
          <div><dt class="field-label">{{ t('catalog.path') }}</dt><dd class="text-sm">{{ ancestors.map(resource => displayName(resource)).join(' / ') || t('catalog.topLevel') }}</dd></div>
          <div v-if="hasRoute(selected.type)"><dt class="field-label">{{ t('catalog.route') }}</dt><dd class="break-all font-mono text-xs">{{ selected.route || '—' }}</dd></div>
          <div><dt class="field-label">{{ t('catalog.state') }}</dt><dd class="text-sm">{{ selected.enabled ? t('catalog.enabled') : t('catalog.disabled') }}<template v-if="hasRoute(selected.type)"> · {{ selected.visible ? t('catalog.visible') : t('catalog.hidden') }}</template></dd></div>
          <div v-if="hasDenyMode(selected.type)"><dt class="field-label">{{ t('catalog.denyMode') }}</dt><dd class="text-sm">{{ t(denyKeys[selected.denyMode]) }}</dd></div>
          <div><dt class="field-label">{{ t('catalog.children') }}</dt><dd class="text-sm">{{ childrenOf(selected.id).length }}</dd></div>
          <div v-if="selected.description" class="sm:col-span-3"><dt class="field-label">{{ t('catalog.descriptionLabel') }}</dt><dd class="text-sm">{{ selected.description }}</dd></div>
        </dl>
        <ResourceDependencies v-if="dependentTypes.includes(selected.type) || targetTypes.includes(selected.type)" :resource="selected" :resources="resources" :can-edit="auth.can('platform.resource.btn.dependencies')" />
        <FieldUsages v-if="selected.type === 'FIELD'" :resource-id="selected.id" />
        <div v-if="canEdit" class="mt-8 flex flex-wrap gap-2 border-t border-line pt-5">
          <UiButton v-permission="'platform.resource.btn.move'" variant="secondary" :disabled="position <= 0 || saving" @click="move(selected.id, selected.parentId ?? null, position - 1)"><ArrowUp :size="15" />{{ t('catalog.moveUp') }}</UiButton>
          <UiButton v-permission="'platform.resource.btn.move'" variant="secondary" :disabled="position >= siblings.length - 1 || saving" @click="move(selected.id, selected.parentId ?? null, position + 1)"><ArrowDown :size="15" />{{ t('catalog.moveDown') }}</UiButton>
          <UiButton v-permission="'platform.resource.btn.move'" variant="secondary" @click="openResource('move')"><MoveRight :size="15" />{{ t('catalog.moveTo') }}</UiButton>
          <UiButton
            v-permission="'platform.resource.btn.delete'"
            variant="danger"
            class="ml-auto"
            :disabled="selected.builtin"
            @click="openResource('delete')"
          >
            <Trash2 :size="15" />{{ t('catalog.delete') }}
          </UiButton>
        </div>
      </template>
    </section>
  </div>
  <UiDialog
    :model-value="dialog === 'create' || dialog === 'edit'"
    :title="dialog === 'edit' ? t('catalog.editTitle') : t('catalog.createTitle')"
    :description="dialog === 'create' ? (createParent ? t('catalog.createUnder', { name: createParentName }) : t('catalog.createAtTop')) : undefined"
    :busy="saving"
    wide
    @update:model-value="dialog = null"
  >
    <form id="catalog-resource" class="grid gap-5 sm:grid-cols-2" novalidate @submit.prevent="saveResource">
      <UiSelect
        v-if="dialog === 'create'"
        v-model="form.type"
        :label="t('catalog.type')"
        :options="createTypes"
        required
      />
      <UiField
        v-model="form.name"
        :label="t('catalog.name')"
        :placeholder="t('catalog.namePlaceholder')"
        required
        :error="fieldErrors.name"
      />
      <UiField
        v-model="form.code"
        :label="t('catalog.code')"
        :placeholder="t('catalog.codePlaceholder')"
        :disabled="dialog === 'edit' && selected?.builtin"
        required
        :error="fieldErrors.code"
      />
      <UiField v-if="hasRoute(form.type)" v-model="form.route" :label="t('catalog.route')" :placeholder="t('catalog.routePlaceholder')" />
      <UiSelect v-if="hasDenyMode(form.type)" v-model="form.denyMode" :label="t('catalog.denyMode')" :options="denyModes" />
      <div class="sm:col-span-2"><UiField v-model="form.description" :label="t('catalog.descriptionLabel')" :placeholder="t('catalog.descriptionPlaceholder')" textarea /></div>
      <div class="flex flex-wrap gap-6 sm:col-span-2"><UiSwitch v-model="form.enabled" :label="t('catalog.enabledSwitch')" /><UiSwitch v-if="hasRoute(form.type)" v-model="form.visible" :label="t('catalog.visibleSwitch')" /></div>
      <ImpactSummary v-if="impact" :impact="impact" tenants class="sm:col-span-2" />
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700 sm:col-span-2" role="alert">{{ formError }}</p>
    </form>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="dialog = null">{{ t('shared.cancel') }}</UiButton><UiButton type="submit" form="catalog-resource" :loading="saving || checking">{{ impact ? t('shared.confirm') : dialog === 'edit' ? t('tenants.save') : t('catalog.createTitle') }}</UiButton></template>
  </UiDialog>
  <UiDialog
    :model-value="dialog === 'move'"
    :title="t('catalog.moveTitle')"
    :description="t('catalog.moveDescription', { name: selected ? displayName(selected) : '' })"
    :busy="saving"
    @update:model-value="dialog = null"
  >
    <UiSelect v-model="moveParent" :label="t('catalog.newParent')" :options="parentOptions" :placeholder="t('catalog.noParents')" />
    <p v-if="formError" class="mt-4 text-xs text-rose-600" role="alert">{{ formError }}</p>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="dialog = null">{{ t('shared.cancel') }}</UiButton><UiButton :loading="saving" :disabled="!parentOptions.some(option => option.value === moveParent)" @click="selected && move(selected.id, moveParent || null, childrenOf(moveParent || null).length)">{{ t('catalog.move') }}</UiButton></template>
  </UiDialog>
  <UiDialog :model-value="dialog === 'delete'" :title="t('catalog.deleteTitle')" :busy="saving" @update:model-value="dialog = null">
    <div class="flex gap-4"><span class="flex size-11 shrink-0 items-center justify-center rounded-xl bg-rose-50 text-rose-500"><AlertTriangle :size="22" /></span><div><p>{{ t('catalog.deleteConfirm', { name: selected ? displayName(selected) : '' }) }}</p><p class="mt-2 text-xs leading-6 text-muted">{{ t('catalog.deleteWarning') }}</p></div></div><p v-if="formError" class="mt-4 text-xs text-rose-600" role="alert">{{ formError }}</p>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="dialog = null">{{ t('shared.cancel') }}</UiButton><UiButton variant="danger" :loading="saving" @click="removeResource">{{ t('catalog.delete') }}</UiButton></template>
  </UiDialog>
  <UiDialog :model-value="dialog === 'app-create' || dialog === 'app-edit'" :title="dialog === 'app-edit' ? t('catalog.editAppTitle') : t('catalog.createApp')" :busy="saving" @update:model-value="dialog = null">
    <form id="catalog-application" class="space-y-5" novalidate @submit.prevent="saveApplication">
      <UiField
        v-model="appForm.name"
        :label="t('catalog.appName')"
        :placeholder="t('catalog.appNamePlaceholder')"
        required
        :error="appFieldErrors.name"
      />
      <UiField
        v-if="dialog === 'app-create'"
        v-model="appForm.code"
        :label="t('catalog.appCode')"
        :placeholder="t('catalog.appCodePlaceholder')"
        required
        :error="appFieldErrors.code"
      />
      <UiField v-model="appForm.description" :label="t('catalog.descriptionLabel')" textarea />
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="dialog = null">{{ t('shared.cancel') }}</UiButton><UiButton type="submit" form="catalog-application" :loading="saving">{{ dialog === 'app-edit' ? t('tenants.save') : t('catalog.createApp') }}</UiButton></template>
  </UiDialog>
  <UiDialog :model-value="dialog === 'app-delete'" :title="t('catalog.deleteAppTitle')" :busy="saving" @update:model-value="dialog = null">
    <p>{{ t('catalog.deleteAppConfirm', { name: application?.name }) }}</p><p class="mt-2 text-xs leading-6 text-muted">{{ t('catalog.deleteAppWarning') }}</p><p v-if="formError" class="mt-4 text-xs text-rose-600" role="alert">{{ formError }}</p>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="dialog = null">{{ t('shared.cancel') }}</UiButton><UiButton variant="danger" :loading="saving" @click="removeApplication">{{ t('catalog.deleteApp') }}</UiButton></template>
  </UiDialog>
  <ApplicationClients v-if="application && !application.builtin" v-model="clientsOpen" :application-id="application.id" :application-name="application.name" />
</template>
