<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed } from 'vue'
import { FolderPlus, Plus, Trash2 } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { childPath, emptyComparison, emptyGroup, MAX_GROUP_DEPTH, takesList, takesValue, variablesFor, type ComparisonNode,
  type ConditionNode, type DataEntityField, type DataVariable, type GroupNode } from '@/lib/dataCondition'
import { dataLabels } from '@/lib/dataLabels'
import UiField from '@/components/UiField.vue'
import UiSelect from '@/components/UiSelect.vue'
import UiTagInput from '@/components/UiTagInput.vue'

/**
 * Builds a data policy condition: comparisons of an entity's filterable fields with values or with the reader's own
 * details, joined with and or or in groups that nest and can be negated. Problems the server reports appear at the
 * comparison they concern.
 */
const group = defineModel<GroupNode>({ required: true })
const { fields, variables, errors, path = 'condition', depth = 1 } = defineProps<{
  fields: DataEntityField[]; variables: DataVariable[]; errors: Record<string, string>; path?: string; depth?: number
}>()
const emit = defineEmits<{ remove: [] }>()
const { t } = useI18n()
const labels = dataLabels(t)

const fieldOptions = computed(() => fields.map(field => ({ value: field.code, label: labels.field(field.code, field.name) })))
const ownErrors = computed(() => [errors[path], errors[`${path}.${group.value.join}`]].filter(Boolean))

