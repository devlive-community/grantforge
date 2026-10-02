<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed } from 'vue'
import { CalendarPlus, Trash2 } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { request } from '@/lib/api'
import { accessTypesAt, canEndAt, chooseLevel, childrenOf, endsFor, type ItemList, type LevelDraft, type PolicyDraft,
  type PolicyKind, type ServiceType } from '@/lib/policy'
import PolicyItems from '@/components/PolicyItems.vue'
import UiField from '@/components/UiField.vue'
import UiSelect from '@/components/UiSelect.vue'
import UiSwitch from '@/components/UiSwitch.vue'
import UiTagInput from '@/components/UiTagInput.vue'

/**
 * Edits any policy of any service type: the type's resource levels are chosen top down with their values (looked up
 * from the service where its plugin can), and the items offer the access types, conditions and masking methods the type
 * defines. Problems the server reports appear at the inputs they concern.
 */
const draft = defineModel<PolicyDraft>({ required: true })
const { type, kind, serviceId, errors } = defineProps<{ type: ServiceType; kind: PolicyKind; serviceId: string; errors: Record<string, string> }>()
const { t } = useI18n()

const leaf = computed(() => draft.value.levels.at(-1)?.name)
const accessTypes = computed(() => accessTypesAt(type, leaf.value))
const allowedEnds = computed(() => endsFor(type, kind))
const lists = computed<{ list: ItemList; title: string; description: string }[]>(() => kind === 'ACCESS'
  ? [{ list: 'allow', title: t('policies.allow'), description: t('policies.allowHint') },
      { list: 'allowExceptions', title: t('policies.allowExceptions'), description: t('policies.allowExceptionsHint') },
      { list: 'deny', title: t('policies.deny'), description: t('policies.denyHint') },
      { list: 'denyExceptions', title: t('policies.denyExceptions'), description: t('policies.denyExceptionsHint') }]
  : [{ list: 'allow', title: kind === 'DATA_MASK' ? t('policies.maskItems') : t('policies.filterItems'),
      description: kind === 'DATA_MASK' ? t('policies.maskItemsHint') : t('policies.filterItemsHint') }])

