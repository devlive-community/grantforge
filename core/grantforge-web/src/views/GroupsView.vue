<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onWatcherCleanup, ref, shallowRef, watch } from 'vue'
import { AlertTriangle, Pencil, Plus, RefreshCw, Search, Trash2, Users } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { useFieldErrors, type FieldErrors } from '@/lib/fieldErrors'
import { vPermission } from '@/lib/permission'
import { initials } from '@/lib/format'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import DataTable from '@/components/DataTable.vue'
import PageControls from '@/components/PageControls.vue'
import UiButton from '@/components/UiButton.vue'
import UiCheckbox from '@/components/UiCheckbox.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiField from '@/components/UiField.vue'

type Group = components['schemas']['GroupResponse']
type GroupPage = components['schemas']['PageResultGroupResponse']
type MemberPage = components['schemas']['PageResultMemberResponse']
type User = components['schemas']['UserResponse']
type UserPage = components['schemas']['PageResultUserResponse']
type Change = components['schemas']['MemberChangeResponse']

/** How many members and candidates the member dialog lists; searching narrows larger sets down. */
const LIST_SIZE = 50
const { t } = useI18n(), toast = useToast()
const page = ref(1), size = ref(20), revision = ref(0), search = ref(''), text = ref('')
const result = shallowRef<GroupPage>({ items: [], page: 1, size: 20, total: 0 })
const loading = ref(false), error = ref('')
const pages = computed(() => Math.ceil(result.value.total / size.value))
const columns = computed(() => [{ key: 'group', label: t('groups.columnGroup') }, { key: 'description', label: t('groups.descriptionLabel') },
  { key: 'members', label: t('groups.columnMembers') }, { key: 'actions', label: t('shared.actions'), class: 'text-right' }])
const dialog = ref<'create' | 'edit' | 'delete' | 'members' | null>(null), target = shallowRef<Group | null>(null)
const saving = ref(false), formError = ref(''), form = ref({ code: '', name: '', description: '' })
const { errors: fieldErrors, invalid } = useFieldErrors(() => form.value, problems)
const members = shallowRef<MemberPage>({ items: [], page: 1, size: LIST_SIZE, total: 0 }), memberSearch = ref(''), selectedMembers = ref<string[]>([])
const candidates = shallowRef<User[]>([]), userSearch = ref(''), selectedUsers = ref<string[]>([])
const memberIds = computed(() => new Set(members.value.items.map(member => member.accountId)))

watch([page, size, text, revision], ([current, limit, query]) => {
  const controller = new AbortController()
  onWatcherCleanup(() => controller.abort())
  loading.value = true; error.value = ''
  void request<GroupPage>('/api/v1/groups', { query: { q: query || undefined, page: current, size: limit }, signal: controller.signal })
    .then(found => { if (!controller.signal.aborted) result.value = found })
    .catch(reason => { if (!controller.signal.aborted) error.value = errorMessage(reason) })
    .finally(() => { if (!controller.signal.aborted) loading.value = false })
}, { immediate: true })

// Searches wait for a pause in typing, so each keystroke does not query the server.
function debounced(source: typeof search, action: (value: string) => void) {
  let pending: ReturnType<typeof setTimeout> | undefined
  watch(source, value => { clearTimeout(pending); pending = setTimeout(() => action(value.trim()), 300) })
}
debounced(search, value => { page.value = 1; text.value = value })
debounced(memberSearch, () => { if (dialog.value === 'members') void loadMembers() })
debounced(userSearch, () => { if (dialog.value === 'members') void loadCandidates() })

