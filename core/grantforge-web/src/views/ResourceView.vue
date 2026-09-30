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
const config = computed(() => ({
  roles: { title: '角色管理', single: '角色', endpoint: '/api/v1/role', description: '以职责组织权限，让每个角色拥有恰到好处的访问范围。' },
  methods: { title: '请求方式', single: '请求方式', endpoint: '/api/v1/method', description: '管理接口支持的 HTTP 方法，定义更精确的访问边界。' },
  menus: { title: '菜单管理', single: '菜单', endpoint: '/api/v1/menu', description: '连接导航、资源与请求方式，构建你的权限目录。' },
})[kind])
const { page, size, data, rows, loading, error, refresh, setSize } = usePage<Resource>(() => config.value.endpoint)
const search = ref(''), toast = useToast()
const filtered = computed(() => rows.value.filter(row => `${row.name} ${row.code || ''} ${row.url || ''}`.toLowerCase().includes(search.value.toLowerCase())))
const columns = computed<TableColumn[]>(() => kind === 'menus' ? [
  { key: 'name', label: '菜单名称' }, { key: 'url', label: '路径' }, { key: 'type', label: '类型' }, { key: 'methods', label: '请求方式' }, { key: 'active', label: '状态' }, { key: 'actions', label: '操作', class: 'text-right' },
] : [ { key: 'name', label: `${config.value.single}名称` }, { key: 'code', label: kind === 'methods' ? 'HTTP 方法' : '角色编码' }, { key: 'description', label: '描述' }, { key: 'active', label: '状态' }, { key: 'createTime', label: '创建时间' }, { key: 'actions', label: '操作', class: 'text-right' } ])
const editOpen = ref(false), deleteOpen = ref(false), grantsOpen = ref(false), saving = ref(false), formError = ref(''), optionsLoading = ref(false)
const target = shallowRef<Resource | null>(null), tree = shallowRef<MenuTree[]>([]), selected = ref<number[]>([])
const methodOptions = shallowRef<Method[]>([]), typeOptions = shallowRef<NamedOption[]>([]), iconOptions = shallowRef<NamedOption[]>([]), parentOptions = shallowRef<Menu[]>([])
const form = reactive({ name: '', code: '', description: '', method: 'GET', active: true, url: '', type: '', iconId: '', parent: '0', sorted: '1', tips: '', methods: [] as number[], newd: false })
const verbs = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS'].map(value => ({ value, label: value }))
const types = computed(() => typeOptions.value.map(item => ({ value: String(item.id), label: item.name })))
const icons = computed(() => iconOptions.value.map(item => ({ value: String(item.id), label: item.name, description: item.code })))
const parents = computed(() => [{ value: '0', label: '顶级菜单' }, ...parentOptions.value.map(item => ({ value: String(item.id), label: item.name }))])
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
  if (!form.name.trim()) { formError.value = `请输入${config.value.single}名称`; return }
  if (kind === 'menus' && !form.url.trim()) { formError.value = '请输入菜单路径'; return }
  if (kind === 'menus' && (!/^\d+$/.test(form.sorted) || Number(form.sorted) < 1 || !Number.isSafeInteger(Number(form.sorted)))) { formError.value = '排序必须是大于或等于 1 的整数'; return }
  if (kind === 'menus' && !form.methods.length) { formError.value = '请选择至少一种请求方式'; return }
  if (kind === 'menus' && (!types.value.some(option => option.value === form.type) || !icons.value.some(option => option.value === form.iconId))) { formError.value = '请选择菜单类型和图标'; return }
  saving.value = true; formError.value = ''
  try {
    let body: unknown
    if (kind === 'menus') body = target.value ? {
      ...target.value, name: form.name.trim(), url: form.url.trim(), tips: form.tips, description: form.description, parent: Number(form.parent), sorted: Number(form.sorted), active: form.active, newd: form.newd,
      type: { id: Number(form.type) }, icon: { id: Number(form.iconId) }, methods: form.methods.map(id => ({ id })),
    } : { name: form.name.trim(), url: form.url.trim(), tips: form.tips || form.name, description: form.description, parent: Number(form.parent), sorted: Number(form.sorted), level: form.parent === '0' ? 1 : 2, newd: form.newd, type: form.type, iconId: form.iconId, icon: iconOptions.value.find(icon => String(icon.id) === form.iconId)?.code || 'menu', method: form.methods.map(String) }
    else body = { ...target.value, name: form.name.trim(), code: kind === 'methods' ? form.method : form.code, description: form.description, active: form.active, ...(kind === 'methods' ? { method: form.method } : {}) }
    await request(config.value.endpoint, { method: target.value ? 'PUT' : 'POST', body })
    editOpen.value = false; refresh(); toast.show(`${config.value.single}已${target.value ? '更新' : '创建'}`)
  } catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
