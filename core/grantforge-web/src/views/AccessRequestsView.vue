<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { onMounted, ref, shallowRef } from 'vue'
import { RefreshCw, Send, X } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { statusLabel } from '@/lib/accessRequests'
import { errorMessage, request } from '@/lib/api'
import { useFieldErrors, type FieldErrors } from '@/lib/fieldErrors'
import { dateLabel } from '@/lib/format'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import UiButton from '@/components/UiButton.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiField from '@/components/UiField.vue'

type Option = components['schemas']['RequestOptionResponse']
type AccessRequest = components['schemas']['AccessRequestResponse']

// Every signed-in user asks for the roles administrators made requestable (D-74) and follows their requests.
const { t } = useI18n(), toast = useToast()
const options = shallowRef<Option[]>([]), mine = shallowRef<AccessRequest[]>([]), loading = ref(false), error = ref('')
const asking = shallowRef<Option | null>(null), reason = ref(''), days = ref(''), saving = ref(false), formError = ref('')
const { errors: fieldErrors, invalid } = useFieldErrors(() => [reason.value, days.value], problems)

async function load() {
  loading.value = true; error.value = ''
  try {
    ;[options.value, mine.value] = await Promise.all([request<Option[]>('/api/v1/me/requestable-roles'), request<AccessRequest[]>('/api/v1/me/access-requests')])
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
function open(option: Option) {
  asking.value = option; reason.value = ''; days.value = String(Math.min(7, option.maxDays)); formError.value = ''; fieldErrors.value = {}
}
function problems(): FieldErrors {
  const found: FieldErrors = {}
  const count = Number(days.value), max = asking.value?.maxDays ?? 0
  if (!reason.value.trim()) found.reason = t('requests.enterReason')
  if (!Number.isInteger(count) || count < 1 || count > max) found.days = t('requests.enterDays', { max })
  return found
}
async function submit() {
  const option = asking.value
  if (!option || saving.value) return
  const count = Number(days.value)
  formError.value = ''
  if (invalid()) return
  saving.value = true; formError.value = ''
  try {
    await request<AccessRequest>('/api/v1/me/access-requests', { method: 'POST', body: { roleId: option.role.id, reason: reason.value.trim(), days: count } })
    asking.value = null; toast.show(t('requests.sent')); await load()
  } catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
async function cancel(item: AccessRequest) {
  try {
    await request<AccessRequest>(`/api/v1/me/access-requests/${encodeURIComponent(item.id)}/cancel`, { method: 'POST' })
    toast.show(t('requests.cancelled')); await load()
  } catch (reason) { toast.show(errorMessage(reason), 'error') }
}
onMounted(load)
</script>
<template>
  <PageHeading :title="t('titles.requests')" :description="t('requests.description')"><UiButton variant="secondary" :loading="loading" @click="load"><RefreshCw :size="15" />{{ t('shared.refresh') }}</UiButton></PageHeading>
  <p v-if="error" class="panel p-6 text-center text-xs text-rose-600" role="alert">{{ error }}</p>
  <div v-else class="grid gap-6 xl:grid-cols-2">
    <section class="panel overflow-hidden" aria-labelledby="requestable">
      <header class="border-b border-line px-6 py-5"><h2 id="requestable" class="text-sm font-semibold">{{ t('requests.roles') }}</h2><p class="mt-1 text-[11px] text-muted">{{ t('requests.rolesCaption') }}</p></header>
      <p v-if="!loading && !options.length" class="px-6 py-8 text-center text-xs text-muted">{{ t('requests.noRoles') }}</p>
      <ul class="divide-y divide-line">
        <li v-for="option in options" :key="option.role.id" class="flex flex-wrap items-center justify-between gap-3 px-6 py-4" :data-option="option.role.code">
          <div><p class="text-sm font-medium">{{ option.role.name }}</p><p class="mt-1 text-[11px] text-muted">{{ option.role.description || t('requests.upTo', { days: option.maxDays }) }}</p></div>
          <span v-if="option.held" class="badge bg-emerald-50 text-emerald-700">{{ t('requests.held') }}</span>
          <span v-else-if="option.pending" class="badge bg-amber-50 text-amber-700">{{ t('requests.waiting') }}</span>
          <UiButton v-else variant="secondary" @click="open(option)"><Send :size="14" />{{ t('requests.ask') }}</UiButton>
        </li>
      </ul>
    </section>
    <section class="panel overflow-hidden" aria-labelledby="my-requests">
      <header class="border-b border-line px-6 py-5"><h2 id="my-requests" class="text-sm font-semibold">{{ t('requests.mine') }}</h2></header>
      <p v-if="!loading && !mine.length" class="px-6 py-8 text-center text-xs text-muted">{{ t('requests.none') }}</p>
      <ul class="divide-y divide-line">
        <li v-for="item in mine" :key="item.id" class="px-6 py-4" data-request>
          <div class="flex flex-wrap items-center justify-between gap-3">
            <p class="text-sm font-medium">{{ item.role.name }}<span class="badge ml-2" :class="item.status === 'APPROVED' ? 'bg-emerald-50 text-emerald-700' : item.status === 'PENDING' ? 'bg-amber-50 text-amber-700' : 'bg-canvas text-muted'">{{ statusLabel(item.status) }}</span></p>
            <button
              v-if="item.status === 'PENDING'"
              type="button"
              class="table-action"
              :aria-label="t('requests.cancelNamed', { name: item.role.name })"
              @click="cancel(item)"
            >
              <X :size="14" />{{ t('requests.cancel') }}
            </button>
          </div>
          <p class="mt-1 text-[11px] text-muted">{{ t('requests.asked', { days: item.requestedDays, time: dateLabel(item.requestedAt) }) }} · {{ item.reason }}</p>
          <p v-if="item.validUntil && item.status === 'APPROVED'" class="mt-1 text-[11px] text-emerald-700">{{ t('requests.until', { time: dateLabel(item.validUntil) }) }}</p>
          <p v-if="item.comment" class="mt-1 text-[11px] text-muted">{{ t('requests.comment', { name: item.decidedByName ?? '', comment: item.comment }) }}</p>
        </li>
      </ul>
    </section>
  </div>
  <UiDialog :model-value="asking !== null" :title="t('requests.askTitle', { name: asking?.role.name })" :busy="saving" @update:model-value="asking = null">
    <form id="access-request" class="space-y-5" novalidate @submit.prevent="submit">
      <UiField
        v-model="reason"
        :label="t('requests.reason')"
        :placeholder="t('requests.reasonPlaceholder')"
        textarea
        required
        :error="fieldErrors.reason"
      />
      <UiField
        v-model="days"
        :label="t('requests.days', { max: asking?.maxDays })"
        type="number"
        min="1"
        required
        :error="fieldErrors.days"
      />
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="asking = null">{{ t('shared.cancel') }}</UiButton><UiButton type="submit" form="access-request" :loading="saving">{{ t('requests.send') }}</UiButton></template>
  </UiDialog>
</template>
