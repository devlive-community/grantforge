<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onWatcherCleanup, ref, shallowRef, watch } from 'vue'
import { AlertTriangle, Copy, Pencil, Plus, Power, PowerOff, RefreshCw, Search, ShieldCheck, Trash2, UsersRound, KeySquare } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { vPermission } from '@/lib/permission'
import { useToast } from '@/stores/toast'
import { roleLabel } from '@/lib/roles'
import RoleAssignments from '@/components/RoleAssignments.vue'
import RoleGrants from '@/components/RoleGrants.vue'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import DataTable from '@/components/DataTable.vue'
import UiButton from '@/components/UiButton.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiField from '@/components/UiField.vue'

type Role = components['schemas']['RoleResponse']

const { t } = useI18n(), toast = useToast()
const roles = shallowRef<Role[]>([]), loading = ref(false), error = ref(''), search = ref(''), text = ref(''), revision = ref(0)
const dialog = ref<'create' | 'edit' | 'copy' | 'delete' | null>(null), target = shallowRef<Role | null>(null)
const saving = ref(false), formError = ref(''), form = ref({ code: '', name: '', description: '' })
const assigning = ref(false), assigned = shallowRef<Role | null>(null)
function openAssignments(role: Role) { assigned.value = role; assigning.value = true }
const granting = ref(false), granted = shallowRef<Role | null>(null)
function openGrants(role: Role) { granted.value = role; granting.value = true }
const columns = computed(() => [{ key: 'role', label: t('roles.columnRole') }, { key: 'description', label: t('roles.descriptionLabel') },
  { key: 'type', label: t('roles.columnType') }, { key: 'status', label: t('roles.columnStatus') },
  { key: 'actions', label: t('shared.actions'), class: 'text-right' }])

watch([text, revision], ([query]) => {
  const controller = new AbortController()
  onWatcherCleanup(() => controller.abort())
  loading.value = true; error.value = ''
  void request<Role[]>('/api/v1/roles', { query: { q: query || undefined }, signal: controller.signal })
    .then(found => { if (!controller.signal.aborted) roles.value = found })
    .catch(reason => { if (!controller.signal.aborted) error.value = errorMessage(reason) })
    .finally(() => { if (!controller.signal.aborted) loading.value = false })
}, { immediate: true })
// Searching waits for a pause in typing, so each keystroke does not query the server.
let pending: ReturnType<typeof setTimeout> | undefined
watch(search, value => { clearTimeout(pending); pending = setTimeout(() => { text.value = value.trim() }, 300) })

