<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import { Search } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { roleLabel } from '@/lib/roles'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import UiButton from './UiButton.vue'
import UiCheckbox from './UiCheckbox.vue'
import UiDialog from './UiDialog.vue'

type Role = components['schemas']['RoleResponse']
type Inheritance = components['schemas']['RoleInheritanceResponse']

const open = defineModel<boolean>({ required: true })
const { roleId, roleName } = defineProps<{ roleId: string; roleName: string }>()
const emit = defineEmits<{ saved: [] }>()
const { t } = useI18n(), toast = useToast()
const roles = shallowRef<Role[]>([]), inheritance = shallowRef<Inheritance | null>(null)
const chosen = ref(new Set<string>()), query = ref(''), loading = ref(false), error = ref(''), saving = ref(false), formError = ref('')

/** Roles that inherit from this one; choosing one of them as a parent would close a cycle. */
const descendants = computed(() => new Set((inheritance.value?.descendants ?? []).map(item => item.role.id)))
const candidates = computed(() => {
  const text = query.value.trim().toLowerCase()
  return roles.value.filter(role => role.id !== roleId
    && (!text || role.code.toLowerCase().includes(text) || roleLabel(role).toLowerCase().includes(text)))
})
const changed = computed(() => {
  const current = new Set((inheritance.value?.parents ?? []).map(role => role.id))
  return current.size !== chosen.value.size || [...chosen.value].some(id => !current.has(id))
})

async function load() {
  loading.value = true; error.value = ''; formError.value = ''
  try {
    const [all, found] = await Promise.all([request<Role[]>('/api/v1/roles'),
      request<Inheritance>(`/api/v1/roles/${encodeURIComponent(roleId)}/inheritance`)])
    roles.value = all; inheritance.value = found; chosen.value = new Set(found.parents.map(role => role.id))
  } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
watch([open, () => roleId], ([visible]) => { if (visible) void load() }, { immediate: true })

function toggle(id: string, value: boolean) {
  const next = new Set(chosen.value)
  if (value) next.add(id); else next.delete(id)
  chosen.value = next
}
async function save() {
  if (saving.value) return
  saving.value = true; formError.value = ''
  try {
    inheritance.value = await request<Inheritance>(`/api/v1/roles/${encodeURIComponent(roleId)}/parents`, { method: 'PUT',
      body: { parentIds: [...chosen.value] } })
    chosen.value = new Set(inheritance.value.parents.map(role => role.id))
    toast.show(t('inheritance.saved'))
    emit('saved')
  } catch (reason) { formError.value = errorMessage(reason) } finally { saving.value = false }
}
</script>
<template>
  <UiDialog
    v-model="open"
    :title="t('inheritance.title', { name: roleName })"
    :description="t('inheritance.description')"
    :busy="saving"
    wide
  >
    <p v-if="error" class="text-xs text-rose-600" role="alert">{{ error }}</p>
    <div v-else-if="loading" class="h-40 animate-pulse rounded-lg bg-line"></div>
    <div v-else class="grid gap-6 md:grid-cols-[minmax(0,1fr)_minmax(0,240px)]">
      <section :aria-label="t('inheritance.parents')">
        <h3 class="mb-2 text-xs font-semibold">{{ t('inheritance.parents') }}</h3>
        <div class="mb-3 flex items-center gap-2 rounded-xl border border-line bg-canvas/40 px-3"><Search :size="15" class="text-muted" /><input v-model="query" :aria-label="t('inheritance.search')" :placeholder="t('inheritance.searchPlaceholder')" class="w-full bg-transparent py-2.5 text-xs outline-none" /></div>
        <ul class="max-h-[45vh] space-y-1 overflow-auto">
          <li v-for="role in candidates" :key="role.id" class="flex items-center justify-between gap-3 rounded-lg px-2 py-1.5 hover:bg-canvas/60" :data-role="role.code">
            <UiCheckbox
              :checked="chosen.has(role.id)"
              :label="roleLabel(role)"
              :disabled="saving || (descendants.has(role.id) && !chosen.has(role.id))"
              @update:checked="toggle(role.id, $event)"
            />
            <span class="flex items-center gap-2 text-[10px] text-muted">
              <span v-if="descendants.has(role.id)">{{ t('inheritance.wouldCycle') }}</span>
              <span v-else-if="!role.enabled">{{ t('inheritance.disabled') }}</span>
              <span class="font-mono">{{ role.code }}</span>
            </span>
          </li>
        </ul>
        <p v-if="formError" class="mt-3 text-xs text-rose-600" role="alert">{{ formError }}</p>
      </section>
      <aside class="space-y-5 text-xs">
        <section :aria-label="t('inheritance.ancestors')">
          <h3 class="mb-2 font-semibold">{{ t('inheritance.ancestors') }}</h3>
          <p v-if="!inheritance?.ancestors.length" class="text-muted">{{ t('inheritance.noAncestors') }}</p>
          <ul v-else class="space-y-1" data-list="ancestors">
            <li v-for="item in inheritance.ancestors" :key="item.role.id" :style="{ paddingLeft: `${(item.distance - 1) * 14}px` }"><span class="text-muted">↳</span> {{ roleLabel(item.role) }}</li>
          </ul>
        </section>
        <section :aria-label="t('inheritance.descendants')">
          <h3 class="mb-2 font-semibold">{{ t('inheritance.descendants') }}</h3>
          <p v-if="!inheritance?.descendants.length" class="text-muted">{{ t('inheritance.noDescendants') }}</p>
          <ul v-else class="space-y-1" data-list="descendants">
            <li v-for="item in inheritance.descendants" :key="item.role.id" :style="{ paddingLeft: `${(item.distance - 1) * 14}px` }"><span class="text-muted">↳</span> {{ roleLabel(item.role) }}</li>
          </ul>
        </section>
      </aside>
    </div>
    <template #footer>
      <span class="mr-auto text-[11px] text-muted">{{ t('inheritance.count', { count: chosen.size }) }}</span>
      <UiButton variant="secondary" :disabled="saving" @click="open = false">{{ t('shared.cancel') }}</UiButton>
      <UiButton :loading="saving" :disabled="!changed || loading" @click="save">{{ t('inheritance.save') }}</UiButton>
    </template>
  </UiDialog>
</template>
