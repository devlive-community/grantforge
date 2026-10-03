<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { components } from '@/api/schema'
import { errorMessage, request } from '@/lib/api'
import { displayName, resourceTypeKeys } from '@/lib/catalog'
import { dataLabels } from '@/lib/dataLabels'
import UiDialog from '@/components/UiDialog.vue'

type Access = components['schemas']['EffectiveAccessResponse']
type Explanation = components['schemas']['AccessExplanationResponse']
type Item = Access['resources'][number]
type Kind = Explanation['kind']

const open = defineModel<boolean>({ required: true })
const { accountId, accountName } = defineProps<{ accountId: string; accountName: string }>()
const { t } = useI18n()
const labels = dataLabels(t)
const access = shallowRef<Access | null>(null), loading = ref(false), error = ref('')
const explained = shallowRef<Explanation | null>(null), explaining = ref(''), explainError = ref('')

// Literal keys, so the message checker sees every one in use.
const holderKeys = { USER: 'assignments.typeUser', GROUP: 'assignments.typeGroup', ORG_UNIT: 'assignments.typeOrgUnit',
  POSITION: 'assignments.typePosition' } as const
const outcomeKeys = { ALLOWED: 'userPermissions.outcomeAllowed', DENIED: 'userPermissions.outcomeDenied', DISABLED: 'userPermissions.outcomeDisabled',
  NOT_GRANTED: 'userPermissions.outcomeNotGranted', UNKNOWN: 'userPermissions.outcomeUnknown' } as const
const viaKeys = { GRANT: 'userPermissions.viaGrant', SYSTEM_ROLE: 'userPermissions.viaSystemRole', ANCESTOR: 'userPermissions.viaAncestor',
  DEPENDENCY: 'userPermissions.viaDependency' } as const
const readKeys = { VISIBLE: 'fieldPolicies.readVisible', MASKED: 'fieldPolicies.readMasked', HIDDEN: 'fieldPolicies.readHidden' } as const
const writeKeys = { EDITABLE: 'fieldPolicies.writeEditable', READONLY: 'fieldPolicies.writeReadonly' } as const

/** Resources with their depth below the top, in catalog order, so the list reads like the navigation. */
const tree = computed(() => {
  const items = access.value?.resources ?? []
  const byCode = new Map(items.map(item => [item.code, item]))
  const depth = (item: Item): number => item.parentCode && byCode.has(item.parentCode) ? depth(byCode.get(item.parentCode) as Item) + 1 : 0
  return items.map(item => ({ item, depth: depth(item) }))
})
const fields = computed(() => Object.entries(access.value?.fields ?? {}).sort(([left], [right]) => left.localeCompare(right)))

