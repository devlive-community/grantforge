<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { AlertTriangle, ArrowDown, ArrowUp, FolderTree, MoveRight, Pencil, Plus, Trash2 } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { vPermission } from '@/lib/permission'
import { useAuth } from '@/stores/auth'
import { orgOptions } from '@/lib/org'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import UiButton from '@/components/UiButton.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiField from '@/components/UiField.vue'
import UiSelect from '@/components/UiSelect.vue'
import UiTree, { type TreeNode } from '@/components/UiTree.vue'

type Unit = components['schemas']['OrgUnitResponse']

const { t } = useI18n(), auth = useAuth(), toast = useToast()
const units = shallowRef<Unit[]>([]), loading = ref(false), error = ref(''), selectedId = ref<string | null>(null)
const dialog = ref<'create' | 'edit' | 'move' | 'delete' | null>(null), saving = ref(false), formError = ref('')
const code = ref(''), name = ref(''), createParent = ref<string | null>(null), moveParent = ref('')
// Hides the button rows when none of their buttons is permitted; each button checks its own permission.
const canEdit = computed(() => ['system.org.btn.create', 'system.org.btn.edit', 'system.org.btn.move', 'system.org.btn.delete'].some(auth.can))

const byId = computed(() => new Map(units.value.map(unit => [unit.id, unit])))
const selected = computed(() => selectedId.value ? byId.value.get(selectedId.value) ?? null : null)
const childrenOf = (parentId: string | null | undefined) => units.value
  .filter(unit => (unit.parentId ?? null) === (parentId ?? null)).sort((a, b) => a.sortOrder - b.sortOrder)
const nodes = computed<TreeNode[]>(() => {
  const build = (parentId: string | null): TreeNode[] => childrenOf(parentId)
    .map(unit => ({ id: unit.id, label: unit.name, hint: unit.code, children: build(unit.id) }))
  return build(null)
})
const ancestors = computed(() => {
  const result: Unit[] = []
  for (let unit = selected.value?.parentId ? byId.value.get(selected.value.parentId) : undefined; unit;
    unit = unit.parentId ? byId.value.get(unit.parentId) : undefined) result.unshift(unit)
  return result
})
const siblings = computed(() => selected.value ? childrenOf(selected.value.parentId) : [])
const position = computed(() => siblings.value.findIndex(unit => unit.id === selectedId.value))
/** Possible new parents: anything outside the moving department's own subtree. */
const parentOptions = computed(() => [{ value: '', label: t('org.root') }, ...orgOptions(units.value, selectedId.value)])

