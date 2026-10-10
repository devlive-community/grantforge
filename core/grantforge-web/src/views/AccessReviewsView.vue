<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { CalendarCheck, Check, ListChecks, Pencil, Play, Plus, RefreshCw, Trash2, Undo2, X } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { decisionLabel, fallbackEffect, fallbackLabel, outcomeLabel, roundStatusLabel, subjectTypeLabel } from '@/lib/accessReviews'
import { errorMessage, request } from '@/lib/api'
import { useFieldErrors, type FieldErrors } from '@/lib/fieldErrors'
import { dateLabel } from '@/lib/format'
import { vPermission } from '@/lib/permission'
import { useAuth } from '@/stores/auth'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import PageControls from '@/components/PageControls.vue'
import PageHeading from '@/components/PageHeading.vue'
import UiButton from '@/components/UiButton.vue'
import UiCheckbox from '@/components/UiCheckbox.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiDatePicker from '@/components/UiDatePicker.vue'
import UiField from '@/components/UiField.vue'
import UiSelect from '@/components/UiSelect.vue'
import UiSwitch from '@/components/UiSwitch.vue'

type Review = components['schemas']['AccessReviewResponse']
type Round = components['schemas']['ReviewRoundResponse']
type Item = components['schemas']['ReviewItemResponse']
type ItemPage = components['schemas']['PageResultReviewItemResponse']
type Role = components['schemas']['RoleResponse']
type Decision = Item['decision']
type Fallback = Review['unreviewed']

// Access reviews (D-75): reviewers keep or revoke the assignments of a round; completing the round applies the decisions.
const { t } = useI18n(), auth = useAuth(), toast = useToast()
const reviews = shallowRef<Review[]>([]), rounds = shallowRef<Round[]>([]), roles = shallowRef<Role[]>([])
const items = shallowRef<ItemPage>({ items: [], page: 1, size: 20, total: 0 })
const loading = ref(false), error = ref(''), selectedId = ref(''), roundId = ref(''), filter = ref<'ALL' | Decision>('ALL'), page = ref(1), size = ref(20)
const chosen = ref<string[]>([]), saving = ref(false), formError = ref(''), roleFilter = ref('')
const editing = ref<'create' | 'edit' | null>(null), target = shallowRef<Review | null>(null), deleting = shallowRef<Review | null>(null)
const revoking = ref<string[] | null>(null), comment = ref(''), confirming = ref<'complete' | 'cancel' | null>(null)
const form = ref({ name: '', description: '', roleIds: [] as string[], durationDays: '14', intervalDays: '', nextRun: '', unreviewed: 'KEEP' as Fallback,
  enabled: true })
const { errors: fieldErrors, invalid } = useFieldErrors(() => form.value, problems)

const selected = computed(() => reviews.value.find(review => review.id === selectedId.value) ?? null)
const round = computed(() => rounds.value.find(item => item.id === roundId.value) ?? null)
const open = computed(() => round.value?.status === 'OPEN')
const deciding = computed(() => open.value && auth.can('system.access-review.btn.decide'))
const pages = computed(() => Math.ceil(items.value.total / items.value.size))
const filters = computed(() => [{ value: 'ALL', label: t('reviews.filter.ALL') }, { value: 'PENDING', label: t('reviews.filter.PENDING') },
  { value: 'KEEP', label: t('reviews.filter.KEEP') }, { value: 'REVOKE', label: t('reviews.filter.REVOKE') }])
const roundOptions = computed(() => rounds.value.map(item => ({ value: item.id,
  label: t('reviews.roundOption', { time: dateLabel(item.startedAt), status: roundStatusLabel(item.status) }) })))
const fallbacks = computed(() => (['KEEP', 'REVOKE'] as const).map(value => ({ value, label: fallbackLabel(value),
  description: t('reviews.unreviewed', { action: fallbackEffect(value) }) })))
const shownRoles = computed(() => {
  const query = roleFilter.value.trim().toLowerCase()
  return roles.value.filter(role => !query || role.name.toLowerCase().includes(query) || role.code.toLowerCase().includes(query))
})
const allChosen = computed(() => items.value.items.length > 0 && items.value.items.every(item => chosen.value.includes(item.id)))