function levelOf(name: string) { return type.resources.find(level => level.name === name) }
/** The choices for the level at a depth: the top levels, or the levels below the one above. */
function choices(depth: number) {
  const parent = depth === 0 ? undefined : draft.value.levels[depth - 1]?.name
  return childrenOf(type, parent).map(level => ({ value: level.name, label: level.label }))
}
/** Whether the chain may grow below its last level, and whether it must. */
const below = computed(() => {
  const last = leaf.value ? levelOf(leaf.value) : undefined
  if (!last) return { offered: false, required: false }
  const children = childrenOf(type, last.name)
  return { offered: children.length > 0, required: !canEndAt(type, last) }
})
function patchLevel(depth: number, patch: Partial<LevelDraft>) {
  draft.value = { ...draft.value, levels: draft.value.levels.map((level, at) => at === depth ? { ...level, ...patch } : level) }
}
function lookup(depth: number) {
  const level = levelOf(draft.value.levels[depth]?.name ?? '')
  if (!level?.lookupSupported) return undefined
  const context = Object.fromEntries(draft.value.levels.slice(0, depth).map(entry => [entry.name, entry.values]))
  return (text: string) => request<string[]>(`/api/v1/services/${encodeURIComponent(serviceId)}/lookup`, {
    method: 'POST', body: { resource: level.name, userInput: text, context, limit: 20 } })
}
function suggest(subject: 'USER' | 'GROUP' | 'ROLE', text: string) {
  return request<string[]>(`/api/v1/policy-subjects?kind=${subject}&text=${encodeURIComponent(text)}&limit=20`)
}
function setItems(list: ItemList, items: PolicyDraft[ItemList]) { draft.value = { ...draft.value, [list]: items } }
function setPeriod(index: number, part: 'from' | 'until', value: string) {
  draft.value = { ...draft.value, validity: draft.value.validity.map((period, at) => at === index ? { ...period, [part]: value } : period) }
}
</script>
<template>
  <div class="space-y-8">
    <section class="grid gap-5 md:grid-cols-2">
      <UiField v-model="draft.name" :label="t('policies.name')" :error="errors.name" required />
      <UiTagInput v-model="draft.labels" :label="t('policies.labels')" :placeholder="t('policies.labelsPlaceholder')" :error="errors.labels" />
      <div class="md:col-span-2"><UiField v-model="draft.description" :label="t('policies.descriptionLabel')" :error="errors.description" textarea /></div>
      <UiSwitch v-model="draft.enabled" :label="t('policies.enabledSwitch')" />
      <div>
        <UiSwitch v-model="draft.override" :label="t('policies.overrideSwitch')" />
        <p class="mt-1 text-[11px] text-muted">{{ t('policies.overrideHint') }}</p>
      </div>
    </section>

    <section class="space-y-4" aria-labelledby="policy-resources">
      <div>
        <h3 id="policy-resources" class="text-sm font-semibold">{{ t('policies.resources') }}</h3>
        <p class="mt-0.5 text-[11px] text-muted">{{ t('policies.resourcesHint') }}</p>
      </div>
      <p v-if="errors.resources" class="text-xs text-rose-600" role="alert">{{ errors.resources }}</p>
      <div v-for="(level, depth) in draft.levels" :key="depth" class="grid gap-4 rounded-xl bg-canvas/60 p-4 md:grid-cols-[12rem_1fr]" :data-level="level.name">
        <UiSelect
          :model-value="level.name"
          :label="depth === 0 ? t('policies.topLevel') : t('policies.levelBelow')"
          :options="choices(depth)"
          @update:model-value="draft = { ...draft, levels: chooseLevel(draft, depth, $event) }"
        />
        <div class="space-y-3">
          <UiTagInput
            :model-value="level.values"
            :label="t('policies.values', { level: levelOf(level.name)?.label ?? level.name })"
            :placeholder="t('policies.valuesPlaceholder')"
            :hint="levelOf(level.name)?.matcher === 'PATH' ? t('policies.pathHint') : t('policies.wildcardHint')"
            :error="errors[`resources.${level.name}`]"
            :suggest="lookup(depth)"
            @update:model-value="patchLevel(depth, { values: $event })"
          />
          <div class="flex flex-wrap gap-6">
            <UiSwitch
              v-if="levelOf(level.name)?.excludesSupported"
              :model-value="level.excludes"
              :label="t('policies.excludes')"
              @update:model-value="patchLevel(depth, { excludes: $event })"
            />
            <UiSwitch
              v-if="levelOf(level.name)?.recursiveSupported"
              :model-value="level.recursive"
              :label="t('policies.recursive')"
              @update:model-value="patchLevel(depth, { recursive: $event })"
            />
          </div>
        </div>
      </div>
      <div v-if="below.offered" class="max-w-xs">
        <UiSelect
          model-value=""
          :label="below.required ? t('policies.chooseBelowRequired') : t('policies.chooseBelow')"
          :options="choices(draft.levels.length)"
          :placeholder="t('policies.chooseBelowPlaceholder')"
          @update:model-value="draft = { ...draft, levels: chooseLevel(draft, draft.levels.length, $event) }"
        />
      </div>
      <p v-if="leaf && !allowedEnds.includes(leaf)" class="text-[11px] text-amber-700" role="note">
        {{ t('policies.endNotAllowed', { levels: allowedEnds.map(name => levelOf(name)?.label ?? name).join('、') }) }}
      </p>
    </section>

    <section class="space-y-6" :aria-label="t('policies.items')">
      <PolicyItems
        v-for="entry in lists"
        :key="entry.list"
        :model-value="draft[entry.list]"
        :list="entry.list"
        :title="entry.title"
        :description="entry.description"
        :kind="kind"
        :type="type"
        :access-types="accessTypes"
        :errors="errors"
        :suggest="suggest"
        @update:model-value="setItems(entry.list, $event)"
      />
    </section>

    <section class="space-y-3" aria-labelledby="policy-validity">
      <div class="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h3 id="policy-validity" class="text-sm font-semibold">{{ t('policies.validity') }}</h3>
          <p class="mt-0.5 text-[11px] text-muted">{{ t('policies.validityHint') }}</p>
        </div>
        <button type="button" class="table-action" @click="draft = { ...draft, validity: [...draft.validity, { from: '', until: '' }] }">
          <CalendarPlus :size="13" />{{ t('policies.addPeriod') }}
        </button>
      </div>
      <p v-if="errors.validity" class="text-xs text-rose-600" role="alert">{{ errors.validity }}</p>
      <div v-for="(period, index) in draft.validity" :key="index" class="grid items-end gap-4 md:grid-cols-[1fr_1fr_auto]" :data-period="index">
        <UiField :model-value="period.from" type="datetime-local" :label="t('policies.from')" @update:model-value="setPeriod(index, 'from', $event)" />
        <UiField
          :model-value="period.until"
          type="datetime-local"
          :label="t('policies.until')"
          :error="errors[`validity[${index}]`]"
          @update:model-value="setPeriod(index, 'until', $event)"
        />
        <button type="button" class="icon-button mb-1 hover:text-rose-600" :aria-label="t('policies.removePeriod', { number: index + 1 })" @click="draft = { ...draft, validity: draft.validity.filter((_, at) => at !== index) }">
          <Trash2 :size="15" />
        </button>
      </div>
    </section>
  </div>
</template>
