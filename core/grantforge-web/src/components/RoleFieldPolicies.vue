<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { components } from '@/api/schema'
import { ApiError, errorMessage, request } from '@/lib/api'
import { dataLabels } from '@/lib/dataLabels'
import { useToast } from '@/stores/toast'
import UiButton from '@/components/UiButton.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiSelect from '@/components/UiSelect.vue'

type Policy = components['schemas']['FieldPolicyResponse']
type Entities = components['schemas']['DataEntitiesResponse']
type ReadMode = Policy['readMode'] | ''
type Mask = NonNullable<Policy['maskStrategy']>
interface Row { entityCode: string; entityName: string; fieldCode: string; fieldName: string; readMode: ReadMode; maskStrategy: Mask; writeMode: Policy['writeMode'] }

const open = defineModel<boolean>({ required: true })
const { roleId, roleName } = defineProps<{ roleId: string; roleName: string }>()
const { t } = useI18n(), toast = useToast()
const labels = dataLabels(t)
const rows = ref<Row[]>([]), loading = ref(false), error = ref(''), saving = ref(false), formError = ref('')
const rowErrors = shallowRef<Record<number, string>>({})

const readOptions = computed(() => [{ value: '', label: t('fieldPolicies.readDefault') }, { value: 'VISIBLE', label: t('fieldPolicies.readVisible') },
  { value: 'MASKED', label: t('fieldPolicies.readMasked') }, { value: 'HIDDEN', label: t('fieldPolicies.readHidden') }])
const maskOptions = computed(() => [{ value: 'EMAIL', label: t('fieldPolicies.maskEmail') }, { value: 'PHONE', label: t('fieldPolicies.maskPhone') },
  { value: 'ID_NUMBER', label: t('fieldPolicies.maskIdNumber') }, { value: 'PARTIAL', label: t('fieldPolicies.maskPartial') },
  { value: 'FULL', label: t('fieldPolicies.maskFull') }])
const writeOptions = computed(() => [{ value: 'EDITABLE', label: t('fieldPolicies.writeEditable') },
  { value: 'READONLY', label: t('fieldPolicies.writeReadonly') }])

async function load() {
  loading.value = true; error.value = ''; formError.value = ''; rowErrors.value = {}
  try {
    const [found, described] = await Promise.all([
      request<Policy[]>(`/api/v1/roles/${encodeURIComponent(roleId)}/field-policies`),
      request<Entities>('/api/v1/data-entities'),
    ])
    const byField = new Map(found.map(policy => [`${policy.entityCode}.${policy.fieldCode}`, policy]))
    rows.value = described.entities.flatMap(entity => entity.securedFields.map(field => {
      const policy = byField.get(`${entity.code}.${field.code}`)
      return {
        entityCode: entity.code, entityName: labels.entity(entity.code, entity.name), fieldCode: field.code,
        fieldName: labels.field(field.code, field.name), readMode: policy?.readMode ?? '', maskStrategy: policy?.maskStrategy ?? 'PARTIAL',
        writeMode: policy?.writeMode ?? 'EDITABLE',
      }
    }))
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
watch(open, value => { if (value) void load() }, { immediate: true })

/** Fields without a read mode carry no policy: the holder's other roles decide, or the field stays visible and editable. */
function policies() {
  return rows.value.filter(row => row.readMode !== '').map(row => ({
    entityCode: row.entityCode, fieldCode: row.fieldCode, readMode: row.readMode,
    maskStrategy: row.readMode === 'MASKED' ? row.maskStrategy : null, writeMode: row.writeMode,
  }))
}
async function save() {
  if (saving.value) return
  saving.value = true; formError.value = ''; rowErrors.value = {}
  try {
    await request<Policy[]>(`/api/v1/roles/${encodeURIComponent(roleId)}/field-policies`, { method: 'PUT', body: { policies: policies() } })
    toast.show(t('fieldPolicies.saved')); open.value = false
  } catch (reason) {
    formError.value = errorMessage(reason)
    if (reason instanceof ApiError) {
      const chosen = rows.value.filter(row => row.readMode !== '')
      const found: Record<number, string> = {}
      for (const issue of reason.problem?.errors ?? []) {
        const index = Number(/^policies\[(\d+)]/.exec(issue.field)?.[1] ?? -1)
        const row = chosen[index]
        if (row) found[rows.value.indexOf(row)] = issue.message
      }
      rowErrors.value = found
    }
  } finally { saving.value = false }
}
</script>

<template>
  <UiDialog
    v-model="open"
    :title="t('fieldPolicies.title', { name: roleName })"
    :description="t('fieldPolicies.description')"
    :busy="saving"
    wide
  >
    <p v-if="error" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ error }}</p>
    <div v-else-if="loading" class="h-24 animate-pulse rounded-xl bg-line"></div>
    <p v-else-if="!rows.length" class="rounded-xl border border-dashed border-line p-6 text-center text-xs text-muted">{{ t('fieldPolicies.empty') }}</p>
    <form v-else id="field-policy-form" novalidate @submit.prevent="save">
      <table class="w-full text-xs">
        <thead>
          <tr class="text-left text-muted">
            <th class="py-2 font-medium">{{ t('fieldPolicies.field') }}</th>
            <th class="py-2 font-medium">{{ t('fieldPolicies.read') }}</th>
            <th class="py-2 font-medium">{{ t('fieldPolicies.mask') }}</th>
            <th class="py-2 font-medium">{{ t('fieldPolicies.write') }}</th>
          </tr>
        </thead>
        <tbody class="divide-y divide-line">
          <tr v-for="(row, index) in rows" :key="`${row.entityCode}.${row.fieldCode}`" :data-field="`${row.entityCode}.${row.fieldCode}`">
            <td class="py-2 pr-3"><span class="font-medium">{{ row.entityName }} · {{ row.fieldName }}</span><p v-if="rowErrors[index]" class="mt-1 text-rose-600">{{ rowErrors[index] }}</p></td>
            <td class="py-2 pr-3">
              <UiSelect
                v-model="row.readMode"
                :label="t('fieldPolicies.readNamed', { name: row.fieldName })"
                :options="readOptions"
                compact
                hide-label
              />
            </td>
            <td class="py-2 pr-3">
              <UiSelect
                v-if="row.readMode === 'MASKED'"
                v-model="row.maskStrategy"
                :label="t('fieldPolicies.maskNamed', { name: row.fieldName })"
                :options="maskOptions"
                compact
                hide-label
              /><span v-else class="text-muted">—</span>
            </td>
            <td class="py-2">
              <UiSelect
                v-model="row.writeMode"
                :label="t('fieldPolicies.writeNamed', { name: row.fieldName })"
                :options="writeOptions"
                :disabled="row.readMode === ''"
                compact
                hide-label
              />
            </td>
          </tr>
        </tbody>
      </table>
      <p v-if="formError" class="mt-4 rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ formError }}</p>
    </form>
    <template #footer>
      <UiButton variant="secondary" :disabled="saving" @click="open = false">{{ t('shared.cancel') }}</UiButton>
      <UiButton v-if="rows.length" type="submit" form="field-policy-form" :loading="saving">{{ t('fieldPolicies.save') }}</UiButton>
    </template>
  </UiDialog>
</template>