function openDelete(row: Resource) { target.value = row; formError.value = ''; deleteOpen.value = true }
async function remove() {
  saving.value = true; formError.value = ''
  try { await request(config.value.endpoint, { method: 'DELETE', query: { id: target.value?.id } }); deleteOpen.value = false; refresh(); toast.show(`${config.value.single}已删除`) }
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
  try { await request('/api/v1/role/menus', { method: 'PUT', body: { roleId: target.value?.id, menus: selected.value } }); grantsOpen.value = false; toast.show('角色权限已更新') }
  catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
const verbColor = (method: string) => ({ GET: 'bg-emerald-50 text-emerald-700', POST: 'bg-blue-50 text-blue-700', PUT: 'bg-amber-50 text-amber-700', DELETE: 'bg-rose-50 text-rose-700' })[method as 'GET' | 'POST' | 'PUT' | 'DELETE'] || 'bg-brand-soft text-brand'
</script>
<template>
  <PageHeading :title="config.title" :description="config.description" :badge="`${data.totalElements} 项`"><UiButton variant="secondary" :disabled="loading" @click="refresh"><RefreshCw :size="15" />刷新</UiButton><UiButton @click="openEdit()"><Plus :size="16" />创建{{ config.single }}</UiButton></PageHeading>
  <div v-if="kind === 'roles'" class="panel mb-6 flex items-start gap-4 border-brand/10 bg-gradient-to-r from-brand-soft to-surface p-5"><span class="flex size-10 shrink-0 items-center justify-center rounded-xl bg-surface text-brand"><ShieldCheck :size="21" /></span><div><h2 class="text-sm font-semibold">先定义职责，再分配权限</h2><p class="mt-1.5 text-xs leading-6 text-muted">角色连接用户与权限。通过菜单授权，为不同职责配置清晰、可维护的访问范围。</p></div></div>
  <section class="panel overflow-hidden">
    <div class="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4"><div><h2 class="text-sm font-semibold">全部{{ config.single }}</h2><p class="mt-1 text-[11px] text-muted">{{ kind === 'roles' ? '让授权与业务职责保持一致。' : kind === 'menus' ? '以分组与路径组织访问资源。' : '以方法区分读取、创建、更新与删除。' }}</p></div><div class="flex w-full items-center gap-2 rounded-xl border border-line bg-canvas/40 px-3 sm:w-64"><Search :size="15" class="text-muted" /><input v-model="search" :aria-label="`筛选当前页${config.single}`" :placeholder="`筛选当前页${config.single}…`" class="w-full bg-transparent py-2.5 text-xs outline-none" /></div></div>
    <DataTable
      :rows="filtered"
      :columns="columns"
      :loading="loading"
      :error="error"
      :empty-title="`暂无${config.single}`"
      empty-description="创建新记录，或者调整当前页筛选条件。"
      @retry="refresh"
    >
      <template #name="{ row }"><p class="font-medium">{{ row.name }}</p><p class="mt-1 font-mono text-[10px] text-muted">{{ kind === 'menus' ? (row.parent ? `上级 #${row.parent}` : '顶级菜单') : `ID ${String(row.id).padStart(3, '0')}` }}</p></template>
      <template #code="{ row }"><span v-if="kind === 'methods'" class="rounded-md px-2 py-1 font-mono text-[11px] font-semibold" :class="verbColor(row.method || row.code || '')">{{ row.method || row.code }}</span><code v-else class="rounded-lg bg-canvas px-2 py-1 text-[11px] text-muted">{{ row.code || '—' }}</code></template>
      <template #description="{ row }"><span class="line-clamp-2 max-w-xs text-xs leading-5 text-muted">{{ row.description || '暂无描述' }}</span></template>
      <template #url="{ row }"><span class="inline-flex max-w-60 items-center gap-1 truncate rounded-lg bg-canvas px-2 py-1 font-mono text-[11px] text-muted">{{ row.url }}<ArrowUpRight v-if="row.url !== '#'" :size="11" class="shrink-0" /></span></template>
      <template #type="{ row }"><span class="badge">{{ row.type?.name || '未设置' }}</span></template>
      <template #methods="{ row }"><div class="flex max-w-48 flex-wrap gap-1"><span v-for="method in row.methods" :key="method.id" class="rounded px-1.5 py-0.5 font-mono text-[10px]" :class="verbColor(method.method)">{{ method.method }}</span></div></template>
      <template #active="{ row }"><StatusBadge :active="row.active" /></template>
      <template #createTime="{ row }"><span class="text-xs text-muted">{{ dateLabel(row.createTime) }}</span></template>
      <template #actions="{ row }"><div class="flex justify-end gap-0.5"><button v-if="kind === 'roles'" type="button" class="table-action" @click="openGrants(row)"><KeyRound :size="13" />授权</button><button type="button" class="table-action" :aria-label="`编辑${config.single} ${row.name}`" @click="openEdit(row)"><Pencil :size="14" /></button><button type="button" class="table-action hover:text-rose-600" :aria-label="`删除${config.single} ${row.name}`" @click="openDelete(row)"><Trash2 :size="14" /></button></div></template>
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
    :title="`${target ? '编辑' : '创建'}${config.single}`"
    :description="kind === 'menus' ? '配置菜单的路径、类型和允许的请求方式。' : '清晰的名称和描述可以帮助团队理解访问职责。'"
    :wide="kind === 'menus'"
    :busy="saving"
  >
    <form id="resource-form" class="space-y-5" novalidate @submit.prevent="save">
      <UiField v-model="form.name" :label="`${config.single}名称`" required placeholder="输入清晰易懂的名称" />
      <UiField v-if="kind === 'roles'" v-model="form.code" label="角色编码" placeholder="例如 PROJECT_ADMIN" />
      <UiSelect v-if="kind === 'methods'" v-model="form.method" label="HTTP 方法" :options="verbs" />
      <template v-if="kind === 'menus'">
        <UiField v-model="form.url" label="菜单路径" placeholder="/admin/example，分组使用 #" required /><div class="grid gap-4 sm:grid-cols-2">
          <UiSelect
            v-model="form.type"
            label="菜单类型"
            :options="types"
            :disabled="optionsLoading"
            required
            placeholder="选择类型"
          />
          <UiSelect
            v-model="form.iconId"
            label="图标"
            :options="icons"
            :disabled="optionsLoading"
            required
            placeholder="选择图标"
          />
          <UiSelect v-model="form.parent" label="上级分组" :options="parents" :disabled="optionsLoading" /><UiField
            v-model="form.sorted"
            label="排序"
            type="number"
            min="1"
            required
          />
        </div><fieldset>
          <legend class="field-label">允许的请求方式</legend><div class="flex flex-wrap gap-2">
            <UiCheckbox
              v-for="method in methodOptions"
              :key="method.id"
              :label="method.method"
              :checked="form.methods.includes(method.id)"
              :disabled="optionsLoading || saving"
              class="rounded-xl border px-3 py-2 text-xs"
              :class="form.methods.includes(method.id) ? 'border-brand/30 bg-brand-soft' : 'border-line'"
              @update:checked="toggleMethod(method.id, $event)"
            /><p v-if="optionsLoading" class="text-muted">正在获取可选项…</p>
          </div>
        </fieldset><UiField v-model="form.tips" label="提示文字" placeholder="简短说明这个菜单的用途" />
      </template>
      <UiField v-model="form.description" label="描述" textarea placeholder="补充用途或职责说明（可选）" /><UiSwitch v-if="target || kind !== 'menus'" v-model="form.active" :label="`启用${config.single}`" :disabled="saving" /><p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form><template #footer><UiButton variant="secondary" :disabled="saving" @click="editOpen = false">取消</UiButton><UiButton type="submit" form="resource-form" :loading="saving" :disabled="optionsLoading">{{ target ? '保存修改' : `创建${config.single}` }}</UiButton></template>
  </UiDialog>
  <UiDialog
    v-model="grantsOpen"
    title="角色授权"
    :description="`配置 ${target?.name || '角色'} 可以访问的菜单与资源。`"
    wide
    :busy="saving"
  >
    <div class="mb-4 flex items-center justify-between rounded-xl bg-canvas px-4 py-3"><p class="text-xs text-muted">已选择 <strong class="text-brand">{{ selected.length }}</strong> 项权限</p><div class="flex gap-3 text-xs"><button type="button" class="font-medium text-brand" @click="selected = flattenTree(tree).map(item => item.id)">全选</button><button type="button" class="text-muted" @click="selected = []">清空</button></div></div><p v-if="optionsLoading" class="py-10 text-center text-muted">正在加载权限目录…</p><TreeChoices v-else :nodes="tree" :selected="selected" @toggle="toggle" /><p v-if="!optionsLoading && !tree.length" class="py-8 text-center text-muted">暂无可授权的菜单，请先创建菜单。</p><p v-if="formError" class="mt-4 text-xs text-rose-600" role="alert">{{ formError }}</p><template #footer><UiButton variant="secondary" :disabled="saving" @click="grantsOpen = false">取消</UiButton><UiButton :loading="saving" :disabled="optionsLoading" @click="saveGrants"><Check :size="15" />保存权限</UiButton></template>
  </UiDialog>
  <UiDialog v-model="deleteOpen" :title="`删除${config.single}`" :busy="saving"><div class="flex items-start gap-4"><span class="flex size-11 shrink-0 items-center justify-center rounded-xl bg-rose-50 text-rose-500"><AlertTriangle :size="22" /></span><div><p>确定删除 <strong>{{ target?.name }}</strong> 吗？</p><p class="mt-2 text-xs leading-6 text-muted">此操作无法撤销，请确认相关用户或角色不再依赖这条记录。</p></div></div><p v-if="formError" class="mt-4 text-xs text-rose-600" role="alert">{{ formError }}</p><template #footer><UiButton variant="secondary" :disabled="saving" @click="deleteOpen = false">取消</UiButton><UiButton variant="danger" :loading="saving" @click="remove">确认删除</UiButton></template></UiDialog>
</template>
