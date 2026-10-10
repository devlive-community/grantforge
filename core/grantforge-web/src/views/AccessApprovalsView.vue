<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch } from 'vue'
import { Check, RefreshCw, Settings2, Undo2, X } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { statusLabel } from '@/lib/accessRequests'
import { errorMessage, request } from '@/lib/api'
import { dateLabel } from '@/lib/format'
import { useFieldErrors, type FieldErrors } from '@/lib/fieldErrors'
import { vPermission } from '@/lib/permission'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import UiButton from '@/components/UiButton.vue'
import UiCheckbox from '@/components/UiCheckbox.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiField from '@/components/UiField.vue'
import UiSelect from '@/components/UiSelect.vue'
import UiTip from '@/components/UiTip.vue'

type AccessRequest = components['schemas']['AccessRequestResponse']
type Requestable = components['schemas']['RequestableRoleResponse']
type Role = components['schemas']['RoleResponse']
type Status = AccessRequest['status']

// Approvers decide access requests (D-74); granting goes through the role assignment rules on the server.
const { t } = useI18n(), toast = useToast()
const items = shallowRef<AccessRequest[]>([]), loading = ref(false), error = ref(''), filter = ref<'PENDING' | 'APPROVED' | 'ALL'>('PENDING')
const filters = computed(() => [{ value: 'PENDING', label: t('approvals.filter.PENDING') }, { value: 'APPROVED', label: t('approvals.filter.APPROVED') },
  { value: 'ALL', label: t('approvals.filter.ALL') }])
const deciding = shallowRef<AccessRequest | null>(null), approving = ref(true), days = ref(''), comment = ref(''), saving = ref(false), formError = ref('')
const { errors: fieldErrors, invalid } = useFieldErrors(() => days.value, problems)
const configuring = ref(false), roles = shallowRef<Role[]>([]), chosen = ref<Record<string, string>>({})
const { errors: dayErrors, invalid: dayInvalid } = useFieldErrors(() => chosen.value, dayProblems)
// The day box of each configured role, by role ID, so its message can float beside it.
const dayInputs = new Map<string, HTMLInputElement | null>()
function keepDayInput(id: string, el: unknown) { if (el instanceof HTMLInputElement) dayInputs.set(id, el) }

