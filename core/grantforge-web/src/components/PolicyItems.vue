<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed } from 'vue'
import { Plus, Trash2 } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { emptyItem, type AccessType, type ItemDraft, type PolicyKind, type ServiceType } from '@/lib/policy'
import UiCheckbox from '@/components/UiCheckbox.vue'
import UiField from '@/components/UiField.vue'
import UiSelect from '@/components/UiSelect.vue'
import UiTagInput from '@/components/UiTagInput.vue'

type Kind = 'USER' | 'GROUP' | 'ROLE'
/** One list of policy items, such as the allow items: whom each is about and what it allows, denies, masks or filters. */
const items = defineModel<ItemDraft[]>({ required: true })
const { list, title, description, kind, type, accessTypes, errors, suggest } = defineProps<{
  list: string; title: string; description: string; kind: PolicyKind; type: ServiceType; accessTypes: AccessType[]
  errors: Record<string, string>; suggest: (kind: Kind, text: string) => Promise<string[]>
}>()
const { t } = useI18n()
const masks = computed(() => type.maskTypes.map(mask => ({ value: mask.name, label: mask.label })))

function change(index: number, patch: Partial<ItemDraft>) {
  items.value = items.value.map((item, at) => at === index ? { ...item, ...patch } : item)
}
function toggle(index: number, item: ItemDraft, name: string, on: boolean) {
  change(index, { accessTypes: on ? [...item.accessTypes, name] : item.accessTypes.filter(other => other !== name) })
}
function error(index: number, part: string) { return errors[`${list}[${index}].${part}`] ?? '' }
function conditionError(index: number, condition: string) { return errors[`${list}[${index}].conditions.${condition}`] ?? '' }
</script>
<template>
  <section class="space-y-3" :data-items="list">
    <div class="flex flex-wrap items-end justify-between gap-2">
      <div><h4 class="text-xs font-semibold">{{ title }}</h4><p class="mt-0.5 text-[11px] text-muted">{{ description }}</p></div>
      <button type="button" class="table-action" @click="items = [...items, emptyItem()]"><Plus :size="13" />{{ t('policies.addItem') }}</button>
    </div>
    <p v-if="errors[list]" class="text-xs text-rose-600" role="alert">{{ errors[list] }}</p>
    <p v-if="!items.length" class="rounded-xl border border-dashed border-line p-4 text-center text-[11px] text-muted">{{ t('policies.noItems') }}</p>
    <div v-for="(item, index) in items" :key="index" class="space-y-4 rounded-xl border border-line p-4" :data-item="`${list}[${index}]`">
      <div class="flex items-center justify-between">
        <span class="eyebrow text-muted">{{ t('policies.itemNumber', { number: index + 1 }) }}</span>
        <button type="button" class="table-action hover:text-rose-600" :aria-label="t('policies.removeItem', { list: title, number: index + 1 })" @click="items = items.filter((_, at) => at !== index)">
          <Trash2 :size="13" />
        </button>
      </div>
      <div class="grid gap-4 md:grid-cols-3">
        <UiTagInput
          :model-value="item.users"
          :label="t('policies.users')"
          :placeholder="t('policies.usersPlaceholder')"
          :error="error(index, 'users')"
          :suggest="text => suggest('USER', text)"
          @update:model-value="change(index, { users: $event })"
        />
        <UiTagInput
          :model-value="item.groups"
          :label="t('policies.groups')"
          :placeholder="t('policies.groupsPlaceholder')"
          :error="error(index, 'groups')"
          :suggest="text => suggest('GROUP', text)"
          @update:model-value="change(index, { groups: $event })"
        />
        <UiTagInput
          :model-value="item.roles"
          :label="t('policies.roles')"
          :placeholder="t('policies.rolesPlaceholder')"
          :error="error(index, 'roles')"
          :suggest="text => suggest('ROLE', text)"
          @update:model-value="change(index, { roles: $event })"
        />
      </div>
      <p v-if="error(index, 'subjects')" class="text-xs text-rose-600">{{ error(index, 'subjects') }}</p>
      <fieldset>
        <legend class="field-label">{{ t('policies.accessTypes') }}</legend>
        <div class="flex flex-wrap gap-x-5 gap-y-2">
          <UiCheckbox
            v-for="access in accessTypes"
            :key="access.name"
            :checked="item.accessTypes.includes(access.name)"
            :label="access.label"
            @update:checked="toggle(index, item, access.name, $event)"
          />
        </div>
        <p v-if="error(index, 'accessTypes')" class="mt-2 text-xs text-rose-600">{{ error(index, 'accessTypes') }}</p>
      </fieldset>
      <div v-if="type.conditions.length" class="grid gap-4 md:grid-cols-2">
        <UiTagInput
          v-for="condition in type.conditions"
          :key="condition.name"
          :model-value="item.conditions[condition.name] ?? []"
          :label="t('policies.condition', { name: condition.label })"
          :error="conditionError(index, condition.name)"
          :hint="t('policies.conditionHint')"
          @update:model-value="change(index, { conditions: { ...item.conditions, [condition.name]: $event } })"
        />
      </div>
      <div v-if="kind === 'DATA_MASK'" class="grid gap-4 md:grid-cols-2">
        <UiSelect
          :model-value="item.maskType"
          :label="t('policies.maskType')"
          :options="masks"
          :error="error(index, 'maskType')"
          required
          @update:model-value="change(index, { maskType: $event })"
        />
        <UiField
          :model-value="item.maskValue"
          :label="t('policies.maskValue')"
          :placeholder="type.maskTypes.find(mask => mask.name === item.maskType)?.transformer ?? ''"
          :error="error(index, 'maskValue')"
          @update:model-value="change(index, { maskValue: $event })"
        />
      </div>
      <UiField
        v-if="kind === 'ROW_FILTER'"
        :model-value="item.rowFilter"
        :label="t('policies.rowFilter')"
        :placeholder="t('policies.rowFilterPlaceholder')"
        :error="error(index, 'rowFilter')"
        textarea
        required
        @update:model-value="change(index, { rowFilter: $event })"
      />
    </div>
  </section>
</template>