async function load() {
  loading.value = true; error.value = ''
  try {
    reviews.value = await request<Review[]>('/api/v1/access-reviews')
    if (!selected.value) selectedId.value = reviews.value[0]?.id ?? ''
    await loadRounds()
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
async function loadRounds() {
  const review = selected.value
  rounds.value = review ? await request<Round[]>(`/api/v1/access-reviews/${encodeURIComponent(review.id)}/rounds`) : []
  if (!round.value) { roundId.value = rounds.value[0]?.id ?? ''; page.value = 1 }
  await loadItems()
}
async function loadItems() {
  chosen.value = []
  if (!roundId.value) { items.value = { items: [], page: 1, size: size.value, total: 0 }; return }
  items.value = await request<ItemPage>(`/api/v1/access-review-rounds/${encodeURIComponent(roundId.value)}/items`, { query: {
    decision: filter.value === 'ALL' ? undefined : filter.value, page: page.value, size: size.value } })
}
async function show(task: () => Promise<void>) {
  try { await task() } catch (reason) { toast.show(errorMessage(reason), 'error') }
}
function select(review: Review) {
  if (review.id === selectedId.value) return
  selectedId.value = review.id; roundId.value = ''; filter.value = 'ALL'
  void show(loadRounds)
}
function pickRound(value: string) { roundId.value = value; page.value = 1; void show(loadItems) }
function pickFilter(value: string) { filter.value = value as 'ALL' | Decision; page.value = 1; void show(loadItems) }
function turnPage(value: number) { page.value = value; void show(loadItems) }
function resize(value: number) { size.value = value; page.value = 1; void show(loadItems) }
function toggle(id: string, on: boolean) { chosen.value = on ? [...chosen.value.filter(item => item !== id), id] : chosen.value.filter(item => item !== id) }
function toggleAll(on: boolean) { chosen.value = on ? items.value.items.map(item => item.id) : [] }

function period(item: Item): string {
  if (!item.assigned) return t('reviews.gone')
  if (!item.validFrom && !item.validTo) return t('assignments.always')
  return t('assignments.period', { from: item.validFrom ? dateLabel(item.validFrom) : '—', to: item.validTo ? dateLabel(item.validTo) : '—' })
}
function schedule(review: Review): string {
  return review.intervalDays ? t('reviews.repeats', { days: review.durationDays, interval: review.intervalDays }) : t('reviews.manual', { days: review.durationDays })
}
function decisionClass(decision: Decision): string {
  return decision === 'KEEP' ? 'bg-emerald-50 text-emerald-700' : decision === 'REVOKE' ? 'bg-rose-50 text-rose-700' : 'bg-canvas text-ink'
}

async function decide(ids: string[], decision: Decision, note?: string) {
  if (saving.value || !ids.length) return
  saving.value = true; formError.value = ''
  try {
    await request<Item[]>(`/api/v1/access-review-rounds/${encodeURIComponent(roundId.value)}/decisions`, { method: 'POST',
      body: { itemIds: ids, decision, comment: note?.trim() || undefined } })
    revoking.value = null; toast.show(t('reviews.decided')); await load()
  } catch (reason) {
    if (revoking.value) formError.value = errorMessage(reason); else toast.show(errorMessage(reason), 'error')
  } finally { saving.value = false }
}
function askRevoke(ids: string[]) { revoking.value = ids; comment.value = ''; formError.value = '' }
async function start(review: Review) {
  if (saving.value) return
  saving.value = true
  try {
    const started = await request<Round>(`/api/v1/access-reviews/${encodeURIComponent(review.id)}/start`, { method: 'POST' })
    selectedId.value = review.id; roundId.value = started.id; page.value = 1; filter.value = 'ALL'
    toast.show(t('reviews.started')); await load()
  } catch (reason) { toast.show(errorMessage(reason), 'error') } finally { saving.value = false }
}
async function finish() {
  const action = confirming.value
  if (!action || saving.value) return
  saving.value = true
  try {
    await request<Round>(`/api/v1/access-review-rounds/${encodeURIComponent(roundId.value)}/${action}`, { method: 'POST' })
    confirming.value = null; toast.show(t(action === 'complete' ? 'reviews.completed' : 'reviews.cancelled')); await load()
  } catch (reason) { toast.show(errorMessage(reason), 'error') } finally { saving.value = false }
}

function day(value?: string): string {
  if (!value) return ''
  const date = new Date(value)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
async function openEditor(review: Review | null) {
  form.value = review ? { name: review.name, description: review.description ?? '', roleIds: review.roles.map(role => role.id),
    durationDays: String(review.durationDays), intervalDays: review.intervalDays ? String(review.intervalDays) : '', nextRun: day(review.nextRunAt),
    unreviewed: review.unreviewed, enabled: review.enabled }
    : { name: '', description: '', roleIds: [], durationDays: '14', intervalDays: '', nextRun: '', unreviewed: 'KEEP', enabled: true }
  target.value = review; formError.value = ''; fieldErrors.value = {}; roleFilter.value = ''; editing.value = review ? 'edit' : 'create'
  if (!roles.value.length) roles.value = await request<Role[]>('/api/v1/roles').catch(() => [] as Role[])
}
function toggleRole(id: string, on: boolean) {
  const ids = form.value.roleIds.filter(item => item !== id)
  form.value.roleIds = on ? [...ids, id] : ids
}
function problems(): FieldErrors {
  const value = form.value
  const found: FieldErrors = {}
  if (!value.name.trim()) found.name = t('reviews.enterName')
  if (!value.roleIds.length) found.roleIds = t('reviews.pickRoles')
  const days = Number(value.durationDays)
  const durationOk = Number.isInteger(days) && days >= 1 && days <= 90
  if (!durationOk) found.durationDays = t('reviews.enterDuration')
  const interval = Number(value.intervalDays)
  // The interval is measured against the duration, so it only means something once the duration is a number of days.
  if (durationOk && value.intervalDays.trim() && (!Number.isInteger(interval) || interval < days || interval > 366)) found.intervalDays = t('reviews.enterInterval', { min: days })
  return found
}
async function save() {
  if (saving.value) return
  formError.value = ''
  if (invalid()) return
  const value = form.value
  const body = { name: value.name.trim(), description: value.description.trim() || undefined, roleIds: value.roleIds, durationDays: Number(value.durationDays),
    intervalDays: value.intervalDays.trim() ? Number(value.intervalDays) : undefined, unreviewed: value.unreviewed, enabled: value.enabled,
    nextRunAt: value.nextRun ? new Date(`${value.nextRun}T00:00:00`).toISOString() : undefined }
  saving.value = true
  try {
    const review = target.value
    const saved = review ? await request<Review>(`/api/v1/access-reviews/${encodeURIComponent(review.id)}`, { method: 'PUT', body })
      : await request<Review>('/api/v1/access-reviews', { method: 'POST', body })
    selectedId.value = saved.id; editing.value = null; toast.show(t(review ? 'reviews.saved' : 'reviews.created')); await load()
  } catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
async function remove() {
  const review = deleting.value
  if (!review || saving.value) return
  saving.value = true
  try {
    await request<null>(`/api/v1/access-reviews/${encodeURIComponent(review.id)}`, { method: 'DELETE' })
    deleting.value = null
    if (selectedId.value === review.id) { selectedId.value = ''; roundId.value = '' }
    toast.show(t('reviews.deleted')); await load()
  } catch (reason) { toast.show(errorMessage(reason), 'error') } finally { saving.value = false }
}
onMounted(load)
</script>
<template>
  <PageHeading :title="t('titles.accessReviews')" :description="t('reviews.description')">
    <UiButton variant="secondary" :loading="loading" @click="load"><RefreshCw :size="15" />{{ t('shared.refresh') }}</UiButton>
    <UiButton v-permission="'system.access-review.btn.manage'" @click="openEditor(null)"><Plus :size="16" />{{ t('reviews.create') }}</UiButton>
  </PageHeading>
  <p v-if="error" class="panel p-6 text-center text-xs text-rose-600" role="alert">{{ error }}</p>
  <div v-else class="space-y-6">
    <section class="panel overflow-hidden" aria-labelledby="review-list">
      <header class="flex items-center gap-3 border-b border-line px-6 py-5"><span class="flex size-9 items-center justify-center rounded-xl bg-brand-soft text-brand"><CalendarCheck :size="18" /></span><div><h2 id="review-list" class="text-sm font-semibold">{{ t('reviews.list') }}</h2><p class="mt-1 text-[11px] text-muted">{{ t('reviews.listCaption') }}</p></div></header>
      <p v-if="!loading && !reviews.length" class="px-6 py-8 text-center text-xs text-muted">{{ t('reviews.none') }}</p>
      <ul class="divide-y divide-line">
        <li
          v-for="review in reviews"
          :key="review.id"
          class="px-6 py-4"
          :class="review.id === selectedId ? 'bg-canvas/40' : ''"
          :aria-current="review.id === selectedId ? 'true' : undefined"
          :data-review="review.name"
        >
          <div class="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p class="text-sm font-medium">{{ review.name }}</p>
              <p class="mt-1 text-[11px] text-muted">{{ schedule(review) }}<template v-if="review.nextRunAt"> · {{ review.enabled ? t('reviews.next', { time: dateLabel(review.nextRunAt) }) : t('reviews.paused') }}</template> · {{ t('reviews.unreviewed', { action: fallbackEffect(review.unreviewed) }) }}<template v-if="review.description"> · {{ review.description }}</template></p>
              <p class="mt-2 flex flex-wrap gap-1.5"><span v-for="role in review.roles" :key="role.id" class="badge bg-canvas text-ink">{{ role.name }}</span></p>
              <p v-if="review.openRound" class="mt-2 text-[11px] text-brand">{{ t('reviews.running', { done: review.openRound.progress.total - review.openRound.progress.pending, total: review.openRound.progress.total, time: dateLabel(review.openRound.dueAt) }) }}</p>
            </div>
            <div class="flex gap-1">
              <button type="button" class="table-action" :aria-label="t('reviews.viewNamed', { name: review.name })" @click="select(review)"><ListChecks :size="14" /></button><button
                v-if="!review.openRound"
                v-permission="'system.access-review.btn.manage'"
                type="button"
                class="table-action"
                :aria-label="t('reviews.startNamed', { name: review.name })"
                :data-tooltip="t('reviews.start')"
                @click="start(review)"
              >
                <Play :size="14" />
              </button><button
                v-permission="'system.access-review.btn.manage'"
                type="button"
                class="table-action"
                :aria-label="t('reviews.editNamed', { name: review.name })"
                @click="openEditor(review)"
              >
                <Pencil :size="14" />
              </button><button
                v-permission="'system.access-review.btn.manage'"
                type="button"
                class="table-action hover:text-rose-600"
                :aria-label="t('reviews.deleteNamed', { name: review.name })"
                @click="deleting = review"
              >
                <Trash2 :size="14" />
              </button>
            </div>
          </div>
        </li>
      </ul>
    </section>
    <section v-if="selected" class="panel overflow-hidden" aria-labelledby="review-rounds">
      <div class="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4">
        <h2 id="review-rounds" class="text-sm font-semibold">{{ t('reviews.rounds') }} · {{ selected.name }}</h2>
        <div v-if="rounds.length" class="flex flex-wrap items-center gap-2">
          <div class="w-64">
            <UiSelect
              :model-value="roundId"
              :label="t('reviews.round')"
              :options="roundOptions"
              compact
              hide-label
              @update:model-value="pickRound"
            />
          </div>
          <div class="w-32">
            <UiSelect
              :model-value="filter"
              :label="t('reviews.show')"
              :options="filters"
              compact
              hide-label
              @update:model-value="pickFilter"
            />
          </div>
        </div>
      </div>
      <p v-if="!rounds.length" class="px-6 py-8 text-center text-xs text-muted">{{ t('reviews.noRounds') }}</p>
      <template v-else-if="round">
        <div class="flex flex-wrap items-center justify-between gap-3 border-b border-line px-6 py-3" data-round>
          <p class="text-[11px] text-muted">
            <span class="badge mr-2 bg-canvas text-ink">{{ roundStatusLabel(round.status) }}</span>{{ open ? t('reviews.due', { time: dateLabel(round.dueAt) }) : t('reviews.ended', { time: dateLabel(round.endedAt), count: round.progress.revoked }) }} · {{ t('reviews.roundSummary', round.progress) }}
          </p>
          <div v-if="open" class="flex gap-2">
            <UiButton v-permission="'system.access-review.btn.manage'" variant="secondary" @click="confirming = 'cancel'">{{ t('reviews.cancel') }}</UiButton>
            <UiButton v-permission="'system.access-review.btn.manage'" @click="confirming = 'complete'">{{ t('reviews.complete') }}</UiButton>
          </div>
        </div>
        <div v-if="deciding && items.items.length" class="flex flex-wrap items-center justify-between gap-3 border-b border-line px-6 py-3">
          <UiCheckbox :checked="allChosen" :indeterminate="!allChosen && chosen.length > 0" :label="t('reviews.selectAll')" @update:checked="toggleAll"><span class="text-xs">{{ t('reviews.selected', { count: chosen.length }) }}</span></UiCheckbox>
          <div class="flex gap-2">
            <UiButton variant="secondary" :disabled="!chosen.length || saving" @click="decide(chosen, 'KEEP')"><Check :size="15" />{{ t('reviews.keepSelected') }}</UiButton>
            <UiButton variant="danger" :disabled="!chosen.length || saving" @click="askRevoke(chosen)"><X :size="15" />{{ t('reviews.revokeSelected') }}</UiButton>
          </div>
        </div>
        <p v-if="!items.items.length" class="px-6 py-8 text-center text-xs text-muted">{{ t('reviews.noItems') }}</p>
        <ul class="divide-y divide-line">
          <li v-for="item in items.items" :key="item.id" class="flex flex-wrap items-start justify-between gap-3 px-6 py-4" :data-item="item.subjectName">
            <div class="flex items-start gap-3">
              <UiCheckbox
                v-if="deciding"
                :checked="chosen.includes(item.id)"
                :label="t('reviews.selectNamed', { name: item.subjectName, role: item.role.name })"
                class="mt-0.5"
                @update:checked="value => toggle(item.id, value)"
              >
                <span class="sr-only">{{ item.subjectName }}</span>
              </UiCheckbox>
              <div>
                <p class="text-sm font-medium">{{ item.subjectName }}<span v-if="item.subjectDetail" class="ml-1 font-mono text-[11px] text-muted">{{ item.subjectDetail }}</span><span class="badge ml-2 bg-canvas text-muted">{{ subjectTypeLabel(item.subjectType) }}</span> → {{ item.role.name }}</p>
                <p class="mt-1 text-[11px]" :class="item.assigned ? 'text-muted' : 'text-rose-600'">{{ period(item) }}<template v-if="item.includeSubUnits"> · {{ t('assignments.withSubUnits') }}</template></p>
                <p v-if="item.decidedByName" class="mt-1 text-[11px] text-muted">{{ t('reviews.decidedBy', { name: item.decidedByName, time: dateLabel(item.decidedAt) }) }}<template v-if="item.comment"> · {{ item.comment }}</template></p>
              </div>
            </div>
            <div class="flex flex-wrap items-center gap-1">
              <span class="badge" :class="decisionClass(item.decision)">{{ decisionLabel(item.decision) }}</span>
              <span v-if="item.outcome" class="badge bg-canvas text-ink">{{ outcomeLabel(item.outcome) }}</span>
              <template v-if="open">
                <button
                  v-if="item.decision !== 'KEEP'"
                  v-permission="'system.access-review.btn.decide'"
                  type="button"
                  class="table-action"
                  :aria-label="t('reviews.keepNamed', { name: item.subjectName, role: item.role.name })"
                  :data-tooltip="t('reviews.keep')"
                  @click="decide([item.id], 'KEEP')"
                >
                  <Check :size="14" />
                </button><button
                  v-if="item.decision !== 'REVOKE'"
                  v-permission="'system.access-review.btn.decide'"
                  type="button"
                  class="table-action hover:text-rose-600"
                  :aria-label="t('reviews.revokeNamed', { name: item.subjectName, role: item.role.name })"
                  :data-tooltip="t('reviews.revoke')"
                  @click="askRevoke([item.id])"
                >
                  <X :size="14" />
                </button><button
                  v-if="item.decision !== 'PENDING'"
                  v-permission="'system.access-review.btn.decide'"
                  type="button"
                  class="table-action"
                  :aria-label="t('reviews.undoNamed', { name: item.subjectName, role: item.role.name })"
                  @click="decide([item.id], 'PENDING')"
                >
                  <Undo2 :size="14" />
                </button>
              </template>
            </div>
          </li>
        </ul>
        <PageControls
          v-if="items.total"
          :page="page"
          :size="size"
          :total="items.total"
          :pages="pages"
          :loading="loading"
          @update:page="turnPage"
          @size="resize"
        />
      </template>
    </section>
  </div>
  <UiDialog
    :model-value="editing !== null"
    :title="editing === 'create' ? t('reviews.create') : t('reviews.editTitle')"
    wide
    :busy="saving"
    @update:model-value="editing = null"
  >
    <form id="access-review" class="space-y-5" novalidate @submit.prevent="save">
      <UiField
        v-model="form.name"
        :label="t('reviews.name')"
        :placeholder="t('reviews.namePlaceholder')"
        required
        :error="fieldErrors.name"
      />
      <UiField v-model="form.description" :label="t('reviews.descriptionLabel')" />
      <fieldset>
        <legend class="field-label">{{ t('reviews.roles', { count: form.roleIds.length }) }}</legend>
        <input v-model="roleFilter" :aria-label="t('reviews.filterRoles')" :placeholder="t('reviews.filterRoles')" class="field mb-2" />
        <div class="max-h-48 space-y-1.5 overflow-y-auto rounded-xl border border-line p-3" data-role-list>
          <UiCheckbox
            v-for="role in shownRoles"
            :key="role.id"
            :checked="form.roleIds.includes(role.id)"
            :label="role.name"
            @update:checked="value => toggleRole(role.id, value)"
          />
        </div>
        <p v-if="fieldErrors.roleIds" class="mt-1 text-[11px] text-rose-600">{{ fieldErrors.roleIds }}</p>
      </fieldset>
      <div class="grid items-end gap-5 sm:grid-cols-2">
        <UiField
          v-model="form.durationDays"
          :label="t('reviews.duration')"
          type="number"
          min="1"
          required
          :error="fieldErrors.durationDays"
        /><UiField
          v-model="form.intervalDays"
          :label="t('reviews.interval')"
          type="number"
          min="1"
          :error="fieldErrors.intervalDays"
        />
      </div>
      <div class="grid items-end gap-5 sm:grid-cols-2">
        <UiDatePicker v-model="form.nextRun" :label="t('reviews.nextRun')" /><UiSelect v-model="form.unreviewed" :label="t('reviews.fallbackLabel')" :options="fallbacks" />
      </div>
      <UiSwitch v-model="form.enabled" :label="t('reviews.enabled')" />
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="editing = null">{{ t('shared.cancel') }}</UiButton><UiButton type="submit" form="access-review" :loading="saving">{{ editing === 'create' ? t('reviews.create') : t('reviews.save') }}</UiButton></template>
  </UiDialog>
  <UiDialog :model-value="revoking !== null" :title="t('reviews.revokeTitle')" :busy="saving" @update:model-value="revoking = null">
    <form id="review-revoke" class="space-y-5" novalidate @submit.prevent="decide(revoking ?? [], 'REVOKE', comment)">
      <p class="text-xs leading-6">{{ t('reviews.revokeText', { count: revoking?.length ?? 0 }) }}</p>
      <UiField v-model="comment" :label="t('reviews.comment')" textarea />
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="revoking = null">{{ t('shared.cancel') }}</UiButton><UiButton type="submit" form="review-revoke" variant="danger" :loading="saving">{{ t('reviews.revoke') }}</UiButton></template>
  </UiDialog>
  <UiDialog :model-value="confirming !== null" :title="confirming === 'complete' ? t('reviews.completeTitle') : t('reviews.cancelTitle')" :busy="saving" @update:model-value="confirming = null">
    <p class="text-xs leading-6">{{ confirming === 'complete' ? t('reviews.completeText', { revoke: round?.progress.revoke ?? 0, pending: round?.progress.pending ?? 0, action: fallbackEffect(selected?.unreviewed ?? 'KEEP') }) : t('reviews.cancelText') }}</p>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="confirming = null">{{ t('shared.cancel') }}</UiButton><UiButton :variant="confirming === 'complete' ? 'primary' : 'danger'" :loading="saving" @click="finish">{{ confirming === 'complete' ? t('reviews.complete') : t('reviews.cancel') }}</UiButton></template>
  </UiDialog>
  <UiDialog :model-value="deleting !== null" :title="t('reviews.deleteTitle')" :busy="saving" @update:model-value="deleting = null">
    <p class="text-xs leading-6">{{ t('reviews.deleteWarning', { name: deleting?.name }) }}</p>
    <template #footer><UiButton variant="secondary" :disabled="saving" @click="deleting = null">{{ t('shared.cancel') }}</UiButton><UiButton variant="danger" :loading="saving" @click="remove">{{ t('reviews.delete') }}</UiButton></template>
  </UiDialog>
</template>
