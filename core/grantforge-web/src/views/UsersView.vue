<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, ref, shallowRef } from 'vue'
import { Plus, Search, RefreshCw, UsersRound, ShieldCheck, UserCheck, KeyRound, Trash2, AlertTriangle } from '@lucide/vue'
import { usePage } from '@/composables/usePage'
import { allOptions, errorMessage, request } from '@/lib/api'
import { dateLabel, initials } from '@/lib/format'
import type { Role, User } from '@/types/api'
import { useI18n } from 'vue-i18n'
import { useToast } from '@/stores/toast'
import PageHeading from '@/components/PageHeading.vue'
import DataTable from '@/components/DataTable.vue'
import PageControls from '@/components/PageControls.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import UiButton from '@/components/UiButton.vue'
import UiField from '@/components/UiField.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiCheckbox from '@/components/UiCheckbox.vue'
const { page, size, data, rows, loading, error, refresh, setSize } = usePage<User>('/api/v1/user')
const toast = useToast(), search = ref(''), { t } = useI18n()
const filtered = computed(() => rows.value.filter(row => `${row.name} ${row.email || ''}`.toLowerCase().includes(search.value.toLowerCase())))
const createOpen = ref(false), rolesOpen = ref(false), deleteOpen = ref(false), saving = ref(false), formError = ref('')
const target = shallowRef<User | null>(null), roleOptions = shallowRef<Role[]>([]), optionsLoading = ref(false), selectedRoles = ref<number[]>([])
const username = ref(''), password = ref('')
const columns = computed(() => [{ key: 'name', label: t('users.columnUser') }, { key: 'roles', label: t('users.columnRoles') }, { key: 'status', label: t('shared.status') }, { key: 'createTime', label: t('shared.createdAt') }, { key: 'actions', label: t('shared.actions'), class: 'text-right' }])
async function loadRoles() {
  optionsLoading.value = true
  try { roleOptions.value = await allOptions<Role>('/api/v1/role') } catch (reason) { formError.value = errorMessage(reason) } finally { optionsLoading.value = false }
}
function openCreate() { username.value = ''; password.value = ''; selectedRoles.value = []; formError.value = ''; createOpen.value = true; void loadRoles() }
function openRoles(user: User) { target.value = user; selectedRoles.value = user.roles?.map(role => role.id) || []; formError.value = ''; rolesOpen.value = true; void loadRoles() }
function openDelete(user: User) { target.value = user; formError.value = ''; deleteOpen.value = true }
function toggleRole(id: number, checked: boolean) {
  selectedRoles.value = checked
    ? [...new Set([...selectedRoles.value, id])]
    : selectedRoles.value.filter(value => value !== id)
}
async function create() {
  if (saving.value) return
  if (!username.value.trim() || !password.value) {
    formError.value = !username.value.trim() ? t('users.enterUsername') : t('users.enterPassword')
    return
  }
  saving.value = true; formError.value = ''
  try {
    const id = await request<number>('/api/v1/user/register', { method: 'POST', body: { username: username.value.trim(), password: password.value } })
    if (selectedRoles.value.length) {
      try { await request('/api/v1/user/role', { method: 'PUT', body: { id: String(id), values: selectedRoles.value } }) }
      catch (reason) { createOpen.value = false; refresh(); toast.show(t('users.createdRoleFailed', { reason: errorMessage(reason) }), 'error'); return }
    }
    createOpen.value = false; refresh(); toast.show(t('users.created'))
  } catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
async function assignRoles() {
  if (!selectedRoles.value.length) { formError.value = t('users.selectRole'); return }
  saving.value = true; formError.value = ''
  try { await request('/api/v1/user/role', { method: 'PUT', body: { id: String(target.value?.id), values: selectedRoles.value } }); rolesOpen.value = false; refresh(); toast.show(t('users.rolesUpdated')) }
  catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
async function remove() {
  saving.value = true; formError.value = ''
  try { await request('/api/v1/user', { method: 'DELETE', query: { id: target.value?.id } }); deleteOpen.value = false; refresh(); toast.show(t('users.deleted')) }
  catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
</script>
<template>
  <PageHeading :title="t('titles.users')" :description="t('users.description')" :badge="t('users.count', { count: data.totalElements })"><UiButton variant="secondary" :disabled="loading" @click="refresh"><RefreshCw :size="15" />{{ t('shared.refresh') }}</UiButton><UiButton @click="openCreate"><Plus :size="16" />{{ t('users.create') }}</UiButton></PageHeading>
  <div class="mb-6 grid gap-4 sm:grid-cols-3"><div v-for="item in [{ label: t('users.statTotal'), value: data.totalElements, icon: UsersRound, caption: t('users.statTotalCaption') }, { label: t('users.statActive'), value: rows.filter(row => row.active && !row.locked).length, icon: UserCheck, caption: t('users.statActiveCaption') }, { label: t('users.statAssigned'), value: rows.filter(row => row.roles?.length).length, icon: ShieldCheck, caption: t('users.statAssignedCaption') }]" :key="item.label" class="panel flex items-center justify-between p-5"><div><p class="text-xs text-muted">{{ item.label }}</p><p class="mt-2 text-2xl font-semibold tracking-tight">{{ item.value }}<span class="ml-3 text-[10px] font-normal text-muted">{{ item.caption }}</span></p></div><span class="flex size-11 items-center justify-center rounded-xl bg-brand-soft text-brand"><component :is="item.icon" :size="21" :stroke-width="1.7" /></span></div></div>
  <section class="panel overflow-hidden">
    <div class="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4"><div><h2 class="text-sm font-semibold">{{ t('users.all') }}</h2><p class="mt-1 text-[11px] text-muted">{{ t('users.allCaption') }}</p></div><div class="flex w-full items-center gap-2 rounded-xl border border-line bg-canvas/40 px-3 sm:w-64"><Search :size="15" class="text-muted" /><input v-model="search" :aria-label="t('users.filter')" :placeholder="t('users.filterPlaceholder')" class="w-full bg-transparent py-2.5 text-xs outline-none" /></div></div>
    <DataTable
      :rows="filtered"
      :columns="columns"
      :loading="loading"
      :error="error"
      :empty-title="t('users.emptyTitle')"
      :empty-description="t('users.emptyDescription')"
      @retry="refresh"
    >
      <template #name="{ row }"><div class="flex items-center gap-3"><span class="flex size-9 shrink-0 items-center justify-center rounded-xl bg-brand-soft text-[11px] font-semibold text-brand">{{ initials(row.name) }}</span><div><p class="font-medium">{{ row.name }}</p><p class="mt-1 font-mono text-[10px] text-muted">{{ row.email || `USER-${String(row.id).padStart(4, '0')}` }}</p></div></div></template>
      <template #roles="{ row }"><div class="flex flex-wrap gap-1.5"><span v-for="role in row.roles" :key="role.id" class="badge bg-brand-soft text-brand">{{ role.name }}</span><span v-if="!row.roles?.length" class="text-xs text-muted">{{ t('users.noRoles') }}</span></div></template>
      <template #status="{ row }"><StatusBadge :active="row.active" :locked="row.locked" /></template>
      <template #createTime="{ row }"><span class="text-xs text-muted">{{ dateLabel(row.createTime) }}</span></template>
      <template #actions="{ row }">
        <div class="flex justify-end gap-1">
          <button type="button" class="table-action" @click="openRoles(row)"><KeyRound :size="14" />{{ t('users.assignRoles') }}</button><button
            type="button"
            class="table-action hover:text-rose-600"
            :disabled="row.isSystem"
            :aria-label="t('users.deleteNamed', { name: row.name })"
            @click="openDelete(row)"
          >
            <Trash2 :size="14" />
          </button>
        </div>
      </template>
    </DataTable><PageControls
      v-model:page="page"
      :size="size"
      :total="data.totalElements"
      :pages="data.totalPages"
      :loading="loading"
      @size="setSize"
    />
  </section>
  <UiDialog v-model="createOpen" :title="t('users.create')" :description="t('users.createDescription')" :busy="saving">
    <form id="create-user" class="space-y-5" novalidate @submit.prevent="create">
      <UiField
        v-model="username"
        :label="t('users.username')"
        :placeholder="t('users.usernamePlaceholder')"
        autocomplete="off"
        required
      /><UiField
        v-model="password"
        :label="t('users.initialPassword')"
        type="password"
        autocomplete="new-password"
        :placeholder="t('users.initialPasswordPlaceholder')"
        required
      /><fieldset>
        <legend class="field-label">{{ t('users.assignRoles') }} <span class="text-muted">{{ t('shared.optional') }}</span></legend><p v-if="optionsLoading" class="text-xs text-muted">{{ t('users.loadingRoles') }}</p><div v-else class="grid gap-2 sm:grid-cols-2">
          <UiCheckbox
            v-for="role in roleOptions"
            :key="role.id"
            :label="role.name"
            :checked="selectedRoles.includes(role.id)"
            :disabled="saving"
            class="rounded-xl border p-3"
            :class="selectedRoles.includes(role.id) ? 'border-brand/30 bg-brand-soft' : 'border-line hover:border-brand/30'"
            @update:checked="toggleRole(role.id, $event)"
          >
            <span class="text-xs">{{ role.name }}</span>
          </UiCheckbox>
        </div>
      </fieldset><p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form><template #footer><UiButton variant="secondary" :disabled="saving" @click="createOpen = false">{{ t('shared.cancel') }}</UiButton><UiButton type="submit" form="create-user" :loading="saving">{{ t('users.create') }}</UiButton></template>
  </UiDialog>
  <UiDialog v-model="rolesOpen" :title="t('users.assignRoles')" :description="t('users.rolesDescription', { name: target?.name || t('users.someone') })" :busy="saving">
    <p v-if="optionsLoading" class="text-muted">{{ t('users.loadingRoles') }}</p><div v-else class="space-y-2">
      <UiCheckbox
        v-for="role in roleOptions"
        :key="role.id"
        :label="role.name"
        :checked="selectedRoles.includes(role.id)"
        :disabled="saving"
        class="rounded-xl border p-4"
        :class="selectedRoles.includes(role.id) ? 'border-brand/30 bg-brand-soft' : 'border-line hover:border-brand/30'"
        @update:checked="toggleRole(role.id, $event)"
      >
        <span><span class="block text-sm font-medium">{{ role.name }}</span><span class="mt-1 block text-xs text-muted">{{ role.description || t('users.noRoleDescription') }}</span></span>
      </UiCheckbox><p v-if="!roleOptions.length" class="py-6 text-center text-muted">{{ t('users.noRoleOptions') }}</p>
    </div><p v-if="formError" class="mt-4 text-xs text-rose-600" role="alert">{{ formError }}</p><template #footer><UiButton variant="secondary" :disabled="saving" @click="rolesOpen = false">{{ t('shared.cancel') }}</UiButton><UiButton :loading="saving" :disabled="optionsLoading" @click="assignRoles">{{ t('users.saveRoles') }}</UiButton></template>
  </UiDialog>
  <UiDialog v-model="deleteOpen" :title="t('users.delete')" :busy="saving"><div class="flex gap-4"><span class="flex size-11 shrink-0 items-center justify-center rounded-xl bg-rose-50 text-rose-500"><AlertTriangle :size="22" /></span><div><i18n-t keypath="shared.deleteConfirm" tag="p"><template #name><strong>{{ target?.name }}</strong></template></i18n-t><p class="mt-2 text-xs leading-6 text-muted">{{ t('users.deleteWarning') }}</p></div></div><p v-if="formError" class="mt-4 text-xs text-rose-600" role="alert">{{ formError }}</p><template #footer><UiButton variant="secondary" :disabled="saving" @click="deleteOpen = false">{{ t('shared.cancel') }}</UiButton><UiButton variant="danger" :loading="saving" @click="remove">{{ t('shared.confirmDelete') }}</UiButton></template></UiDialog>
</template>