async function load() {
  loading.value = true; error.value = ''
  try {
    units.value = await request<Unit[]>('/api/v1/org-units')
    if (selectedId.value && !byId.value.has(selectedId.value)) selectedId.value = null
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
function open(kind: 'create' | 'edit' | 'move' | 'delete', parent: string | null = null) {
  formError.value = ''; dialog.value = kind; createParent.value = parent
  code.value = kind === 'edit' ? selected.value?.code ?? '' : ''
  name.value = kind === 'edit' ? selected.value?.name ?? '' : ''
  moveParent.value = selected.value?.parentId ?? ''
}
async function run(action: () => Promise<Unit | null>, done: string) {
  if (saving.value) return
  saving.value = true; formError.value = ''
  try {
    const result = await action()
    dialog.value = null; toast.show(done)
    if (result) selectedId.value = result.id
    await load()
  } catch (reason) {
    if (dialog.value) formError.value = errorMessage(reason); else toast.show(errorMessage(reason), 'error')
  } finally { saving.value = false }
}
function checked(): boolean {
  formError.value = !code.value.trim() ? t('org.enterCode') : !name.value.trim() ? t('org.enterName') : ''
  return !formError.value
}
function save() {
  if (!checked()) return
  if (dialog.value === 'create') {
    void run(() => request<Unit>('/api/v1/org-units', { method: 'POST', body: { parentId: createParent.value, code: code.value, name: name.value } }), t('org.created'))
  } else if (selected.value) {
    const id = selected.value.id
    void run(() => request<Unit>(`/api/v1/org-units/${encodeURIComponent(id)}`, { method: 'PUT', body: { code: code.value, name: name.value } }), t('org.saved'))
  }
}
function move(parentId: string | null, index: number) {
  const unit = selected.value
  if (!unit) return
  void run(() => request<Unit>(`/api/v1/org-units/${encodeURIComponent(unit.id)}/move`, { method: 'POST', body: { parentId, position: index } }), t('org.moved'))
}
function remove() {
  const unit = selected.value
  if (!unit) return
  void run(async () => {
    await request<null>(`/api/v1/org-units/${encodeURIComponent(unit.id)}`, { method: 'DELETE' })
    selectedId.value = unit.parentId ?? null
    return null
  }, t('org.deleted'))
}
onMounted(load)
</script>
<template>
  <PageHeading :title="t('titles.org')" :description="t('org.description')"><UiButton v-if="canEdit" v-permission="'system.org.btn.create'" @click="open('create')"><Plus :size="16" />{{ t('org.createRoot') }}</UiButton></PageHeading>
  <div class="grid gap-6 lg:grid-cols-[minmax(0,360px)_1fr]">
    <section class="panel p-4">
      <header class="mb-3 flex items-center gap-3 px-2"><span class="flex size-8 items-center justify-center rounded-lg bg-brand-soft text-brand"><FolderTree :size="16" /></span><div><h2 class="text-sm font-semibold">{{ t('org.tree') }}</h2><p class="mt-0.5 text-[11px] text-muted">{{ t('org.treeCaption') }}</p></div></header>
      <p v-if="error" class="px-2 py-8 text-center text-xs text-rose-600" role="alert">{{ error }}</p>
      <div v-else-if="loading && !units.length" class="space-y-3 p-2"><div v-for="index in 4" :key="index" class="h-7 animate-pulse rounded-lg bg-line"></div></div>
      <div v-else-if="!units.length" class="px-2 py-10 text-center"><p class="font-medium">{{ t('org.empty') }}</p><p class="mt-2 text-xs text-muted">{{ t('org.emptyHint') }}</p></div>
      <UiTree v-else v-model:selected="selectedId" :nodes="nodes" :label="t('org.tree')" />
    </section>
    <section class="panel p-6">
      <p v-if="!selected" class="py-16 text-center text-xs text-muted">{{ t('org.nothingSelected') }}</p>
      <template v-else>
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div><h2 class="text-lg font-semibold">{{ selected.name }}</h2><p class="mt-1 font-mono text-xs text-muted">{{ selected.code }}</p></div>
          <div v-if="canEdit" class="flex flex-wrap gap-2">
            <UiButton v-permission="'system.org.btn.create'" variant="secondary" @click="open('create', selected.id)"><Plus :size="15" />{{ t('org.addChild') }}</UiButton>
            <UiButton v-permission="'system.org.btn.edit'" variant="secondary" @click="open('edit')"><Pencil :size="15" />{{ t('org.edit') }}</UiButton>
          </div>
        </div>
        <dl class="mt-6 grid gap-5 sm:grid-cols-3">
          <div><dt class="field-label">{{ t('org.level') }}</dt><dd class="text-sm">{{ t('org.levelValue', { level: selected.depth + 1 }) }}</dd></div>
          <div><dt class="field-label">{{ t('org.path') }}</dt><dd class="text-sm">{{ ancestors.map(unit => unit.name).join(' / ') || t('org.root') }}</dd></div>
          <div><dt class="field-label">{{ t('org.children') }}</dt><dd class="text-sm">{{ childrenOf(selected.id).length }}</dd></div>
        </dl>
        <div v-if="canEdit" class="mt-8 flex flex-wrap gap-2 border-t border-line pt-5">
          <UiButton v-permission="'system.org.btn.move'" variant="secondary" :disabled="position <= 0 || saving" @click="move(selected.parentId ?? null, position - 1)"><ArrowUp :size="15" />{{ t('org.moveUp') }}</UiButton>
          <UiButton v-permission="'system.org.btn.move'" variant="secondary" :disabled="position >= siblings.length - 1 || saving" @click="move(selected.parentId ?? null, position + 1)"><ArrowDown :size="15" />{{ t('org.moveDown') }}</UiButton>
          <UiButton v-permission="'system.org.btn.move'" variant="secondary" @click="open('move')"><MoveRight :size="15" />{{ t('org.moveTo') }}</UiButton>
          <UiButton v-permission="'system.org.btn.delete'" variant="danger" class="ml-auto" @click="open('delete')"><Trash2 :size="15" />{{ t('org.delete') }}</UiButton>
        </div>
      </template>
    </section>
  </div>
  <UiDialog
    :model-value="dialog === 'create' || dialog === 'edit'"
    :title="dialog === 'edit' ? t('org.editTitle') : t('org.createTitle')"
    :description="dialog === 'create' && createParent ? t('org.createUnder', { name: byId.get(createParent)?.name }) : undefined"
    :busy="saving"
    @update:model-value="dialog = null"
  >
    <form id="org-unit" class="space-y-5" novalidate @submit.prevent="save">
      <UiField v-model="name" :label="t('org.name')" :placeholder="t('org.namePlaceholder')" required /><UiField v-model="code" :label="t('org.code')" :placeholder="t('org.codePlaceholder')" required />
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="dialog = null">{{ t('shared.cancel') }}</UiButton><UiButton type="submit" form="org-unit" :loading="saving">{{ dialog === 'edit' ? t('tenants.save') : t('org.createTitle') }}</UiButton></template>
  </UiDialog>
  <UiDialog
    :model-value="dialog === 'move'"
    :title="t('org.moveTitle')"
    :description="t('org.moveDescription', { name: selected?.name })"
    :busy="saving"
    @update:model-value="dialog = null"
  >
    <UiSelect v-model="moveParent" :label="t('org.newParent')" :options="parentOptions" />
    <p v-if="formError" class="mt-4 text-xs text-rose-600" role="alert">{{ formError }}</p>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="dialog = null">{{ t('shared.cancel') }}</UiButton><UiButton :loading="saving" @click="move(moveParent || null, childrenOf(moveParent || null).length)">{{ t('org.move') }}</UiButton></template>
  </UiDialog>
  <UiDialog :model-value="dialog === 'delete'" :title="t('org.deleteTitle')" :busy="saving" @update:model-value="dialog = null">
    <div class="flex gap-4"><span class="flex size-11 shrink-0 items-center justify-center rounded-xl bg-rose-50 text-rose-500"><AlertTriangle :size="22" /></span><div><p>{{ t('org.deleteConfirm', { name: selected?.name }) }}</p><p class="mt-2 text-xs leading-6 text-muted">{{ t('org.deleteWarning') }}</p></div></div><p v-if="formError" class="mt-4 text-xs text-rose-600" role="alert">{{ formError }}</p>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="dialog = null">{{ t('shared.cancel') }}</UiButton><UiButton variant="danger" :loading="saving" @click="remove">{{ t('org.delete') }}</UiButton></template>
  </UiDialog>
</template>