function refresh() { revision.value++ }
function open(kind: 'create' | 'edit' | 'copy' | 'delete', role: Role | null = null) {
  target.value = role; formError.value = ''; dialog.value = kind
  form.value = kind === 'copy' && role
    ? { code: `${role.code}-copy`, name: `${roleLabel(role)}${t('roles.copySuffix')}`, description: '' }
    : { code: role?.code ?? '', name: role?.name ?? '', description: role?.description ?? '' }
}
async function run(action: () => Promise<unknown>, done: string) {
  if (saving.value) return
  saving.value = true; formError.value = ''
  try { await action(); dialog.value = null; toast.show(done); refresh() } catch (reason) {
    if (dialog.value) formError.value = errorMessage(reason); else toast.show(errorMessage(reason), 'error')
  } finally { saving.value = false }
}
function save() {
  formError.value = !form.value.name.trim() ? t('roles.enterName') : !form.value.code.trim() ? t('roles.enterCode') : ''
  if (formError.value) return
  const { code, name, description } = form.value
  const role = target.value
  if (dialog.value === 'create') void run(() => request('/api/v1/roles', { method: 'POST', body: { code, name, description } }), t('roles.created'))
  else if (dialog.value === 'copy' && role) void run(() => request(`/api/v1/roles/${encodeURIComponent(role.id)}/copy`, { method: 'POST', body: { code, name } }), t('roles.copied'))
  else if (role) void run(() => request(`/api/v1/roles/${encodeURIComponent(role.id)}`, { method: 'PUT', body: { code, name, description } }), t('roles.saved'))
}
function toggle(role: Role) {
  void run(() => request(`/api/v1/roles/${encodeURIComponent(role.id)}/${role.enabled ? 'disable' : 'enable'}`, { method: 'POST' }), t('roles.statusChanged'))
}
function remove() {
  const role = target.value
  if (role) void run(() => request(`/api/v1/roles/${encodeURIComponent(role.id)}`, { method: 'DELETE' }), t('roles.deleted'))
}
</script>
<template>
  <PageHeading :title="t('titles.roles')" :description="t('roles.description')" :badge="t('roles.count', { count: roles.length })"><UiButton variant="secondary" :disabled="loading" @click="refresh"><RefreshCw :size="15" />{{ t('shared.refresh') }}</UiButton><UiButton v-permission="'system.role.btn.create'" @click="open('create')"><Plus :size="16" />{{ t('roles.create') }}</UiButton></PageHeading>
  <section class="panel overflow-hidden">
    <div class="flex justify-end border-b border-line px-5 py-4"><div class="flex w-full items-center gap-2 rounded-xl border border-line bg-canvas/40 px-3 sm:w-64"><Search :size="15" class="text-muted" /><input v-model="search" :aria-label="t('roles.search')" :placeholder="t('roles.searchPlaceholder')" class="w-full bg-transparent py-2.5 text-xs outline-none" /></div></div>
    <DataTable
      :rows="roles"
      :columns="columns"
      :loading="loading"
      :error="error"
      :empty-title="t('roles.emptyTitle')"
      :empty-description="t('roles.emptyDescription')"
      @retry="refresh"
    >
      <template #role="{ row }"><div class="flex items-center gap-3"><span class="flex size-9 shrink-0 items-center justify-center rounded-xl bg-brand-soft text-brand"><ShieldCheck :size="16" /></span><div><p class="font-medium">{{ roleLabel(row) }}</p><p class="mt-1 font-mono text-[10px] text-muted">{{ row.code }}</p></div></div></template>
      <template #description="{ row }"><span class="text-xs text-muted">{{ row.description || '—' }}</span></template>
      <template #type="{ row }"><span class="badge" :class="row.type === 'SYSTEM' ? 'bg-brand-soft text-brand' : ''">{{ row.type === 'SYSTEM' ? t('roles.typeSystem') : t('roles.typeCustom') }}</span></template>
      <template #status="{ row }"><span class="badge" :class="row.enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700'">{{ row.enabled ? t('roles.enabled') : t('roles.disabled') }}</span></template>
      <template #actions="{ row }">
        <div class="flex justify-end gap-0.5">
          <button
            v-permission="'system.role.btn.grant'"
            type="button"
            class="table-action"
            :aria-label="t('roles.grantNamed', { name: roleLabel(row) })"
            @click="openGrants(row)"
          >
            <KeySquare :size="14" />{{ t('roles.grant') }}
          </button>
          <button
            v-permission="'system.role.btn.assign'"
            type="button"
            class="table-action"
            :aria-label="t('roles.assignNamed', { name: roleLabel(row) })"
            @click="openAssignments(row)"
          >
            <UsersRound :size="14" />{{ t('roles.assign') }}
          </button>
          <button
            v-permission="'system.role.btn.copy'"
            type="button"
            class="table-action"
            :aria-label="t('roles.copyNamed', { name: roleLabel(row) })"
            @click="open('copy', row)"
          >
            <Copy :size="14" />{{ t('roles.copy') }}
          </button>
          <template v-if="row.type !== 'SYSTEM'">
            <button
              v-permission="'system.role.btn.status'"
              type="button"
              class="table-action"
              :disabled="saving"
              @click="toggle(row)"
            >
              <component :is="row.enabled ? PowerOff : Power" :size="14" />{{ row.enabled ? t('roles.disable') : t('roles.enable') }}
            </button>
            <button
              v-permission="'system.role.btn.edit'"
              type="button"
              class="table-action"
              :aria-label="t('roles.editNamed', { name: roleLabel(row) })"
              @click="open('edit', row)"
            >
              <Pencil :size="14" />
            </button>
            <button
              v-permission="'system.role.btn.delete'"
              type="button"
              class="table-action hover:text-rose-600"
              :aria-label="t('roles.deleteNamed', { name: roleLabel(row) })"
              @click="open('delete', row)"
            >
              <Trash2 :size="14" />
            </button>
          </template>
        </div>
      </template>
    </DataTable>
  </section>
  <UiDialog
    :model-value="dialog === 'create' || dialog === 'edit' || dialog === 'copy'"
    :title="dialog === 'edit' ? t('roles.editTitle') : dialog === 'copy' ? t('roles.copyTitle') : t('roles.create')"
    :description="dialog === 'copy' && target ? t('roles.copyDescription', { name: roleLabel(target) }) : undefined"
    :busy="saving"
    @update:model-value="dialog = null"
  >
    <form id="role-form" class="space-y-5" novalidate @submit.prevent="save">
      <UiField v-model="form.name" :label="t('roles.name')" :placeholder="t('roles.namePlaceholder')" required />
      <UiField v-model="form.code" :label="t('roles.code')" :placeholder="t('roles.codePlaceholder')" required />
      <UiField
        v-if="dialog !== 'copy'"
        v-model="form.description"
        :label="t('roles.descriptionLabel')"
        :placeholder="t('roles.descriptionPlaceholder')"
        textarea
      />
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="dialog = null">{{ t('shared.cancel') }}</UiButton><UiButton type="submit" form="role-form" :loading="saving">{{ dialog === 'edit' ? t('tenants.save') : dialog === 'copy' ? t('roles.copy') : t('roles.create') }}</UiButton></template>
  </UiDialog>
  <UiDialog :model-value="dialog === 'delete'" :title="t('roles.deleteTitle')" :busy="saving" @update:model-value="dialog = null">
    <div class="flex gap-4"><span class="flex size-11 shrink-0 items-center justify-center rounded-xl bg-rose-50 text-rose-500"><AlertTriangle :size="22" /></span><div><p>{{ t('roles.deleteConfirm', { name: target ? roleLabel(target) : '' }) }}</p><p class="mt-2 text-xs leading-6 text-muted">{{ t('roles.deleteWarning') }}</p></div></div><p v-if="formError" class="mt-4 text-xs text-rose-600" role="alert">{{ formError }}</p>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="dialog = null">{{ t('shared.cancel') }}</UiButton><UiButton variant="danger" :loading="saving" @click="remove">{{ t('roles.delete') }}</UiButton></template>
  </UiDialog>
  <RoleGrants v-if="granted" v-model="granting" :role-id="granted.id" :role-name="roleLabel(granted)" />
  <RoleAssignments v-if="assigned" v-model="assigning" :role-id="assigned.id" :role-name="roleLabel(assigned)" />
</template>
