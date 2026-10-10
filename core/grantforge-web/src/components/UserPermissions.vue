<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { CircleHelp } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import type { components } from '@/api/schema'
import { errorMessage, request } from '@/lib/api'
import { displayName, resourceTypeKeys } from '@/lib/catalog'
import { dataLabels } from '@/lib/dataLabels'
import { useAuth } from '@/stores/auth'
import UiButton from '@/components/UiButton.vue'
import UiCheckbox from '@/components/UiCheckbox.vue'
import UiDialog from '@/components/UiDialog.vue'

type Access = components['schemas']['EffectiveAccessResponse']
type Explanation = components['schemas']['AccessExplanationResponse']
type Item = Access['resources'][number]
type Kind = Explanation['kind']
type Role = components['schemas']['RoleResponse']
type Simulated = components['schemas']['SimulationResponse']

const open = defineModel<boolean>({ required: true })
const { accountId, accountName } = defineProps<{ accountId: string; accountName: string }>()
const { t } = useI18n(), auth = useAuth()
const labels = dataLabels(t)
const access = shallowRef<Access | null>(null), loading = ref(false), error = ref('')
const explained = shallowRef<Explanation | null>(null), explaining = ref(''), explainError = ref('')
const roles = shallowRef<Role[]>([]), adding = ref<string[]>([]), removing = ref<string[]>([])
const simulated = shallowRef<Simulated | null>(null), simulating = ref(false), simulateError = ref('')
const canSimulate = computed(() => auth.holds('system.authz.simulate'))
const held = computed(() => (access.value?.roles ?? []).filter(role => role.active))
const addable = computed(() => roles.value.filter(role => role.enabled && !held.value.some(mine => mine.id === role.id)))
const unchanged = computed(() => simulated.value !== null && !simulated.value.gainedResources.length && !simulated.value.lostResources.length
  && !simulated.value.gainedPermissions.length && !simulated.value.lostPermissions.length)

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
  simulated.value = null; simulateError.value = ''; adding.value = []; removing.value = []
  try {
    access.value = await request<Access>('/api/v1/authz/effective', { method: 'POST', body: { accountId } })
    // The simulator offers the tenant's roles; without access to them it is left out.
    roles.value = canSimulate.value ? await request<Role[]>('/api/v1/roles').catch(() => [] as Role[]) : []
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
watch(open, value => { if (value) void load() }, { immediate: true })

async function explain(kind: Kind, code: string) {
  explaining.value = `${kind}:${code}`; explainError.value = ''
  try {
    explained.value = await request<Explanation>('/api/v1/authz/explain', { method: 'POST', body: { accountId, kind, code } })
  } catch (reason) { explained.value = null; explainError.value = errorMessage(reason) } finally { explaining.value = '' }
}
function toggle(which: 'adding' | 'removing', id: string, checked: boolean) {
  const list = which === 'adding' ? adding : removing
  list.value = checked ? [...list.value, id] : list.value.filter(item => item !== id)
}
async function simulate() {
  simulating.value = true; simulateError.value = ''
  try {
    simulated.value = await request<Simulated>('/api/v1/authz/simulate', { method: 'POST',
      body: { accountId, addRoles: adding.value, removeRoles: removing.value, grants: [] } })
  } catch (reason) { simulated.value = null; simulateError.value = errorMessage(reason) } finally { simulating.value = false }
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
            <button
              type="button"
              class="table-action"
              :aria-label="t('userPermissions.explainNamed', { name: displayName(item) })"
              :data-tooltip="t('userPermissions.explain')"
              @click="explain('RESOURCE', item.code)"
            >
              <CircleHelp :size="14" />
            </button>
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
            <button
              type="button"
              class="table-action"
              :aria-label="t('userPermissions.explainNamed', { name: item.code })"
              :data-tooltip="t('userPermissions.explain')"
              @click="explain('PERMISSION', item.code)"
            >
              <CircleHelp :size="14" />
            </button>
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

      <section v-if="canSimulate && roles.length" class="rounded-xl border border-line p-4" :aria-label="t('userPermissions.simulate')">
        <h3 class="text-xs font-semibold">{{ t('userPermissions.simulate') }}</h3>
        <p class="mt-1 text-[11px] text-muted">{{ t('userPermissions.simulateHint') }}</p>
        <div class="mt-3 grid gap-4 sm:grid-cols-2">
          <fieldset>
            <legend class="field-label">{{ t('userPermissions.simulateAdd') }}</legend>
            <p v-if="!addable.length" class="text-muted">{{ t('userPermissions.simulateNothingToAdd') }}</p>
            <div class="max-h-40 space-y-1.5 overflow-y-auto">
              <UiCheckbox
                v-for="role in addable"
                :key="role.id"
                :checked="adding.includes(role.id)"
                :label="role.name"
                @update:checked="toggle('adding', role.id, $event)"
              />
            </div>
          </fieldset>
          <fieldset>
            <legend class="field-label">{{ t('userPermissions.simulateRemove') }}</legend>
            <p v-if="!held.length" class="text-muted">{{ t('userPermissions.noRoles') }}</p>
            <div class="max-h-40 space-y-1.5 overflow-y-auto">
              <UiCheckbox
                v-for="role in held"
                :key="role.id"
                :checked="removing.includes(role.id)"
                :label="role.name"
                @update:checked="toggle('removing', role.id, $event)"
              />
            </div>
          </fieldset>
        </div>
        <UiButton
          class="mt-3"
          variant="secondary"
          :loading="simulating"
          :disabled="!adding.length && !removing.length"
          @click="simulate"
        >
          {{ t('userPermissions.simulateRun') }}
        </UiButton>
        <p v-if="simulateError" class="mt-3 text-rose-600" role="alert">{{ simulateError }}</p>
        <div v-else-if="simulated" class="mt-3 space-y-2" data-simulation>
          <p v-if="unchanged" class="text-muted">{{ t('userPermissions.simulateUnchanged') }}</p>
          <ul v-else class="space-y-1">
            <li v-for="item in simulated.gainedResources" :key="'gr' + item.code" class="text-emerald-700" data-gained>+ {{ t(resourceTypeKeys[item.type]) }} {{ displayName(item) }}</li>
            <li v-for="item in simulated.gainedPermissions" :key="'gp' + item.code" class="font-mono text-emerald-700" data-gained>+ {{ item.code }}</li>
            <li v-for="item in simulated.lostResources" :key="'lr' + item.code" class="text-rose-700" data-lost>− {{ t(resourceTypeKeys[item.type]) }} {{ displayName(item) }}</li>
            <li v-for="item in simulated.lostPermissions" :key="'lp' + item.code" class="font-mono text-rose-700" data-lost>− {{ item.code }}</li>
          </ul>
        </div>
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
