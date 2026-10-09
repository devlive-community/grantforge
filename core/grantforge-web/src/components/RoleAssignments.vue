<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { Plus, Trash2 } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { dateLabel } from '@/lib/format'
import { orgOptions } from '@/lib/org'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import UiButton from './UiButton.vue'
import UiDialog from './UiDialog.vue'
import UiDatePicker from './UiDatePicker.vue'
import UiField from './UiField.vue'
import UiSelect from './UiSelect.vue'
import UiSwitch from './UiSwitch.vue'

type Assignment = components['schemas']['AssignmentResponse']
type SubjectType = Assignment['subjectType']
type Unit = components['schemas']['OrgUnitResponse']
interface Option { value: string; label: string }

const open = defineModel<boolean>({ required: true })
const { roleId, roleName } = defineProps<{ roleId: string; roleName: string }>()
const { t } = useI18n(), toast = useToast()
const assignments = shallowRef<Assignment[]>([]), loading = ref(false), error = ref(''), saving = ref(false), formError = ref('')
const adding = ref(false), subjectType = ref<SubjectType>('USER'), subjectId = ref(''), query = ref('')
const validFrom = ref(''), validTo = ref(''), includeSubUnits = ref(false), candidates = shallowRef<Option[]>([])

// Literal keys, so the message checker sees every one in use.
const typeKeys = { USER: 'assignments.typeUser', GROUP: 'assignments.typeGroup', ORG_UNIT: 'assignments.typeOrgUnit', POSITION: 'assignments.typePosition' } as const
const typeOptions = computed(() => (['USER', 'GROUP', 'ORG_UNIT', 'POSITION'] as const).map(value => ({ value, label: t(typeKeys[value]) })))

async function load() {
  loading.value = true; error.value = ''
  try { assignments.value = await request<Assignment[]>(`/api/v1/roles/${encodeURIComponent(roleId)}/assignments`) } catch (reason) {
    error.value = errorMessage(reason)
  } finally { loading.value = false }
}
watch([open, () => roleId], ([visible]) => { if (visible) { adding.value = false; void load() } }, { immediate: true })

/** Loads who can be picked for the chosen subject type; accounts are searched, the rest listed. */
async function loadCandidates() {
  const type = subjectType.value, text = query.value.trim()
  try {
    if (type === 'USER') {
      const page = await request<{ items: { id: string; username: string; displayName?: string }[] }>('/api/v1/users', { query: { q: text || undefined, page: 1, size: 50 } })
      candidates.value = page.items.map(user => ({ value: user.id, label: user.displayName ? `${user.displayName} (${user.username})` : user.username }))
    } else if (type === 'GROUP') {
      const page = await request<{ items: { id: string; code: string; name: string }[] }>('/api/v1/groups', { query: { q: text || undefined, page: 1, size: 100 } })
      candidates.value = page.items.map(group => ({ value: group.id, label: `${group.name} (${group.code})` }))
    } else if (type === 'ORG_UNIT') {
      candidates.value = orgOptions(await request<Unit[]>('/api/v1/org-units'))
    } else {
      const positions = await request<{ id: string; name: string }[]>('/api/v1/positions/options')
      candidates.value = positions.map(position => ({ value: position.id, label: position.name }))
    }
  } catch (reason) { formError.value = errorMessage(reason) }
}
watch(subjectType, () => { subjectId.value = ''; query.value = ''; includeSubUnits.value = false; if (adding.value) void loadCandidates() })
// Searching accounts and groups waits for a pause in typing.
let pending: ReturnType<typeof setTimeout> | undefined
watch(query, () => { clearTimeout(pending); pending = setTimeout(() => void loadCandidates(), 300) })

