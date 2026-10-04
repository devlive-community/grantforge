<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { Copy, Smartphone } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import UiButton from '@/components/UiButton.vue'
import UiField from '@/components/UiField.vue'

type Status = components['schemas']['MfaStatusResponse']
type Enrollment = components['schemas']['MfaEnrollmentResponse']
type Codes = components['schemas']['RecoveryCodesResponse']

// Two-step sign-in of the signed-in user (D-71): an authenticator app is added by its secret or otpauth link and
// confirmed with a code; recovery codes are shown once, when issued.
const { t } = useI18n(), toast = useToast()
const status = shallowRef<Status | null>(null), enrollment = shallowRef<Enrollment | null>(null), codes = shallowRef<string[]>([])
const code = ref(''), busy = ref(false), error = ref(''), loadError = ref('')
// Groups of four characters are easier to type into an app.
const secret = computed(() => enrollment.value?.secret.match(/.{1,4}/g)?.join(' ') ?? '')

async function load() {
  try { status.value = await request<Status>('/api/v1/me/mfa'); loadError.value = '' } catch (reason) { loadError.value = errorMessage(reason) }
}
async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await action() } catch (reason) { error.value = errorMessage(reason) } finally { busy.value = false }
}
function needCode() {
  if (code.value.trim()) return false
  error.value = t('mfa.enterCode')
  return true
}
const enroll = () => run(async () => { enrollment.value = await request<Enrollment>('/api/v1/me/mfa/enroll', { method: 'POST' }); code.value = '' })
const confirm = () => needCode() || run(async () => {
  codes.value = (await request<Codes>('/api/v1/me/mfa/confirm', { method: 'POST', body: { code: code.value.trim() } })).codes
  enrollment.value = null; code.value = ''; toast.show(t('mfa.enabled')); await load()
})
const renew = () => needCode() || run(async () => {
  codes.value = (await request<Codes>('/api/v1/me/mfa/recovery-codes', { method: 'POST', body: { code: code.value.trim() } })).codes
  code.value = ''; toast.show(t('mfa.renewed')); await load()
})
const disable = () => needCode() || run(async () => {
  await request<null>('/api/v1/me/mfa/disable', { method: 'POST', body: { code: code.value.trim() } })
  code.value = ''; codes.value = []; toast.show(t('mfa.disabled')); await load()
})
async function copy(text: string) {
  try { await navigator.clipboard.writeText(text); toast.show(t('mfa.copied')) } catch { toast.show(t('mfa.copyFailed'), 'error') }
}
onMounted(load)
</script>
<template>
  <section class="panel p-6" aria-labelledby="account-mfa">
    <header class="mb-5 flex items-center gap-3">
      <span class="flex size-9 items-center justify-center rounded-xl bg-brand-soft text-brand"><Smartphone :size="18" /></span>
      <div class="flex-1"><h2 id="account-mfa" class="text-sm font-semibold">{{ t('mfa.title') }}</h2><p class="mt-1 text-[11px] text-muted">{{ t('mfa.caption') }}</p></div>
      <span v-if="status" class="badge" :class="status.enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-line text-muted'" data-mfa-state>{{ status.enabled ? t('mfa.on') : t('mfa.off') }}</span>
    </header>
    <p v-if="loadError" class="text-xs text-rose-600" role="alert">{{ loadError }}</p>
    <div v-if="codes.length" class="mb-5 rounded-xl border border-amber-200 bg-amber-50 p-4" data-recovery-codes>
      <p class="text-xs font-semibold text-amber-900">{{ t('mfa.codesTitle') }}</p>
      <p class="mt-1 text-[11px] leading-5 text-amber-900">{{ t('mfa.codesText') }}</p>
      <ul class="mt-3 grid grid-cols-2 gap-1.5 font-mono text-xs"><li v-for="item in codes" :key="item">{{ item }}</li></ul>
      <div class="mt-3 flex gap-2">
        <UiButton variant="secondary" @click="copy(codes.join('\n'))"><Copy :size="14" />{{ t('mfa.copyCodes') }}</UiButton>
        <UiButton variant="secondary" @click="codes = []">{{ t('mfa.codesSaved') }}</UiButton>
      </div>
    </div>
    <template v-if="status && !status.enabled">
      <div v-if="enrollment" class="space-y-4">
        <p class="text-xs leading-5">{{ t('mfa.scan') }}</p>
        <div>
          <p class="field-label">{{ t('mfa.secret') }}</p>
          <p class="flex items-center gap-2"><code class="break-all font-mono text-sm tracking-wide" data-secret>{{ secret }}</code><button type="button" class="icon-button size-7" :aria-label="t('mfa.copySecret')" @click="copy(enrollment.secret)"><Copy :size="13" /></button></p>
        </div>
        <div>
          <p class="field-label">{{ t('mfa.link') }}</p>
          <p class="flex items-start gap-2"><code class="min-w-0 flex-1 break-all font-mono text-[11px] text-muted" data-uri>{{ enrollment.uri }}</code><button type="button" class="icon-button size-7" :aria-label="t('mfa.copyLink')" @click="copy(enrollment.uri)"><Copy :size="13" /></button></p>
        </div>
        <form id="mfa-confirm" class="space-y-4" novalidate @submit.prevent="confirm">
          <UiField
            v-model="code"
            :label="t('mfa.code')"
            :placeholder="t('mfa.appCodePlaceholder')"
            autocomplete="one-time-code"
            required
          />
          <p v-if="error" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ error }}</p>
          <div class="flex gap-2"><UiButton type="submit" :loading="busy">{{ t('mfa.turnOn') }}</UiButton><UiButton variant="secondary" :disabled="busy" @click="enrollment = null">{{ t('shared.cancel') }}</UiButton></div>
        </form>
      </div>
      <template v-else>
        <p class="mb-4 text-xs leading-5 text-muted">{{ t('mfa.offText') }}</p>
        <p v-if="error" class="mb-4 rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ error }}</p>
        <UiButton :loading="busy" @click="enroll">{{ t('mfa.setUp') }}</UiButton>
      </template>
    </template>
    <form
      v-else-if="status"
      id="mfa-manage"
      class="space-y-4"
      novalidate
      @submit.prevent="renew"
    >
      <p class="text-xs leading-5 text-muted">{{ t('mfa.onText', { count: status.recoveryCodesLeft }) }}</p>
      <UiField
        v-model="code"
        :label="t('mfa.code')"
        :placeholder="t('mfa.codePlaceholder')"
        autocomplete="one-time-code"
        required
      />
      <p v-if="error" class="rounded-lg bg-rose-50 p-3 text-xs text-rose-700" role="alert">{{ error }}</p>
      <div class="flex flex-wrap gap-2"><UiButton type="submit" variant="secondary" :loading="busy">{{ t('mfa.renew') }}</UiButton><UiButton variant="danger" :disabled="busy" @click="disable">{{ t('mfa.turnOff') }}</UiButton></div>
    </form>
  </section>
</template>
