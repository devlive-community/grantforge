<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onMounted, onWatcherCleanup, ref, shallowRef, watch } from 'vue'
import { AlertTriangle, KeyRound, Lock, LockOpen, Pencil, Plus, Power, PowerOff, RefreshCw, ScanEye, Search, Trash2, ShieldCheck } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { vPermission } from '@/lib/permission'
import { dateLabel, initials } from '@/lib/format'
import { orgOptions } from '@/lib/org'
import UserRoles from '@/components/UserRoles.vue'
import UserPermissions from '@/components/UserPermissions.vue'
import { useAuth } from '@/stores/auth'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import DataTable from '@/components/DataTable.vue'
import PageControls from '@/components/PageControls.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import UiButton from '@/components/UiButton.vue'
import UiCheckbox from '@/components/UiCheckbox.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiField from '@/components/UiField.vue'
import UiSelect from '@/components/UiSelect.vue'

type User = components['schemas']['UserResponse']
type UserPage = components['schemas']['PageResultUserResponse']
type Detail = components['schemas']['UserDetailResponse']
type Unit = components['schemas']['OrgUnitResponse']
type PositionOption = components['schemas']['PositionOptionResponse']
type Confirm = 'disable' | 'lock' | 'delete'

const { t } = useI18n(), toast = useToast(), auth = useAuth()
const page = ref(1), size = ref(20), revision = ref(0), search = ref(''), text = ref(''), state = ref(''), unit = ref('')
const result = shallowRef<UserPage>({ items: [], page: 1, size: 20, total: 0 })
const loading = ref(false), error = ref(''), units = shallowRef<Unit[]>([]), positions = shallowRef<PositionOption[]>([])
const pages = computed(() => Math.ceil(result.value.total / size.value))
const columns = computed(() => [{ key: 'user', label: t('users.columnUser') }, { key: 'unit', label: t('users.columnUnit') },
  { key: 'status', label: t('shared.status') }, ...auth.sees('user', 'lastLoginAt') ? [{ key: 'lastLogin', label: t('users.columnLastLogin') }] : [],
  { key: 'actions', label: t('shared.actions'), class: 'text-right' }])
const stateOptions = computed(() => [{ value: '', label: t('users.stateAll') }, { value: 'ACTIVE', label: t('users.stateActive') },
  { value: 'DISABLED', label: t('users.stateDisabled') }, { value: 'LOCKED', label: t('users.stateLocked') }])
const unitOptions = computed(() => orgOptions(units.value))

const editing = ref<'create' | 'edit' | 'password' | null>(null), confirming = ref<Confirm | null>(null)
const target = shallowRef<User | null>(null), saving = ref(false), formError = ref('')
const form = ref({ username: '', displayName: '', email: '', primaryUnitId: '', otherUnitIds: [] as string[], positionIds: [] as string[], password: '', confirm: '' })
const name = (user: User | null) => user ? user.displayName || user.username : ''

watch([page, size, text, state, unit, revision], ([current, limit, query, status, department]) => {
  const controller = new AbortController()
  onWatcherCleanup(() => controller.abort())
  loading.value = true; error.value = ''
  void request<UserPage>('/api/v1/users', { signal: controller.signal,
    query: { q: query || undefined, state: status || undefined, unitId: department || undefined, page: current, size: limit } })
    .then(found => { if (!controller.signal.aborted) result.value = found })
    .catch(reason => { if (!controller.signal.aborted) error.value = errorMessage(reason) })
    .finally(() => { if (!controller.signal.aborted) loading.value = false })
}, { immediate: true })
// Searching waits for a pause in typing; any filter change starts at the first page.
let pending: ReturnType<typeof setTimeout> | undefined
watch(search, value => { clearTimeout(pending); pending = setTimeout(() => { page.value = 1; text.value = value.trim() }, 300) })
watch([state, unit], () => { page.value = 1 })

