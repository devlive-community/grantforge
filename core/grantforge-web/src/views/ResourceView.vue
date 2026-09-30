<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, reactive, ref, shallowRef } from 'vue'
import { Plus, RefreshCw, Search, Pencil, Trash2, KeyRound, ShieldCheck, ArrowUpRight, AlertTriangle, Check } from '@lucide/vue'
import { usePage } from '@/composables/usePage'
import { allOptions, errorMessage, request } from '@/lib/api'
import { dateLabel } from '@/lib/format'
import { checkedIds, flattenTree, toggleTree } from '@/lib/tree'
import type { Entity, Menu, MenuTree, Method, NamedOption, Role, TableColumn } from '@/types/api'
import { useI18n } from 'vue-i18n'
import { useToast } from '@/stores/toast'
import PageHeading from '@/components/PageHeading.vue'
import DataTable from '@/components/DataTable.vue'
import PageControls from '@/components/PageControls.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import UiButton from '@/components/UiButton.vue'
import UiField from '@/components/UiField.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiSelect from '@/components/UiSelect.vue'
import UiCheckbox from '@/components/UiCheckbox.vue'
import UiSwitch from '@/components/UiSwitch.vue'
import TreeChoices from '@/components/TreeChoices.vue'
type Resource = Entity & Partial<Menu> & Partial<Method> & Partial<Role>
const { kind } = defineProps<{ kind: 'roles' | 'methods' | 'menus' }>()
const { t } = useI18n()
const config = computed(() => ({
  roles: { title: t('titles.roles'), single: t('resource.kindRole'), endpoint: '/api/v1/role', description: t('resource.rolesDescription') },
  methods: { title: t('titles.methods'), single: t('resource.kindMethod'), endpoint: '/api/v1/method', description: t('resource.methodsDescription') },
  menus: { title: t('titles.menus'), single: t('resource.kindMenu'), endpoint: '/api/v1/menu', description: t('resource.menusDescription') },
})[kind])
const { page, size, data, rows, loading, error, refresh, setSize } = usePage<Resource>(() => config.value.endpoint)
const search = ref(''), toast = useToast()
const filtered = computed(() => rows.value.filter(row => `${row.name} ${row.code || ''} ${row.url || ''}`.toLowerCase().includes(search.value.toLowerCase())))
const columns = computed<TableColumn[]>(() => kind === 'menus' ? [
  { key: 'name', label: t('resource.menuName') }, { key: 'url', label: t('resource.path') }, { key: 'type', label: t('resource.type') }, { key: 'methods', label: t('titles.methods') }, { key: 'active', label: t('shared.status') }, { key: 'actions', label: t('shared.actions'), class: 'text-right' },
] : [ { key: 'name', label: t('resource.nameLabel', { kind: config.value.single }) }, { key: 'code', label: kind === 'methods' ? t('resource.httpMethod') : t('resource.roleCode') }, { key: 'description', label: t('shared.description') }, { key: 'active', label: t('shared.status') }, { key: 'createTime', label: t('shared.createdAt') }, { key: 'actions', label: t('shared.actions'), class: 'text-right' } ])
const editOpen = ref(false), deleteOpen = ref(false), grantsOpen = ref(false), saving = ref(false), formError = ref(''), optionsLoading = ref(false)
const target = shallowRef<Resource | null>(null), tree = shallowRef<MenuTree[]>([]), selected = ref<number[]>([])
const methodOptions = shallowRef<Method[]>([]), typeOptions = shallowRef<NamedOption[]>([]), iconOptions = shallowRef<NamedOption[]>([]), parentOptions = shallowRef<Menu[]>([])
const form = reactive({ name: '', code: '', description: '', method: 'GET', active: true, url: '', type: '', iconId: '', parent: '0', sorted: '1', tips: '', methods: [] as number[], newd: false })
const verbs = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'].map(value => ({ value, label: value }))
const types = computed(() => typeOptions.value.map(item => ({ value: String(item.id), label: item.name })))
const icons = computed(() => iconOptions.value.map(item => ({ value: String(item.id), label: item.name, description: item.code })))
const parents = computed(() => [{ value: '0', label: t('resource.topLevel') }, ...parentOptions.value.map(item => ({ value: String(item.id), label: item.name }))])
function toggleMethod(id: number, checked: boolean) { form.methods = checked ? [...new Set([...form.methods, id])] : form.methods.filter(value => value !== id) }
async function menuOptions() {
  optionsLoading.value = true
  try {
    const [methods, types, icons, parents] = await Promise.all([allOptions<Method>('/api/v1/method'), allOptions<NamedOption>('/api/v1/system/menu/type'), allOptions<NamedOption>('/api/v1/icon'), allOptions<Menu>('/api/v1/menu')])
    methodOptions.value = methods; typeOptions.value = types; iconOptions.value = icons
    parentOptions.value = parents.filter(parent => (parent.parent || 0) === 0 && parent.url === '#' && parent.id !== target.value?.id)
    if (!form.type && types[0]) form.type = String(types[0].id)
    if (!form.iconId && icons[0]) form.iconId = String(icons[0].id)
  } catch (reason) { formError.value = errorMessage(reason) } finally { optionsLoading.value = false }
}
function openEdit(row: Resource | null = null) {
  target.value = row; formError.value = ''
  Object.assign(form, { name: row?.name || '', code: row?.code || '', description: row?.description || '', active: row?.active ?? true, method: row?.method || 'GET', url: row?.url || '', type: String(row?.type?.id || ''), iconId: String(row?.icon?.id || ''), parent: String(row?.parent || 0), sorted: String(row?.sorted || 1), tips: row?.tips || '', methods: row?.methods?.map(method => method.id) || [], newd: row?.newd || false })
  editOpen.value = true
  if (kind === 'menus') void menuOptions()
}
async function save() {
  if (saving.value || optionsLoading.value) return
  if (!form.name.trim()) { formError.value = t('resource.enterName', { kind: config.value.single }); return }
  if (kind === 'menus' && !form.url.trim()) { formError.value = t('resource.enterPath'); return }
  if (kind === 'menus' && (!/^\d+$/.test(form.sorted) || Number(form.sorted) < 1 || !Number.isSafeInteger(Number(form.sorted)))) { formError.value = t('resource.invalidSort'); return }
  if (kind === 'menus' && !form.methods.length) { formError.value = t('resource.selectMethod'); return }
  if (kind === 'menus' && (!types.value.some(option => option.value === form.type) || !icons.value.some(option => option.value === form.iconId))) { formError.value = t('resource.selectTypeAndIcon'); return }
  saving.value = true; formError.value = ''
  try {
    let body: unknown
    if (kind === 'menus') body = target.value ? {
      ...target.value, name: form.name.trim(), url: form.url.trim(), tips: form.tips, description: form.description, parent: Number(form.parent), sorted: Number(form.sorted), active: form.active, newd: form.newd,
      type: { id: Number(form.type) }, icon: { id: Number(form.iconId) }, methods: form.methods.map(id => ({ id })),
    } : { name: form.name.trim(), url: form.url.trim(), tips: form.tips || form.name, description: form.description, parent: Number(form.parent), sorted: Number(form.sorted), level: form.parent === '0' ? 1 : 2, newd: form.newd, type: form.type, iconId: form.iconId, icon: iconOptions.value.find(icon => String(icon.id) === form.iconId)?.code || 'menu', method: form.methods.map(String) }
    else body = { ...target.value, name: form.name.trim(), code: kind === 'methods' ? form.method : form.code, description: form.description, active: form.active, ...(kind === 'methods' ? { method: form.method } : {}) }
    await request(config.value.endpoint, { method: target.value ? 'PUT' : 'POST', body })
    editOpen.value = false; refresh(); toast.show(t(target.value ? 'resource.updated' : 'resource.created', { kind: config.value.single }))
  } catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
function openDelete(row: Resource) { target.value = row; formError.value = ''; deleteOpen.value = true }
async function remove() {
  saving.value = true; formError.value = ''
  try { await request(config.value.endpoint, { method: 'DELETE', query: { id: target.value?.id } }); deleteOpen.value = false; refresh(); toast.show(t('resource.deleted', { kind: config.value.single })) }
  catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
async function openGrants(row: Resource) {
  target.value = row; formError.value = ''; tree.value = []; selected.value = []; grantsOpen.value = true; optionsLoading.value = true
  try { tree.value = await request<MenuTree[]>('/api/v1/role/menus', { query: { id: row.id } }) || []; selected.value = checkedIds(tree.value) }
  catch (reason) { formError.value = errorMessage(reason) } finally { optionsLoading.value = false }
}
function toggle(id: number, value: boolean) { selected.value = toggleTree(tree.value, selected.value, id, value) }
async function saveGrants() {
  saving.value = true; formError.value = ''
  try { await request('/api/v1/role/menus', { method: 'PUT', body: { roleId: target.value?.id, menus: selected.value } }); grantsOpen.value = false; toast.show(t('resource.grantsUpdated')) }
  catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
const verbColor = (method: string) => ({ GET: 'bg-emerald-50 text-emerald-700', POST: 'bg-blue-50 text-blue-700', PUT: 'bg-amber-50 text-amber-700', DELETE: 'bg-rose-50 text-rose-700' })[method as 'GET' | 'POST' | 'PUT' | 'DELETE'] || 'bg-brand-soft text-brand'
</script>
<template>
  <PageHeading :title="config.title" :description="config.description" :badge="t('resource.count', { count: data.totalElements })"><UiButton variant="secondary" :disabled="loading" @click="refresh"><RefreshCw :size="15" />{{ t('shared.refresh') }}</UiButton><UiButton @click="openEdit()"><Plus :size="16" />{{ t('resource.create', { kind: config.single }) }}</UiButton></PageHeading>
  <div v-if="kind === 'roles'" class="panel mb-6 flex items-start gap-4 border-brand/10 bg-gradient-to-r from-brand-soft to-surface p-5"><span class="flex size-10 shrink-0 items-center justify-center rounded-xl bg-surface text-brand"><ShieldCheck :size="21" /></span><div><h2 class="text-sm font-semibold">{{ t('resource.rolesHintTitle') }}</h2><p class="mt-1.5 text-xs leading-6 text-muted">{{ t('resource.rolesHint') }}</p></div></div>
  <section class="panel overflow-hidden">
    <div class="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4"><div><h2 class="text-sm font-semibold">{{ t('resource.allTitle', { kind: config.single }) }}</h2><p class="mt-1 text-[11px] text-muted">{{ kind === 'roles' ? t('resource.rolesCaption') : kind === 'menus' ? t('resource.menusCaption') : t('resource.methodsCaption') }}</p></div><div class="flex w-full items-center gap-2 rounded-xl border border-line bg-canvas/40 px-3 sm:w-64"><Search :size="15" class="text-muted" /><input v-model="search" :aria-label="t('resource.filter', { kind: config.single })" :placeholder="t('resource.filterPlaceholder', { kind: config.single })" class="w-full bg-transparent py-2.5 text-xs outline-none" /></div></div>
    <DataTable
      :rows="filtered"
      :columns="columns"
      :loading="loading"
      :error="error"
      :empty-title="t('resource.emptyTitle', { kind: config.single })"
      :empty-description="t('resource.emptyDescription')"
      @retry="refresh"
    >
      <template #name="{ row }"><p class="font-medium">{{ row.name }}</p><p class="mt-1 font-mono text-[10px] text-muted">{{ kind === 'menus' ? (row.parent ? t('resource.parentId', { id: row.parent }) : t('resource.topLevel')) : `ID ${String(row.id).padStart(3, '0')}` }}</p></template>
      <template #code="{ row }"><span v-if="kind === 'methods'" class="rounded-md px-2 py-1 font-mono text-[11px] font-semibold" :class="verbColor(row.method || row.code || '')">{{ row.method || row.code }}</span><code v-else class="rounded-lg bg-canvas px-2 py-1 text-[11px] text-muted">{{ row.code || '—' }}</code></template>
      <template #description="{ row }"><span class="line-clamp-2 max-w-xs text-xs leading-5 text-muted">{{ row.description || t('resource.noDescription') }}</span></template>
      <template #url="{ row }"><span class="inline-flex max-w-60 items-center gap-1 truncate rounded-lg bg-canvas px-2 py-1 font-mono text-[11px] text-muted">{{ row.url }}<ArrowUpRight v-if="row.url !== '#'" :size="11" class="shrink-0" /></span></template>
      <template #type="{ row }"><span class="badge">{{ row.type?.name || t('resource.notSet') }}</span></template>
      <template #methods="{ row }"><div class="flex max-w-48 flex-wrap gap-1"><span v-for="method in row.methods" :key="method.id" class="rounded px-1.5 py-0.5 font-mono text-[10px]" :class="verbColor(method.method)">{{ method.method }}</span></div></template>
      <template #active="{ row }"><StatusBadge :active="row.active" /></template>
      <template #createTime="{ row }"><span class="text-xs text-muted">{{ dateLabel(row.createTime) }}</span></template>
      <template #actions="{ row }"><div class="flex justify-end gap-0.5"><button v-if="kind === 'roles'" type="button" class="table-action" @click="openGrants(row)"><KeyRound :size="13" />{{ t('resource.grant') }}</button><button type="button" class="table-action" :aria-label="t('resource.editNamed', { kind: config.single, name: row.name })" @click="openEdit(row)"><Pencil :size="14" /></button><button type="button" class="table-action hover:text-rose-600" :aria-label="t('resource.deleteNamed', { kind: config.single, name: row.name })" @click="openDelete(row)"><Trash2 :size="14" /></button></div></template>
    </DataTable><PageControls
      v-model:page="page"
      :size="size"
      :total="data.totalElements"
      :pages="data.totalPages"
      :loading="loading"
      @size="setSize"
    />
  </section>
  <UiDialog
    v-model="editOpen"
    :title="t(target ? 'resource.editTitle' : 'resource.create', { kind: config.single })"
    :description="kind === 'menus' ? t('resource.menuFormDescription') : t('resource.formDescription')"
    :wide="kind === 'menus'"
    :busy="saving"
  >
    <form id="resource-form" class="space-y-5" novalidate @submit.prevent="save">
      <UiField v-model="form.name" :label="t('resource.nameLabel', { kind: config.single })" required :placeholder="t('resource.namePlaceholder')" />
      <UiField v-if="kind === 'roles'" v-model="form.code" :label="t('resource.roleCode')" :placeholder="t('resource.rolePlaceholder')" />
      <UiSelect v-if="kind === 'methods'" v-model="form.method" :label="t('resource.httpMethod')" :options="verbs" />
      <template v-if="kind === 'menus'">
        <UiField v-model="form.url" :label="t('resource.menuPath')" :placeholder="t('resource.menuPathPlaceholder')" required /><div class="grid gap-4 sm:grid-cols-2">
          <UiSelect
            v-model="form.type"
            :label="t('resource.menuType')"
            :options="types"
            :disabled="optionsLoading"
            required
            :placeholder="t('resource.menuTypePlaceholder')"
          />
          <UiSelect
            v-model="form.iconId"
            :label="t('resource.icon')"
            :options="icons"
            :disabled="optionsLoading"
            required
            :placeholder="t('resource.iconPlaceholder')"
          />
          <UiSelect v-model="form.parent" :label="t('resource.parentGroup')" :options="parents" :disabled="optionsLoading" /><UiField
            v-model="form.sorted"
            :label="t('resource.sort')"
            type="number"
            min="1"
            required
          />
        </div><fieldset>
          <legend class="field-label">{{ t('resource.allowedMethods') }}</legend><div class="flex flex-wrap gap-2">
            <UiCheckbox
              v-for="method in methodOptions"
              :key="method.id"
              :label="method.method"
              :checked="form.methods.includes(method.id)"
              :disabled="optionsLoading || saving"
              class="rounded-xl border px-3 py-2 text-xs"
              :class="form.methods.includes(method.id) ? 'border-brand/30 bg-brand-soft' : 'border-line'"
              @update:checked="toggleMethod(method.id, $event)"
            /><p v-if="optionsLoading" class="text-muted">{{ t('resource.loadingOptions') }}</p>
          </div>
        </fieldset><UiField v-model="form.tips" :label="t('resource.tips')" :placeholder="t('resource.tipsPlaceholder')" />
      </template>
      <UiField v-model="form.description" :label="t('shared.description')" textarea :placeholder="t('resource.descriptionPlaceholder')" /><UiSwitch v-if="target || kind !== 'menus'" v-model="form.active" :label="t('resource.enable', { kind: config.single })" :disabled="saving" /><p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form><template #footer><UiButton variant="secondary" :disabled="saving" @click="editOpen = false">{{ t('shared.cancel') }}</UiButton><UiButton type="submit" form="resource-form" :loading="saving" :disabled="optionsLoading">{{ target ? t('resource.saveChanges') : t('resource.create', { kind: config.single }) }}</UiButton></template>
  </UiDialog>
  <UiDialog
    v-model="grantsOpen"
    :title="t('resource.grantsTitle')"
    :description="t('resource.grantsDescription', { name: target?.name || t('resource.someoneRole') })"
    wide
    :busy="saving"
  >
    <div class="mb-4 flex items-center justify-between rounded-xl bg-canvas px-4 py-3"><i18n-t keypath="resource.selectedCount" tag="p" class="text-xs text-muted"><template #count><strong class="text-brand">{{ selected.length }}</strong></template></i18n-t><div class="flex gap-3 text-xs"><button type="button" class="font-medium text-brand" @click="selected = flattenTree(tree).map(item => item.id)">{{ t('resource.selectAll') }}</button><button type="button" class="text-muted" @click="selected = []">{{ t('resource.clearAll') }}</button></div></div><p v-if="optionsLoading" class="py-10 text-center text-muted">{{ t('resource.loadingGrants') }}</p><TreeChoices v-else :nodes="tree" :selected="selected" @toggle="toggle" /><p v-if="!optionsLoading && !tree.length" class="py-8 text-center text-muted">{{ t('resource.noGrantOptions') }}</p><p v-if="formError" class="mt-4 text-xs text-rose-600" role="alert">{{ formError }}</p><template #footer><UiButton variant="secondary" :disabled="saving" @click="grantsOpen = false">{{ t('shared.cancel') }}</UiButton><UiButton :loading="saving" :disabled="optionsLoading" @click="saveGrants"><Check :size="15" />{{ t('resource.saveGrants') }}</UiButton></template>
  </UiDialog>
  <UiDialog v-model="deleteOpen" :title="t('resource.deleteTitle', { kind: config.single })" :busy="saving"><div class="flex items-start gap-4"><span class="flex size-11 shrink-0 items-center justify-center rounded-xl bg-rose-50 text-rose-500"><AlertTriangle :size="22" /></span><div><i18n-t keypath="shared.deleteConfirm" tag="p"><template #name><strong>{{ target?.name }}</strong></template></i18n-t><p class="mt-2 text-xs leading-6 text-muted">{{ t('resource.deleteWarning') }}</p></div></div><p v-if="formError" class="mt-4 text-xs text-rose-600" role="alert">{{ formError }}</p><template #footer><UiButton variant="secondary" :disabled="saving" @click="deleteOpen = false">{{ t('shared.cancel') }}</UiButton><UiButton variant="danger" :loading="saving" @click="remove">{{ t('shared.confirmDelete') }}</UiButton></template></UiDialog>
</template>