function startAdding() {
  formError.value = ''; subjectType.value = 'USER'; subjectId.value = ''; query.value = ''
  validFrom.value = ''; validTo.value = ''; includeSubUnits.value = false; adding.value = true
  void loadCandidates()
}
/** A date picked in the console starts at local midnight; the end date counts as a whole day. */
function instant(date: string, endOfDay: boolean): string | null {
  if (!date) return null
  const start = new Date(`${date}T00:00:00`)
  return new Date(start.getTime() + (endOfDay ? 24 * 3600 * 1000 : 0)).toISOString()
}
async function run(action: () => Promise<unknown>, done: string) {
  if (saving.value) return
  saving.value = true; formError.value = ''
  try { await action(); adding.value = false; toast.show(done); await load() } catch (reason) {
    if (adding.value) formError.value = errorMessage(reason); else toast.show(errorMessage(reason), 'error')
  } finally { saving.value = false }
}
function assign() {
  if (!subjectId.value) { formError.value = t('assignments.chooseSubject'); return }
  const body = { subjectType: subjectType.value, subjectId: subjectId.value, validFrom: instant(validFrom.value, false),
    validTo: instant(validTo.value, true), includeSubUnits: subjectType.value === 'ORG_UNIT' && includeSubUnits.value }
  void run(() => request(`/api/v1/roles/${encodeURIComponent(roleId)}/assignments`, { method: 'POST', body }), t('assignments.added'))
}
function remove(assignment: Assignment) {
  void run(() => request(`/api/v1/role-assignments/${encodeURIComponent(assignment.id)}`, { method: 'DELETE' }), t('assignments.removed'))
}
function period(assignment: Assignment): string {
  if (!assignment.validFrom && !assignment.validTo) return t('assignments.always')
  return t('assignments.period', { from: assignment.validFrom ? dateLabel(assignment.validFrom) : '—', to: assignment.validTo ? dateLabel(assignment.validTo) : '—' })
}
</script>
<template>
  <UiDialog v-model="open" :title="t('assignments.title', { name: roleName })" :description="t('assignments.description')" wide>
    <div class="mb-4 flex justify-end"><UiButton v-if="!adding" variant="secondary" @click="startAdding"><Plus :size="15" />{{ t('assignments.add') }}</UiButton></div>
    <form
      v-if="adding"
      id="role-assignment"
      class="mb-5 grid gap-4 rounded-xl border border-line p-4 sm:grid-cols-2"
      novalidate
      @submit.prevent="assign"
    >
      <UiSelect v-model="subjectType" :label="t('assignments.subjectType')" :options="typeOptions" />
      <UiField v-if="subjectType === 'USER' || subjectType === 'GROUP'" v-model="query" :label="t('assignments.search')" :placeholder="t('assignments.searchPlaceholder')" />
      <div class="sm:col-span-2">
        <UiSelect
          v-model="subjectId"
          :label="t('assignments.subject')"
          :options="candidates"
          :placeholder="t('assignments.subjectPlaceholder')"
          required
        />
      </div>
      <UiDatePicker v-model="validFrom" :max="validTo" :label="t('assignments.validFrom')" />
      <UiDatePicker v-model="validTo" :min="validFrom" :label="t('assignments.validTo')" />
      <div v-if="subjectType === 'ORG_UNIT'" class="sm:col-span-2"><UiSwitch v-model="includeSubUnits" :label="t('assignments.includeSubUnits')" /></div>
      <p v-if="formError" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700 sm:col-span-2" role="alert">{{ formError }}</p>
      <div class="flex justify-end gap-2 sm:col-span-2"><UiButton variant="secondary" :disabled="saving" @click="adding = false">{{ t('shared.cancel') }}</UiButton><UiButton type="submit" form="role-assignment" :loading="saving">{{ t('assignments.assign') }}</UiButton></div>
    </form>
    <p v-if="error" class="text-xs text-rose-600" role="alert">{{ error }}</p>
    <div v-else-if="loading" class="h-12 animate-pulse rounded-lg bg-line"></div>
    <p v-else-if="!assignments.length" class="py-6 text-center text-xs text-muted">{{ t('assignments.empty') }}</p>
    <ul v-else class="divide-y divide-line rounded-lg border border-line">
      <li v-for="assignment in assignments" :key="assignment.id" class="flex flex-wrap items-center gap-3 px-3 py-2.5 text-xs" :data-assignment="assignment.id">
        <span class="badge">{{ t(typeKeys[assignment.subjectType]) }}</span>
        <span class="min-w-0 flex-1"><span class="font-medium">{{ assignment.subjectName }}</span><span v-if="assignment.subjectDetail" class="ml-2 font-mono text-[10px] text-muted">{{ assignment.subjectDetail }}</span><span v-if="assignment.includeSubUnits" class="ml-2 text-[10px] text-brand">{{ t('assignments.withSubUnits') }}</span></span>
        <span class="text-[11px] text-muted">{{ period(assignment) }}</span>
        <span class="badge" :class="assignment.valid ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'">{{ assignment.valid ? t('assignments.valid') : t('assignments.notValid') }}</span>
        <button
          type="button"
          class="table-action hover:text-rose-600"
          :aria-label="t('assignments.removeNamed', { name: assignment.subjectName })"
          :disabled="saving"
          @click="remove(assignment)"
        >
          <Trash2 :size="14" />
        </button>
      </li>
    </ul>
  </UiDialog>
</template>