function refresh() { revision.value++ }
function setSize(value: number) { page.value = 1; size.value = value }
async function loadOptions() {
  const [found, held] = await Promise.allSettled([request<Unit[]>('/api/v1/org-units'), request<PositionOption[]>('/api/v1/positions/options')])
  units.value = found.status === 'fulfilled' ? found.value : []
  positions.value = held.status === 'fulfilled' ? held.value : []
}
function openCreate() {
  form.value = { username: '', displayName: '', email: '', primaryUnitId: '', otherUnitIds: [], positionIds: [], password: '', confirm: '' }
  formError.value = ''; target.value = null; editing.value = 'create'
}
async function openEdit(user: User) {
  target.value = user; formError.value = ''
  try {
    const detail = await request<Detail>(`/api/v1/users/${encodeURIComponent(user.id)}`)
    form.value = { username: user.username, displayName: detail.user.displayName ?? '', email: detail.user.email ?? '',
      primaryUnitId: detail.memberships.find(member => member.primary)?.unitId ?? '',
      otherUnitIds: detail.memberships.filter(member => !member.primary).map(member => member.unitId),
      positionIds: detail.positions.map(position => position.positionId), password: '', confirm: '' }
    editing.value = 'edit'
  } catch (reason) { toast.show(errorMessage(reason), 'error') }
}
function openPassword(user: User) { target.value = user; formError.value = ''; form.value.password = ''; form.value.confirm = ''; editing.value = 'password' }
function openConfirm(user: User, kind: Confirm) { target.value = user; formError.value = ''; confirming.value = kind }
function toggleOther(id: string, checked: boolean) {
  form.value.otherUnitIds = checked ? [...new Set([...form.value.otherUnitIds, id])] : form.value.otherUnitIds.filter(other => other !== id)
}
function togglePosition(id: string, checked: boolean) {
  form.value.positionIds = checked ? [...new Set([...form.value.positionIds, id])] : form.value.positionIds.filter(other => other !== id)
}
function profile() {
  const { displayName, email, primaryUnitId, otherUnitIds, positionIds } = form.value
  return { displayName, email, primaryUnitId: primaryUnitId || null, otherUnitIds: primaryUnitId ? otherUnitIds.filter(id => id !== primaryUnitId) : [], positionIds }
}
async function run(action: () => Promise<unknown>, done: string) {
  if (saving.value) return
  saving.value = true; formError.value = ''
  try { await action(); editing.value = null; confirming.value = null; toast.show(done); refresh() }
  catch (reason) {
    if (editing.value || confirming.value) formError.value = errorMessage(reason); else toast.show(errorMessage(reason), 'error')
  } finally { saving.value = false }
}
function passwordProblem(): string {
  if (!form.value.password) return t('users.enterPassword')
  return form.value.password === form.value.confirm ? '' : t('users.passwordMismatch')
}
function save() {
  if (editing.value === 'create') {
    formError.value = !form.value.username.trim() ? t('users.enterUsername') : passwordProblem()
    if (formError.value) return
    void run(() => request<Detail>('/api/v1/users', { method: 'POST',
      body: { username: form.value.username.trim(), password: form.value.password, profile: profile() } }), t('users.created'))
  } else if (editing.value === 'edit' && target.value) {
    const id = target.value.id
    void run(() => request<Detail>(`/api/v1/users/${encodeURIComponent(id)}`, { method: 'PUT', body: profile() }), t('users.saved'))
  } else if (editing.value === 'password' && target.value) {
    formError.value = passwordProblem()
    if (formError.value) return
    const id = target.value.id
    void run(() => request<Detail>(`/api/v1/users/${encodeURIComponent(id)}/password`, { method: 'POST', body: { password: form.value.password } }),
      t('users.passwordReset'))
  }
}
function act(user: User, action: 'enable' | 'unlock' | Confirm) {
  const done = { enable: t('users.enabled'), unlock: t('users.unlocked'), disable: t('users.disabled'), lock: t('users.locked'), delete: t('users.deleted') }[action]
  const path = `/api/v1/users/${encodeURIComponent(user.id)}`
  void run(() => action === 'delete' ? request<null>(path, { method: 'DELETE' }) : request<Detail>(`${path}/${action}`, { method: 'POST' }), done)
}
const warning = computed(() => {
  const who = name(target.value)
  return confirming.value === 'disable' ? t('users.disableWarning', { name: who })
    : confirming.value === 'lock' ? t('users.lockWarning', { name: who }) : t('users.deleteWarning', { name: who })
})
const confirmLabel = computed(() => confirming.value === 'disable' ? t('users.disable') : confirming.value === 'lock' ? t('users.lock') : t('users.delete'))
onMounted(loadOptions)
const rolesOpen = ref(false), rolesOf = shallowRef<{ id: string; name: string } | null>(null)
function openRoles(user: { id: string; username: string; displayName?: string }) { rolesOf.value = { id: user.id, name: user.displayName || user.username }; rolesOpen.value = true }
const permissionsOpen = ref(false), permissionsOf = shallowRef<{ id: string; name: string } | null>(null)
function openPermissions(user: { id: string; username: string; displayName?: string }) {
  permissionsOf.value = { id: user.id, name: user.displayName || user.username }; permissionsOpen.value = true
}
</script>
<template>
  <PageHeading :title="t('titles.users')" :description="t('users.description')" :badge="t('users.count', { count: result.total })"><UiButton variant="secondary" :disabled="loading" @click="refresh"><RefreshCw :size="15" />{{ t('shared.refresh') }}</UiButton><UiButton v-permission="'system.user.btn.create'" @click="openCreate"><Plus :size="16" />{{ t('users.create') }}</UiButton></PageHeading>
  <section class="panel overflow-hidden">
    <div class="flex flex-wrap items-end justify-between gap-3 border-b border-line px-5 py-4">
      <div><h2 class="text-sm font-semibold">{{ t('users.all') }}</h2><p class="mt-1 text-[11px] text-muted">{{ t('users.allCaption') }}</p></div>
      <div class="flex w-full flex-wrap items-end gap-2 sm:w-auto">
        <div class="w-36">
          <UiSelect
            v-model="state"
            :label="t('users.stateFilter')"
            :options="stateOptions"
            compact
            hide-label
          />
        </div>
        <div class="w-44">
          <UiSelect
            v-model="unit"
            :label="t('users.unitFilter')"
            :options="[{ value: '', label: t('users.unitAll') }, ...unitOptions]"
            compact
            hide-label
          />
        </div>
        <div class="flex w-full items-center gap-2 rounded-xl border border-line bg-canvas/40 px-3 sm:w-60"><Search :size="15" class="text-muted" /><input v-model="search" :aria-label="t('users.filter')" :placeholder="t('users.filterPlaceholder')" class="w-full bg-transparent py-2.5 text-xs outline-none" /></div>
      </div>
    </div>
    <DataTable
      :rows="result.items"
      :columns="columns"
      :loading="loading"
      :error="error"
      :empty-title="t('users.emptyTitle')"
      :empty-description="t('users.emptyDescription')"
      @retry="refresh"
    >
      <template #user="{ row }"><div class="flex items-center gap-3"><span class="flex size-9 shrink-0 items-center justify-center rounded-xl bg-brand-soft text-[11px] font-semibold text-brand">{{ initials(name(row)) }}</span><div><p class="font-medium">{{ name(row) }}<span v-if="row.systemAccount" class="badge ml-2 bg-brand-soft text-brand">{{ t('users.system') }}</span><span v-if="row.mustChangePassword" class="badge ml-2 bg-amber-50 text-amber-700">{{ t('users.mustChange') }}</span></p><p class="mt-1 font-mono text-[10px] text-muted">{{ row.username }}<template v-if="row.email"> · {{ row.email }}</template></p></div></div></template>
      <template #unit="{ row }"><span class="text-xs" :class="row.primaryUnitName ? '' : 'text-muted'">{{ row.primaryUnitName || t('users.noUnit') }}</span></template>
      <template #status="{ row }"><StatusBadge :active="row.status === 'ACTIVE'" :locked="!!row.lockedUntil" /></template>
      <template #lastLogin="{ row }"><span class="text-xs text-muted">{{ row.lastLoginAt ? dateLabel(row.lastLoginAt) : t('users.never') }}</span></template>
      <template #actions="{ row }">
        <div class="flex justify-end gap-0.5">
          <button
            v-permission="'system.user.btn.edit'"
            type="button"
            class="table-action"
            :aria-label="t('users.editNamed', { name: name(row) })"
            @click="openEdit(row)"
          >
            <Pencil :size="14" />
          </button>
          <button
            v-permission="'system.user.btn.roles'"
            type="button"
            class="table-action"
            :aria-label="t('users.rolesNamed', { name: name(row) })"
            @click="openRoles(row)"
          >
            <ShieldCheck :size="14" />
          </button>
          <button
            v-permission="'system.user.btn.permissions'"
            type="button"
            class="table-action"
            :aria-label="t('users.permissionsNamed', { name: name(row) })"
            @click="openPermissions(row)"
          >
            <ScanEye :size="14" />
          </button>
          <button
            v-permission="'system.user.btn.reset-password'"
            type="button"
            class="table-action"
            :aria-label="t('users.resetPasswordNamed', { name: name(row) })"
            @click="openPassword(row)"
          >
            <KeyRound :size="14" />
          </button>
          <template v-if="!row.systemAccount">
            <button
              v-if="row.status === 'ACTIVE'"
              v-permission="'system.user.btn.status'"
              type="button"
              class="table-action"
              :aria-label="t('users.disableNamed', { name: name(row) })"
              @click="openConfirm(row, 'disable')"
            >
              <PowerOff :size="14" />
            </button>
            <button
              v-else
              v-permission="'system.user.btn.status'"
              type="button"
              class="table-action"
              :aria-label="t('users.enableNamed', { name: name(row) })"
              @click="act(row, 'enable')"
            >
              <Power :size="14" />
            </button>
            <button
              v-if="row.lockedUntil"
              v-permission="'system.user.btn.status'"
              type="button"
              class="table-action"
              :aria-label="t('users.unlockNamed', { name: name(row) })"
              @click="act(row, 'unlock')"
            >
              <LockOpen :size="14" />
            </button>
            <button
              v-else
              v-permission="'system.user.btn.status'"
              type="button"
              class="table-action"
              :aria-label="t('users.lockNamed', { name: name(row) })"
              @click="openConfirm(row, 'lock')"
            >
              <Lock :size="14" />
            </button>
            <button
              v-permission="'system.user.btn.delete'"
              type="button"
              class="table-action hover:text-rose-600"
              :aria-label="t('users.deleteNamed', { name: name(row) })"
              @click="openConfirm(row, 'delete')"
            >
              <Trash2 :size="14" />
            </button>
          </template>
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
  <UiDialog
    :model-value="editing === 'create' || editing === 'edit'"
    :title="editing === 'edit' ? t('users.editTitle') : t('users.create')"
    :description="editing === 'create' ? t('users.passwordHint') : undefined"
    :busy="saving"
    @update:model-value="editing = null"
  >
    <form id="user-form" class="space-y-5" novalidate @submit.prevent="save">
      <UiField
        v-if="editing === 'create'"
        v-model="form.username"
        :label="t('users.username')"
        :placeholder="t('users.usernamePlaceholder')"
        autocomplete="off"
        required
      />
      <div class="grid gap-5 sm:grid-cols-2">
        <UiField v-model="form.displayName" :label="t('users.displayName')" autocomplete="off" />
        <UiField
          v-if="auth.sees('user', 'email')"
          v-model="form.email"
          :label="t('users.email')"
          type="email"
          autocomplete="off"
          :disabled="!auth.edits('user', 'email')"
          :placeholder="auth.edits('user', 'email') ? '' : t('users.emailLocked')"
        />
      </div>
      <UiSelect v-model="form.primaryUnitId" :label="t('users.primaryUnit')" :options="[{ value: '', label: t('users.noPrimaryUnit') }, ...unitOptions]" />
      <fieldset v-if="unitOptions.length">
        <legend class="field-label">{{ t('users.otherUnits') }}<span v-if="!form.primaryUnitId" class="ml-2 text-muted">{{ t('users.otherUnitsHint') }}</span></legend>
        <div class="max-h-44 space-y-1 overflow-y-auto rounded-xl border border-line p-2">
          <UiCheckbox
            v-for="option in unitOptions"
            :key="option.value"
            :label="option.label"
            :checked="form.otherUnitIds.includes(option.value) && option.value !== form.primaryUnitId"
            :disabled="!form.primaryUnitId || option.value === form.primaryUnitId || saving"
            class="rounded-lg px-2 py-1.5"
            @update:checked="toggleOther(option.value, $event)"
          >
            <span class="text-xs">{{ option.label }}</span>
          </UiCheckbox>
        </div>
      </fieldset>
      <fieldset v-if="positions.length">
        <legend class="field-label">{{ t('users.positions') }}</legend>
        <div class="flex max-h-32 flex-wrap gap-x-4 gap-y-1 overflow-y-auto rounded-xl border border-line p-2">
          <UiCheckbox
            v-for="position in positions"
            :key="position.id"
            :label="position.name"
            :checked="form.positionIds.includes(position.id)"
            :disabled="saving"
            class="rounded-lg px-2 py-1.5"
            @update:checked="togglePosition(position.id, $event)"
          >
            <span class="text-xs">{{ position.name }}</span>
          </UiCheckbox>
        </div>
      </fieldset>
      <div v-if="editing === 'create'" class="grid gap-5 sm:grid-cols-2">
        <UiField
          v-model="form.password"
          :label="t('users.password')"
          type="password"
          autocomplete="new-password"
          required
        /><UiField
          v-model="form.confirm"
          :label="t('users.passwordConfirm')"
          type="password"
          autocomplete="new-password"
          required
        />
      </div>
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="editing = null">{{ t('shared.cancel') }}</UiButton><UiButton type="submit" form="user-form" :loading="saving">{{ editing === 'edit' ? t('users.save') : t('users.create') }}</UiButton></template>
  </UiDialog>
  <UiDialog
    :model-value="editing === 'password'"
    :title="t('users.resetPassword')"
    :description="t('users.resetDescription', { name: name(target) })"
    :busy="saving"
    @update:model-value="editing = null"
  >
    <form id="password-form" class="space-y-5" novalidate @submit.prevent="save">
      <UiField
        v-model="form.password"
        :label="t('users.newPassword')"
        type="password"
        autocomplete="new-password"
        required
      /><UiField
        v-model="form.confirm"
        :label="t('users.newPasswordConfirm')"
        type="password"
        autocomplete="new-password"
        required
      />
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="editing = null">{{ t('shared.cancel') }}</UiButton><UiButton type="submit" form="password-form" :loading="saving">{{ t('users.resetPassword') }}</UiButton></template>
  </UiDialog>
  <UiDialog :model-value="confirming !== null" :title="t('users.confirmTitle')" :busy="saving" @update:model-value="confirming = null">
    <div class="flex gap-4"><span class="flex size-11 shrink-0 items-center justify-center rounded-xl bg-rose-50 text-rose-500"><AlertTriangle :size="22" /></span><p class="text-sm leading-6">{{ warning }}</p></div><p v-if="formError" class="mt-4 text-xs text-rose-600" role="alert">{{ formError }}</p>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="confirming = null">{{ t('shared.cancel') }}</UiButton><UiButton variant="danger" :loading="saving" @click="target && confirming && act(target, confirming)">{{ confirmLabel }}</UiButton></template>
  </UiDialog>
  <UserRoles v-if="rolesOf" v-model="rolesOpen" :account-id="rolesOf.id" :account-name="rolesOf.name" />
  <UserPermissions v-if="permissionsOf" v-model="permissionsOpen" :account-id="permissionsOf.id" :account-name="permissionsOf.name" />
</template>