function fieldOf(node: ComparisonNode) { return fields.find(field => field.code === node.field) }
function replace(index: number, node: ConditionNode) {
  group.value = { ...group.value, children: group.value.children.map((child, at) => at === index ? node : child) }
}
function remove(index: number) { group.value = { ...group.value, children: group.value.children.filter((_, at) => at !== index) } }
function changeField(index: number, code: string) {
  replace(index, emptyComparison(fields.find(field => field.code === code)))
}
function changeOperator(index: number, node: ComparisonNode, op: string) {
  // Lists and single values do not convert into each other; variables must fit the new comparison.
  const keepsVariable = variablesFor(variables, fieldOf(node), op).some(variable => variable.key === node.variable)
  replace(index, { ...node, op, variable: keepsVariable ? node.variable : '', value: takesList(op) ? '' : node.value,
    values: takesList(op) ? node.values : [] })
}
function operatorOptions(node: ComparisonNode) {
  return (fieldOf(node)?.operators ?? []).map(op => ({ value: op, label: labels.operator(op) }))
}
function variableOptions(node: ComparisonNode) {
  const fitting = variablesFor(variables, fieldOf(node), node.op)
  return fitting.length ? [{ value: '', label: t('dataPolicies.fixedValue') }, ...fitting.map(variable => ({ value: variable.key,
    label: labels.variable(variable.key) }))] : []
}
function choiceOptions(node: ComparisonNode) {
  const field = fieldOf(node)
  if (field?.type === 'BOOLEAN') return [{ value: 'true', label: t('dataPolicies.true') }, { value: 'false', label: t('dataPolicies.false') }]
  return (field?.choices ?? []).map(choice => ({ value: choice, label: choice }))
}
function suggestChoices(node: ComparisonNode) {
  const field = fieldOf(node)
  return field?.type === 'CHOICE' ? (text: string) => Promise.resolve(field.choices.filter(choice => choice.toLowerCase().includes(text.toLowerCase())))
    : undefined
}
function inputType(node: ComparisonNode) {
  const type = fieldOf(node)?.type
  return type === 'NUMBER' ? 'number' : type === 'TIME' ? 'datetime-local' : 'text'
}
function error(index: number, part = '') { return errors[`${childPath(group.value, path, index)}${part}`] ?? '' }
</script>
<template>
  <div class="space-y-3 rounded-xl border border-line p-3" :class="depth > 1 ? 'bg-canvas/50' : ''" :data-group="path">
    <div class="flex flex-wrap items-center gap-2">
      <div class="flex gap-1 rounded-lg bg-surface p-0.5" role="group" :aria-label="t('dataPolicies.join')">
        <button
          v-for="join in (['and', 'or'] as const)"
          :key="join"
          type="button"
          class="rounded-md px-2.5 py-1 text-[11px] font-medium"
          :class="group.join === join ? 'bg-brand text-white' : 'text-muted hover:text-ink'"
          :aria-pressed="group.join === join"
          @click="group = { ...group, join }"
        >
          {{ join === 'and' ? t('dataPolicies.joinAnd') : t('dataPolicies.joinOr') }}
        </button>
      </div>
      <label class="flex items-center gap-1.5 text-[11px] text-muted">
        <input type="checkbox" :checked="group.negate" @change="group = { ...group, negate: ($event.target as HTMLInputElement).checked }" />
        {{ t('dataPolicies.negate') }}
      </label>
      <span class="flex-1"></span>
      <button type="button" class="table-action" :disabled="!fields.length" @click="group = { ...group, children: [...group.children, emptyComparison(fields[0])] }">
        <Plus :size="13" />{{ t('dataPolicies.addComparison') }}
      </button>
      <button v-if="depth < MAX_GROUP_DEPTH" type="button" class="table-action" @click="group = { ...group, children: [...group.children, emptyGroup()] }">
        <FolderPlus :size="13" />{{ t('dataPolicies.addGroup') }}
      </button>
      <button
        v-if="depth > 1"
        type="button"
        class="table-action hover:text-rose-600"
        :aria-label="t('dataPolicies.removeGroup')"
        @click="emit('remove')"
      >
        <Trash2 :size="13" />
      </button>
    </div>
    <p v-for="message in ownErrors" :key="message" class="text-xs text-rose-600" role="alert">{{ message }}</p>
    <p v-if="!group.children.length" class="text-[11px] text-muted">{{ t('dataPolicies.emptyGroup') }}</p>
    <template v-for="(child, index) in group.children" :key="index">
      <ConditionBuilder
        v-if="child.type === 'group'"
        :model-value="child"
        :fields="fields"
        :variables="variables"
        :errors="errors"
        :path="childPath(group, path, index)"
        :depth="depth + 1"
        @update:model-value="replace(index, $event)"
        @remove="remove(index)"
      />
      <div v-else class="grid items-start gap-2 md:grid-cols-[10rem_9rem_1fr_auto]" :data-comparison="childPath(group, path, index)">
        <UiSelect
          :model-value="child.field"
          :label="t('dataPolicies.field')"
          :options="fieldOptions"
          :error="error(index, '.field')"
          compact
          @update:model-value="changeField(index, $event)"
        />
        <UiSelect
          :model-value="child.op"
          :label="t('dataPolicies.operator')"
          :options="operatorOptions(child)"
          :error="error(index, '.op')"
          compact
          @update:model-value="changeOperator(index, child, $event)"
        />
        <div v-if="takesValue(child.op)" class="space-y-2">
          <UiSelect
            v-if="variableOptions(child).length"
            :model-value="child.variable"
            :label="t('dataPolicies.valueSource')"
            :options="variableOptions(child)"
            compact
            @update:model-value="replace(index, { ...child, variable: $event })"
          />
          <template v-if="!child.variable">
            <UiTagInput
              v-if="takesList(child.op)"
              :model-value="child.values"
              :label="t('dataPolicies.values')"
              :suggest="suggestChoices(child)"
              :error="error(index, '.value')"
              @update:model-value="replace(index, { ...child, values: $event })"
            />
            <UiSelect
              v-else-if="fieldOf(child)?.type === 'CHOICE' || fieldOf(child)?.type === 'BOOLEAN'"
              :model-value="child.value"
              :label="t('dataPolicies.value')"
              :options="choiceOptions(child)"
              :error="error(index, '.value')"
              compact
              @update:model-value="replace(index, { ...child, value: $event })"
            />
            <UiField
              v-else
              :model-value="child.value"
              :label="t('dataPolicies.value')"
              :type="inputType(child)"
              :error="error(index, '.value')"
              @update:model-value="replace(index, { ...child, value: $event })"
            />
          </template>
          <p v-else-if="error(index, '.value')" class="text-xs text-rose-600">{{ error(index, '.value') }}</p>
        </div>
        <span v-else class="pt-8 text-[11px] text-muted">{{ t('dataPolicies.noValue') }}</span>
        <button type="button" class="icon-button mt-6 hover:text-rose-600" :aria-label="t('dataPolicies.removeComparison', { number: index + 1 })" @click="remove(index)">
          <Trash2 :size="14" />
        </button>
        <p v-if="error(index)" class="text-xs text-rose-600 md:col-span-4">{{ error(index) }}</p>
      </div>
    </template>
  </div>
</template>