function refresh() { revision.value++ }
function setSize(value: number) { page.value = 1; size.value = value }
function open(kind: 'create' | 'edit' | 'delete', group: Group | null = null) {
  target.value = group; formError.value = ''; fieldErrors.value = {}; dialog.value = kind
  form.value = { code: group?.code ?? '', name: group?.name ?? '', description: group?.description ?? '' }
}
async function openMembers(group: Group) {
  target.value = group; formError.value = ''; fieldErrors.value = {}; memberSearch.value = ''; userSearch.value = ''
  selectedMembers.value = []; selectedUsers.value = []; dialog.value = 'members'
  await Promise.all([loadMembers(), loadCandidates()])
}
async function loadMembers() {
  if (!target.value) return
  try {
    members.value = await request<MemberPage>(`/api/v1/groups/${encodeURIComponent(target.value.id)}/members`,
      { query: { q: memberSearch.value.trim() || undefined, page: 1, size: LIST_SIZE } })
  } catch (reason) { formError.value = errorMessage(reason) }
}
async function loadCandidates() {
  try {
    candidates.value = (await request<UserPage>('/api/v1/users', { query: { q: userSearch.value.trim() || undefined, page: 1, size: LIST_SIZE } })).items
  } catch (reason) { formError.value = errorMessage(reason) }
}
const toggled = (list: string[], id: string, checked: boolean) => checked ? [...new Set([...list, id])] : list.filter(other => other !== id)
function toggleMember(id: string, checked: boolean) { selectedMembers.value = toggled(selectedMembers.value, id, checked) }
function toggleUser(id: string, checked: boolean) { selectedUsers.value = toggled(selectedUsers.value, id, checked) }
async function run(action: () => Promise<unknown>, done: () => string, close = true) {
  if (saving.value) return
  saving.value = true; formError.value = ''; fieldErrors.value = {}
  try {
    await action()
    if (close) dialog.value = null
    toast.show(done()); refresh()
  } catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
function problems(): FieldErrors {
  const found: FieldErrors = {}
  if (!form.value.code.trim()) found.code = t('groups.enterCode')
  if (!form.value.name.trim()) found.name = t('groups.enterName')
  return found
}
function save() {
  formError.value = ''
  if (invalid()) return
  const body = { ...form.value }
  if (dialog.value === 'create') void run(() => request<Group>('/api/v1/groups', { method: 'POST', body }), () => t('groups.created'))
  else if (target.value) {
    const id = target.value.id
    void run(() => request<Group>(`/api/v1/groups/${encodeURIComponent(id)}`, { method: 'PUT', body }), () => t('groups.saved'))
  }
}
function remove() {
  const group = target.value
  if (group) void run(() => request<null>(`/api/v1/groups/${encodeURIComponent(group.id)}`, { method: 'DELETE' }), () => t('groups.deleted'))
}
function changeMembers(adding: boolean) {
  const group = target.value, ids = adding ? selectedUsers.value : selectedMembers.value
  if (!group || !ids.length) return
  let changed = 0
  void run(async () => {
    const path = `/api/v1/groups/${encodeURIComponent(group.id)}/members${adding ? '' : '/remove'}`
    changed = (await request<Change>(path, { method: 'POST', body: { accountIds: ids } })).changed
    selectedUsers.value = []; selectedMembers.value = []
    await loadMembers()
  }, () => adding ? t('groups.added', { count: changed }) : t('groups.removed', { count: changed }), false)
}
const name = (user: { displayName?: string | null; username: string }) => user.displayName || user.username
</script>
<template>
  <PageHeading :title="t('titles.groups')" :description="t('groups.description')" :badge="t('groups.count', { count: result.total })"><UiButton variant="secondary" :disabled="loading" @click="refresh"><RefreshCw :size="15" />{{ t('shared.refresh') }}</UiButton><UiButton v-permission="'system.group.btn.create'" @click="open('create')"><Plus :size="16" />{{ t('groups.create') }}</UiButton></PageHeading>
  <section class="panel overflow-hidden">
    <div class="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4"><div><h2 class="text-sm font-semibold">{{ t('groups.all') }}</h2><p class="mt-1 text-[11px] text-muted">{{ t('groups.allCaption') }}</p></div><div class="flex w-full items-center gap-2 rounded-xl border border-line bg-canvas/40 px-3 sm:w-64"><Search :size="15" class="text-muted" /><input v-model="search" :aria-label="t('groups.search')" :placeholder="t('groups.searchPlaceholder')" class="w-full bg-transparent py-2.5 text-xs outline-none" /></div></div>
    <DataTable
      :rows="result.items"
      :columns="columns"
      :loading="loading"
      :error="error"
      :empty-title="t('groups.emptyTitle')"
      :empty-description="t('groups.emptyDescription')"
      @retry="refresh"
    >
      <template #group="{ row }"><div class="flex items-center gap-3"><span class="flex size-9 shrink-0 items-center justify-center rounded-xl bg-brand-soft text-[11px] font-semibold text-brand">{{ initials(row.name) }}</span><div><p class="font-medium">{{ row.name }}</p><p class="mt-1 font-mono text-[10px] text-muted">{{ row.code }}</p></div></div></template>
      <template #description="{ row }"><span class="text-xs text-muted">{{ row.description || '—' }}</span></template>
      <template #members="{ row }"><span class="text-xs">{{ row.members }}</span></template>
      <template #actions="{ row }">
        <div class="flex justify-end gap-0.5">
          <button
            v-permission="'system.group.btn.members'"
            type="button"
            class="table-action"
            :aria-label="t('groups.membersNamed', { name: row.name })"
            @click="openMembers(row)"
          >
            <Users :size="14" />{{ t('groups.members') }}
          </button>
          <button
            v-permission="'system.group.btn.edit'"
            type="button"
            class="table-action"
            :aria-label="t('groups.editNamed', { name: row.name })"
            @click="open('edit', row)"
          >
            <Pencil :size="14" />
          </button>
          <button
            v-permission="'system.group.btn.delete'"
            type="button"
            class="table-action hover:text-rose-600"
            :aria-label="t('groups.deleteNamed', { name: row.name })"
            @click="open('delete', row)"
          >
            <Trash2 :size="14" />
          </button>
        </div>
      </template>
    </DataTable><PageControls
      v-model:page="page"
      :size="size"
      :total="result.total"
      :pages="pages"
      :loading="loading"
      @size="setSize"
    />
  </section>
  <UiDialog :model-value="dialog === 'create' || dialog === 'edit'" :title="dialog === 'edit' ? t('groups.editTitle') : t('groups.create')" :busy="saving" @update:model-value="dialog = null">
    <form id="group-form" class="space-y-5" novalidate @submit.prevent="save">
      <div class="grid gap-5 sm:grid-cols-2">
        <UiField
          v-model="form.name"
          :label="t('groups.name')"
          :placeholder="t('groups.namePlaceholder')"
          required
          :error="fieldErrors.name"
        /><UiField
          v-model="form.code"
          :label="t('groups.code')"
          :placeholder="t('groups.codePlaceholder')"
          required
          :error="fieldErrors.code"
        />
      </div>
      <UiField v-model="form.description" :label="t('groups.descriptionLabel')" textarea />
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="dialog = null">{{ t('shared.cancel') }}</UiButton><UiButton type="submit" form="group-form" :loading="saving">{{ dialog === 'edit' ? t('tenants.save') : t('groups.create') }}</UiButton></template>
  </UiDialog>
  <UiDialog :model-value="dialog === 'delete'" :title="t('groups.deleteTitle')" :busy="saving" @update:model-value="dialog = null">
    <div class="flex gap-4"><span class="flex size-11 shrink-0 items-center justify-center rounded-xl bg-rose-50 text-rose-500"><AlertTriangle :size="22" /></span><p class="text-sm leading-6">{{ t('groups.deleteWarning', { name: target?.name, count: target?.members ?? 0 }) }}</p></div><p v-if="formError" class="mt-4 text-xs text-rose-600" role="alert">{{ formError }}</p>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="dialog = null">{{ t('shared.cancel') }}</UiButton><UiButton variant="danger" :loading="saving" @click="remove">{{ t('groups.deleteTitle') }}</UiButton></template>
  </UiDialog>
  <UiDialog :model-value="dialog === 'members'" :title="t('groups.membersTitle', { name: target?.name })" :busy="saving" @update:model-value="dialog = null">
    <div class="space-y-6">
      <section aria-labelledby="current-members">
        <div class="mb-2 flex flex-wrap items-center justify-between gap-2"><h3 id="current-members" class="text-xs font-semibold">{{ t('groups.currentMembers') }}</h3><UiButton variant="danger" :disabled="!selectedMembers.length || saving" @click="changeMembers(false)">{{ t('groups.removeSelected', { count: selectedMembers.length }) }}</UiButton></div>
        <input v-model="memberSearch" :aria-label="t('groups.searchMembers')" :placeholder="t('groups.searchMembers')" class="mb-2 w-full rounded-xl border border-line bg-canvas/40 px-3 py-2 text-xs outline-none" />
        <div class="max-h-48 space-y-1 overflow-y-auto rounded-xl border border-line p-2">
          <UiCheckbox
            v-for="member in members.items"
            :key="member.accountId"
            :label="name(member)"
            :checked="selectedMembers.includes(member.accountId)"
            :disabled="saving"
            class="rounded-lg px-2 py-1.5"
            @update:checked="toggleMember(member.accountId, $event)"
          >
            <span class="text-xs">{{ name(member) }}</span><span class="ml-2 font-mono text-[10px] text-muted">{{ member.username }}</span>
          </UiCheckbox>
          <p v-if="!members.items.length" class="py-4 text-center text-xs text-muted">{{ t('groups.noMembers') }}</p>
        </div>
        <p v-if="members.total > members.items.length" class="mt-1 text-[11px] text-muted">{{ t('groups.membersLimited', { total: members.total, shown: members.items.length }) }}</p>
      </section>
      <section aria-labelledby="add-members">
        <div class="mb-2 flex flex-wrap items-center justify-between gap-2"><h3 id="add-members" class="text-xs font-semibold">{{ t('groups.addMembers') }}</h3><UiButton :disabled="!selectedUsers.length || saving" @click="changeMembers(true)">{{ t('groups.addSelected', { count: selectedUsers.length }) }}</UiButton></div>
        <input v-model="userSearch" :aria-label="t('groups.searchUsers')" :placeholder="t('groups.searchUsersPlaceholder')" class="mb-2 w-full rounded-xl border border-line bg-canvas/40 px-3 py-2 text-xs outline-none" />
        <div class="max-h-48 space-y-1 overflow-y-auto rounded-xl border border-line p-2">
          <UiCheckbox
            v-for="user in candidates.filter(candidate => !memberIds.has(candidate.id))"
            :key="user.id"
            :label="name(user)"
            :checked="selectedUsers.includes(user.id)"
            :disabled="saving"
            class="rounded-lg px-2 py-1.5"
            @update:checked="toggleUser(user.id, $event)"
          >
            <span class="text-xs">{{ name(user) }}</span><span class="ml-2 font-mono text-[10px] text-muted">{{ user.username }}</span>
          </UiCheckbox>
          <p v-if="!candidates.some(candidate => !memberIds.has(candidate.id))" class="py-4 text-center text-xs text-muted">{{ t('groups.noUsers') }}</p>
        </div>
      </section>
      <p v-if="formError" class="text-xs text-rose-600" role="alert">{{ formError }}</p>
    </div>
  </UiDialog>
</template>