async function load() {
  loading.value = true; error.value = ''
  try {
    const status: Status[] | undefined = filter.value === 'ALL' ? undefined : [filter.value]
    items.value = await request<AccessRequest[]>('/api/v1/access-requests', { query: { status: status?.join(',') } })
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
watch(filter, load)
function decide(item: AccessRequest, approve: boolean) {
  deciding.value = item; approving.value = approve; days.value = String(item.requestedDays); comment.value = ''; formError.value = ''; fieldErrors.value = {}
}
function problems(): FieldErrors {
  const item = deciding.value, count = Number(days.value)
  const found: FieldErrors = {}
  if (item && approving.value && (!Number.isInteger(count) || count < 1 || count > item.requestedDays)) found.days = t('approvals.enterDays', { max: item.requestedDays })
  return found
}
async function submit() {
  const item = deciding.value
  if (!item || saving.value) return
  formError.value = ''
  if (invalid()) return
  const count = Number(days.value)
  saving.value = true; formError.value = ''
  try {
    const action = approving.value ? 'approve' : 'reject'
    await request<AccessRequest>(`/api/v1/access-requests/${encodeURIComponent(item.id)}/${action}`, { method: 'POST',
      body: approving.value ? { days: count, comment: comment.value.trim() || undefined } : { comment: comment.value.trim() || undefined } })
    deciding.value = null; toast.show(t(approving.value ? 'approvals.approved' : 'approvals.rejected')); await load()
  } catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
async function revoke(item: AccessRequest) {
  try {
    await request<AccessRequest>(`/api/v1/access-requests/${encodeURIComponent(item.id)}/revoke`, { method: 'POST' })
    toast.show(t('approvals.revoked')); await load()
  } catch (reason) { toast.show(errorMessage(reason), 'error') }
}
async function openConfigure() {
  formError.value = ''
  dayErrors.value = {}
  try {
    const [all, current] = await Promise.all([request<Role[]>('/api/v1/roles'), request<Requestable[]>('/api/v1/requestable-roles')])
    roles.value = all.filter(role => role.type !== 'SYSTEM')
    chosen.value = Object.fromEntries(current.map(item => [item.role.id, String(item.maxDays)]))
    configuring.value = true
  } catch (reason) { toast.show(errorMessage(reason), 'error') }
}
function toggle(id: string, on: boolean) {
  const next = { ...chosen.value }
  if (on) next[id] = next[id] ?? '30'
  else delete next[id]
  chosen.value = next
}
function setDays(id: string, value: string) { chosen.value = { ...chosen.value, [id]: value } }
function dayProblems(): FieldErrors {
  const found: FieldErrors = {}
  for (const [id, value] of Object.entries(chosen.value)) if (!/^\d+$/.test(value) || Number(value) < 1 || Number(value) > 365) found[id] = t('approvals.enterMaxDays')
  return found
}
async function saveConfiguration() {
  if (saving.value) return
  formError.value = ''
  if (dayInvalid()) return
  const entries = Object.entries(chosen.value)
  saving.value = true; formError.value = ''
  try {
    await request<Requestable[]>('/api/v1/requestable-roles', { method: 'PUT', body: { roles: entries.map(([roleId, value]) => ({ roleId, maxDays: Number(value) })) } })
    configuring.value = false; toast.show(t('approvals.configured'))
  } catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
onMounted(load)
</script>
<template>
  <PageHeading :title="t('titles.accessRequests')" :description="t('approvals.description')">
    <UiButton variant="secondary" :loading="loading" @click="load"><RefreshCw :size="15" />{{ t('shared.refresh') }}</UiButton>
    <UiButton v-permission="'system.access-request.btn.configure'" variant="secondary" @click="openConfigure"><Settings2 :size="15" />{{ t('approvals.configure') }}</UiButton>
  </PageHeading>
  <section class="panel overflow-hidden">
    <div class="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4">
      <h2 class="text-sm font-semibold">{{ t('approvals.list') }}</h2><div class="w-44">
        <UiSelect
          v-model="filter"
          :label="t('approvals.show')"
          :options="filters"
          compact
          hide-label
        />
      </div>
    </div>
    <p v-if="error" class="px-6 py-8 text-center text-xs text-rose-600" role="alert">{{ error }}</p>
    <p v-else-if="!loading && !items.length" class="px-6 py-8 text-center text-xs text-muted">{{ t('approvals.none') }}</p>
    <ul class="divide-y divide-line">
      <li v-for="item in items" :key="item.id" class="flex flex-wrap items-start justify-between gap-3 px-6 py-4" data-request>
        <div>
          <p class="text-sm font-medium">{{ item.requesterName }}<span v-if="item.requesterUsername" class="ml-1 font-mono text-[11px] text-muted">{{ item.requesterUsername }}</span> → {{ item.role.name }}<span class="badge ml-2 bg-canvas text-ink">{{ statusLabel(item.status) }}</span></p>
          <p class="mt-1 text-[11px] text-muted">{{ t('requests.asked', { days: item.requestedDays, time: dateLabel(item.requestedAt) }) }} · {{ item.reason }}</p>
          <p v-if="item.validUntil && item.status === 'APPROVED'" class="mt-1 text-[11px] text-emerald-700">{{ t('requests.until', { time: dateLabel(item.validUntil) }) }}</p>
        </div>
        <div class="flex gap-1">
          <template v-if="item.status === 'PENDING'">
            <button
              v-permission="'system.access-request.btn.approve'"
              type="button"
              class="table-action"
              :aria-label="t('approvals.approveNamed', { name: item.requesterName })"
              :data-tooltip="t('approvals.approve')"
              @click="decide(item, true)"
            >
              <Check :size="14" />
            </button><button
              v-permission="'system.access-request.btn.approve'"
              type="button"
              class="table-action hover:text-rose-600"
              :aria-label="t('approvals.rejectNamed', { name: item.requesterName })"
              :data-tooltip="t('approvals.reject')"
              @click="decide(item, false)"
            >
              <X :size="14" />
            </button>
          </template>
          <button
            v-else-if="item.status === 'APPROVED'"
            v-permission="'system.access-request.btn.approve'"
            type="button"
            class="table-action hover:text-rose-600"
            :aria-label="t('approvals.revokeNamed', { name: item.requesterName })"
            :data-tooltip="t('approvals.revoke')"
            @click="revoke(item)"
          >
            <Undo2 :size="14" />
          </button>
        </div>
      </li>
    </ul>
  </section>
  <UiDialog :model-value="deciding !== null" :title="approving ? t('approvals.approveTitle') : t('approvals.rejectTitle')" :busy="saving" @update:model-value="deciding = null">
    <form id="decision" class="space-y-5" novalidate @submit.prevent="submit">
      <p class="text-xs leading-6">{{ t('approvals.decisionText', { name: deciding?.requesterName, role: deciding?.role.name }) }}</p>
      <UiField
        v-if="approving"
        v-model="days"
        :label="t('approvals.days', { max: deciding?.requestedDays })"
        type="number"
        min="1"
        required
        :error="fieldErrors.days"
      />
      <UiField v-model="comment" :label="t('approvals.comment')" textarea />
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="deciding = null">{{ t('shared.cancel') }}</UiButton><UiButton type="submit" form="decision" :variant="approving ? 'primary' : 'danger'" :loading="saving">{{ approving ? t('approvals.approve') : t('approvals.reject') }}</UiButton></template>
  </UiDialog>
  <UiDialog
    v-model="configuring"
    :title="t('approvals.configureTitle')"
    :description="t('approvals.configureText')"
    wide
    :busy="saving"
  >
    <ul class="space-y-2" data-requestable>
      <li v-for="role in roles" :key="role.id" class="flex items-center justify-between gap-3">
        <UiCheckbox :checked="chosen[role.id] !== undefined" :label="role.name" @update:checked="value => toggle(role.id, value)" />
        <input
          v-if="chosen[role.id] !== undefined"
          :id="`days-${role.id}`"
          :ref="el => keepDayInput(role.id, el)"
          :value="chosen[role.id]"
          class="field w-28"
          type="number"
          min="1"
          max="365"
          :aria-label="t('approvals.maxDaysNamed', { name: role.name })"
          :aria-invalid="Boolean(dayErrors[role.id])"
          :aria-describedby="dayErrors[role.id] ? `days-${role.id}-tip` : undefined"
          @input="setDays(role.id, ($event.target as HTMLInputElement).value)"
        />
        <UiTip :message="dayErrors[role.id]" :anchor="dayInputs.get(role.id)" :control="`days-${role.id}`" />
      </li>
    </ul>
    <p v-if="formError" class="mt-4 rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="configuring = false">{{ t('shared.cancel') }}</UiButton><UiButton :loading="saving" @click="saveConfiguration">{{ t('approvals.save') }}</UiButton></template>
  </UiDialog>
</template>