async function load() {
  loading.value = true; error.value = ''; explained.value = null; explainError.value = ''
  try {
    access.value = await request<Access>('/api/v1/authz/effective', { method: 'POST', body: { accountId } })
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
watch(open, value => { if (value) void load() }, { immediate: true })

async function explain(kind: Kind, code: string) {
  explaining.value = `${kind}:${code}`; explainError.value = ''
  try {
    explained.value = await request<Explanation>('/api/v1/authz/explain', { method: 'POST', body: { accountId, kind, code } })
  } catch (reason) { explained.value = null; explainError.value = errorMessage(reason) } finally { explaining.value = '' }
}
function fieldLabel(key: string) {
  const [entity = '', field = ''] = key.split('.')
  return `${labels.entity(entity, entity)} · ${labels.field(field, field)}`
}
function ruleLabel(rule: Access['data'][number]['allow'][number]) {
  if (rule.scope === 'CUSTOM_ORGS') return t('userPermissions.departments', { scope: labels.scope(rule.scope), count: rule.orgUnitCount })
  return labels.scope(rule.scope)
}
</script>

<template>
  <UiDialog v-model="open" :title="t('userPermissions.title', { name: accountName })" :description="t('userPermissions.description')" wide>
    <p v-if="error" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ error }}</p>
    <div v-else-if="loading || !access" class="h-24 animate-pulse rounded-xl bg-line"></div>
    <div v-else class="space-y-6 text-xs">
      <section :aria-label="t('userPermissions.roles')">
        <h3 class="field-label">{{ t('userPermissions.roles') }}</h3>
        <p v-if="!access.roles.length" class="text-muted">{{ t('userPermissions.noRoles') }}</p>
        <ul v-else class="divide-y divide-line rounded-xl border border-line">
          <li v-for="role in access.roles" :key="role.code" class="flex flex-wrap items-center gap-2 px-4 py-2.5" :data-role="role.code">
            <span class="font-medium">{{ role.name }}</span>
            <span class="font-mono text-[10px] text-muted">{{ role.code }}</span>
            <span v-if="!role.active" class="badge bg-amber-50 text-amber-700">{{ t('userPermissions.inactive') }}</span>
            <span class="flex-1"></span>
            <span v-for="holder in role.assignedTo" :key="holder.type + holder.id" class="badge">{{ t(holderKeys[holder.type]) }} · {{ holder.name }}</span>
          </li>
        </ul>
      </section>

      <section :aria-label="t('userPermissions.resources')">
        <h3 class="field-label">{{ t('userPermissions.resources') }}</h3>
        <p v-if="!tree.length" class="text-muted">{{ t('userPermissions.noResources') }}</p>
        <ul v-else class="max-h-64 overflow-y-auto rounded-xl border border-line py-1">
          <li
            v-for="{ item, depth } in tree"
            :key="item.code"
            class="flex items-center gap-2 px-4 py-1"
            :style="{ paddingLeft: `${16 + depth * 16}px` }"
            :data-resource="item.code"
          >
            <span class="badge">{{ t(resourceTypeKeys[item.type]) }}</span>
            <span>{{ displayName(item) }}</span>
            <span class="font-mono text-[10px] text-muted">{{ item.code }}</span>
            <span class="flex-1"></span>
            <button type="button" class="table-action" :aria-label="t('userPermissions.explainNamed', { name: displayName(item) })" @click="explain('RESOURCE', item.code)">{{ t('userPermissions.explain') }}</button>
          </li>
        </ul>
      </section>

      <section :aria-label="t('userPermissions.permissions')">
        <h3 class="field-label">{{ t('userPermissions.permissions') }}</h3>
        <p v-if="!access.permissions.length" class="text-muted">{{ t('userPermissions.noPermissions') }}</p>
        <ul v-else class="max-h-48 overflow-y-auto rounded-xl border border-line py-1">
          <li v-for="item in access.permissions" :key="item.code" class="flex items-center gap-2 px-4 py-1" :data-permission="item.code">
            <span class="font-mono">{{ item.code }}</span>
            <span class="text-muted">{{ displayName(item) }}</span>
            <span class="flex-1"></span>
            <button type="button" class="table-action" :aria-label="t('userPermissions.explainNamed', { name: item.code })" @click="explain('PERMISSION', item.code)">{{ t('userPermissions.explain') }}</button>
          </li>
        </ul>
      </section>

      <section v-if="explained || explaining || explainError" class="rounded-xl bg-canvas/60 p-4" role="status" :aria-label="t('userPermissions.why')">
        <div v-if="explaining" class="h-10 animate-pulse rounded-lg bg-line"></div>
        <p v-else-if="explainError" class="text-rose-600">{{ explainError }}</p>
        <template v-else-if="explained">
          <p class="font-semibold">{{ explained.name ?? explained.code }} · {{ t(outcomeKeys[explained.outcome]) }}</p>
          <ol v-if="explained.paths.length" class="mt-2 space-y-2">
            <li v-for="(path, index) in explained.paths" :key="index" class="rounded-lg border border-line bg-surface p-3" data-path>
              <p>
                <template v-for="(role, step) in path.roles" :key="role.code">
                  <span v-if="step > 0" class="text-muted"> → {{ t('userPermissions.inherits') }}&nbsp;</span>
                  <span class="font-medium">{{ role.name }}</span>
                  <span v-if="role.assignedTo.length" class="text-muted">{{ t('userPermissions.assignedTo', { holders: role.assignedTo.map(holder => `${t(holderKeys[holder.type])} ${holder.name}`).join(', ') }) }}</span>
                </template>
              </p>
              <p class="mt-1">
                <template v-for="(resource, step) in path.resources" :key="resource.code">
                  <span v-if="step > 0" class="text-muted"> → </span>
                  <span class="badge">{{ t(viaKeys[resource.via]) }}</span> {{ resource.name }}
                </template>
              </p>
            </li>
          </ol>
          <ul v-if="explained.denials.length" class="mt-2 space-y-1 text-rose-700">
            <li v-for="denial in explained.denials" :key="denial.roleCode + denial.resourceCode">{{ t('userPermissions.deniedBy', { role: denial.roleName, code: denial.resourceCode }) }}</li>
          </ul>
        </template>
      </section>

      <section :aria-label="t('userPermissions.data')">
        <h3 class="field-label">{{ t('userPermissions.data') }}</h3>
        <p v-if="!access.data.length" class="text-muted">{{ t('userPermissions.noData') }}</p>
        <ul v-else class="divide-y divide-line rounded-xl border border-line">
          <li v-for="rule in access.data" :key="rule.entityCode + rule.action" class="flex flex-wrap items-center gap-2 px-4 py-2" :data-rule="`${rule.entityCode}:${rule.action}`">
            <span class="font-medium">{{ labels.entity(rule.entityCode, rule.entityCode) }}</span>
            <span class="badge">{{ labels.action(rule.action) }}</span>
            <span v-for="(allowed, index) in rule.allow" :key="'a' + index" class="badge bg-emerald-50 text-emerald-700">{{ ruleLabel(allowed) }}<template v-if="allowed.conditional"> · {{ t('userPermissions.conditional') }}</template></span>
            <span v-for="(denied, index) in rule.deny" :key="'d' + index" class="badge bg-rose-50 text-rose-700">{{ labels.effect('DENY') }} {{ ruleLabel(denied) }}</span>
          </li>
        </ul>
      </section>

      <section :aria-label="t('userPermissions.fields')">
        <h3 class="field-label">{{ t('userPermissions.fields') }}</h3>
        <p v-if="!fields.length" class="text-muted">{{ t('userPermissions.noFields') }}</p>
        <ul v-else class="divide-y divide-line rounded-xl border border-line">
          <li v-for="[key, mode] in fields" :key="key" class="flex flex-wrap items-center gap-2 px-4 py-2" :data-field="key">
            <span class="font-medium">{{ fieldLabel(key) }}</span>
            <span class="badge">{{ t(readKeys[mode.readMode]) }}</span>
            <span class="badge">{{ t(writeKeys[mode.writeMode]) }}</span>
          </li>
        </ul>
      </section>
    </div>
  </UiDialog>
</template>
