<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { dateLabel } from '@/lib/format'
import { roleLabel } from '@/lib/roles'
import type { components } from '@/api/schema'
import UiDialog from './UiDialog.vue'

type EffectiveRole = components['schemas']['EffectiveRoleResponse']
type Source = EffectiveRole['sources'][number]

const open = defineModel<boolean>({ required: true })
const { accountId, accountName } = defineProps<{ accountId: string; accountName: string }>()
const { t } = useI18n()
const roles = shallowRef<EffectiveRole[]>([]), loading = ref(false), error = ref('')

// Literal keys, so the message checker sees every one in use.
const viaKeys = { USER: 'userRoles.viaUser', GROUP: 'userRoles.viaGroup', ORG_UNIT: 'userRoles.viaOrgUnit', POSITION: 'userRoles.viaPosition' } as const
function via(source: Source) {
  return t(viaKeys[source.subjectType], { name: source.subjectName })
}

watch([open, () => accountId], async ([visible]) => {
  if (!visible) return
  loading.value = true; error.value = ''
  try { roles.value = await request<EffectiveRole[]>(`/api/v1/users/${encodeURIComponent(accountId)}/roles`) } catch (reason) {
    error.value = errorMessage(reason)
  } finally { loading.value = false }
}, { immediate: true })
</script>
<template>
  <UiDialog v-model="open" :title="t('userRoles.title', { name: accountName })" :description="t('userRoles.description')" wide>
    <p v-if="error" class="text-xs text-rose-600" role="alert">{{ error }}</p>
    <div v-else-if="loading" class="h-12 animate-pulse rounded-lg bg-line"></div>
    <p v-else-if="!roles.length" class="py-6 text-center text-xs text-muted">{{ t('userRoles.empty') }}</p>
    <ul v-else class="space-y-3">
      <li v-for="role in roles" :key="role.role.id" class="rounded-lg border border-line p-3" :data-role="role.role.code">
        <div class="flex flex-wrap items-center gap-2"><span class="font-medium">{{ roleLabel(role.role) }}</span><span class="font-mono text-[10px] text-muted">{{ role.role.code }}</span><span class="badge ml-auto" :class="role.active ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-700'">{{ role.active ? t('userRoles.active') : role.role.enabled ? t('userRoles.notValid') : t('userRoles.roleDisabled') }}</span></div>
        <ul class="mt-2 space-y-1 text-[11px] text-muted">
          <li v-for="source in role.sources" :key="source.id">{{ via(source) }}<template v-if="source.validTo"> · {{ t('userRoles.until', { date: dateLabel(source.validTo) }) }}</template><template v-if="!source.valid"> · {{ t('userRoles.sourceNotValid') }}</template></li>
        </ul>
      </li>
    </ul>
  </UiDialog>
</template>
