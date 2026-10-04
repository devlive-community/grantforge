<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, onStepUp, request } from '@/lib/api'
import UiButton from '@/components/UiButton.vue'
import UiDialog from '@/components/UiDialog.vue'
import UiField from '@/components/UiField.vue'

// Sensitive calls of a user with two-step sign-in need the second factor again after a while (D-71): this asks for it
// and lets the call repeat. Calls that need it at the same moment share one question.
const { t } = useI18n()
const open = ref(false), code = ref(''), busy = ref(false), error = ref('')
let answer: ((given: boolean) => void) | undefined, asking: Promise<boolean> | undefined

function ask(): Promise<boolean> {
  asking ??= new Promise<boolean>(resolve => {
    code.value = ''; error.value = ''; open.value = true
    answer = given => { asking = undefined; answer = undefined; open.value = false; resolve(given) }
  })
  return asking
}
async function confirm() {
  if (busy.value) return
  if (!code.value.trim()) { error.value = t('mfa.enterCode'); return }
  busy.value = true; error.value = ''
  try {
    await request<null>('/api/v1/auth/step-up', { method: 'POST', body: { code: code.value.trim() }, stepUp: false })
    answer?.(true)
  } catch (reason) { error.value = errorMessage(reason) } finally { busy.value = false }
}
function closed(value: boolean) { if (!value) answer?.(false) }
onMounted(() => onStepUp(ask))
</script>
<template>
  <UiDialog
    :model-value="open"
    :title="t('mfa.stepUpTitle')"
    :description="t('mfa.stepUpText')"
    :busy="busy"
    @update:model-value="closed"
  >
    <form id="step-up" class="space-y-4" novalidate @submit.prevent="confirm">
      <UiField
        v-model="code"
        :label="t('mfa.code')"
        :placeholder="t('mfa.codePlaceholder')"
        autocomplete="one-time-code"
        required
      />
      <p v-if="error" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ error }}</p>
    </form>
    <template #footer>
      <UiButton variant="secondary" :disabled="busy" @click="closed(false)">{{ t('shared.cancel') }}</UiButton>
      <UiButton type="submit" form="step-up" :loading="busy">{{ t('mfa.confirm') }}</UiButton>
    </template>
  </UiDialog>
</template>
