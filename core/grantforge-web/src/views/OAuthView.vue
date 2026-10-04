<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef } from 'vue'
import { Copy, KeyRound, RefreshCw, RotateCw } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { errorMessage, request } from '@/lib/api'
import { dateLabel } from '@/lib/format'
import { vPermission } from '@/lib/permission'
import { useToast } from '@/stores/toast'
import type { components } from '@/api/schema'
import PageHeading from '@/components/PageHeading.vue'
import UiButton from '@/components/UiButton.vue'
import UiDialog from '@/components/UiDialog.vue'

type Server = components['schemas']['OAuthServerResponse']
type Key = components['schemas']['OAuthSigningKeyResponse']

const { t } = useI18n(), toast = useToast()
const server = shallowRef<Server | null>(null), loading = ref(false), error = ref(''), rotating = ref(false), busy = ref(false)

// Spring Authorization Server's endpoints below the issuer; clients read them from the discovery document.
const endpoints = computed(() => {
  const issuer = server.value?.issuer ?? ''
  return [
    { label: t('oauth.authorizeEndpoint'), url: `${issuer}/oauth2/authorize` },
    { label: t('oauth.tokenEndpoint'), url: `${issuer}/oauth2/token` },
    { label: t('oauth.userInfoEndpoint'), url: `${issuer}/userinfo` },
    { label: t('oauth.jwksEndpoint'), url: `${issuer}/oauth2/jwks` },
    { label: t('oauth.revokeEndpoint'), url: `${issuer}/oauth2/revoke` },
  ]
})

async function load() {
  loading.value = true; error.value = ''
  try { server.value = await request<Server>('/api/v1/oauth') } catch (reason) { error.value = errorMessage(reason) } finally { loading.value = false }
}
async function rotate() {
  if (busy.value) return
  busy.value = true
  try {
    await request<Key>('/api/v1/oauth/signing-keys/rotate', { method: 'POST' })
    rotating.value = false; toast.show(t('oauth.rotated')); await load()
  } catch (reason) { toast.show(errorMessage(reason), 'error') } finally { busy.value = false }
}
async function copy(text: string) {
  try { await navigator.clipboard.writeText(text); toast.show(t('oauth.copied')) } catch { toast.show(t('oauth.copyFailed'), 'error') }
}
function keyState(key: Key) {
  return key.active ? t('oauth.keyActive') : t('oauth.keyRetired', { time: dateLabel(key.publishedUntil) })
}
onMounted(load)
</script>
<template>
  <PageHeading :title="t('titles.oauth')" :description="t('oauth.description')">
    <UiButton variant="secondary" :loading="loading" @click="load"><RefreshCw :size="15" />{{ t('shared.refresh') }}</UiButton>
    <UiButton v-permission="'platform.oauth.btn.rotate'" :disabled="!server" @click="rotating = true"><RotateCw :size="15" />{{ t('oauth.rotate') }}</UiButton>
  </PageHeading>
  <p v-if="error" class="panel p-6 text-center text-xs text-rose-600" role="alert">{{ error }}</p>
  <div v-else-if="server" class="grid gap-5 xl:grid-cols-[1fr_24rem]">
    <section class="panel p-5" aria-labelledby="oauth-endpoints">
      <h2 id="oauth-endpoints" class="text-sm font-semibold">{{ t('oauth.endpoints') }}</h2>
      <p class="mt-1 text-[11px] text-muted">{{ t('oauth.endpointsHint') }}</p>
      <dl class="mt-4 space-y-3 text-xs">
        <div>
          <dt class="text-muted">{{ t('oauth.issuer') }}</dt>
          <dd class="mt-0.5 break-all font-mono" data-issuer>{{ server.issuer }}</dd>
        </div>
        <div>
          <dt class="text-muted">{{ t('oauth.discovery') }}</dt>
          <dd class="mt-0.5 flex items-start gap-2">
            <code class="min-w-0 flex-1 break-all font-mono" data-discovery>{{ server.discoveryUrl }}</code>
            <button type="button" class="icon-button size-7" :aria-label="t('oauth.copyDiscovery')" @click="copy(server.discoveryUrl)"><Copy :size="13" /></button>
          </dd>
        </div>
        <div v-for="endpoint in endpoints" :key="endpoint.url">
          <dt class="text-muted">{{ endpoint.label }}</dt>
          <dd class="mt-0.5 break-all font-mono">{{ endpoint.url }}</dd>
        </div>
      </dl>
    </section>
    <section class="panel p-5" aria-labelledby="oauth-keys">
      <h2 id="oauth-keys" class="flex items-center gap-2 text-sm font-semibold"><KeyRound :size="15" />{{ t('oauth.keys') }}</h2>
      <p class="mt-1 text-[11px] text-muted">{{ t('oauth.keysHint') }}</p>
      <ul class="mt-3 space-y-2">
        <li v-for="key in server.keys" :key="key.keyId" class="rounded-xl bg-canvas/60 px-3 py-2" :data-key="key.keyId">
          <p class="flex items-center gap-2 text-xs">
            <span class="badge" :class="key.active ? 'bg-emerald-50 text-emerald-700' : 'bg-line text-muted'">{{ keyState(key) }}</span>
            <span class="text-[11px] text-muted">{{ key.algorithm }}</span>
          </p>
          <p class="mt-1 break-all font-mono text-[11px]">{{ key.keyId }}</p>
          <p class="mt-0.5 text-[10px] text-muted">{{ t('oauth.activatedAt', { time: dateLabel(key.activatedAt) }) }}</p>
        </li>
      </ul>
    </section>
  </div>
  <UiDialog v-model="rotating" :title="t('oauth.rotateTitle')" :busy="busy">
    <p class="text-xs leading-6">{{ t('oauth.rotateConfirm') }}</p>
    <template #footer>
      <UiButton variant="secondary" :disabled="busy" @click="rotating = false">{{ t('shared.cancel') }}</UiButton>
      <UiButton :loading="busy" @click="rotate">{{ t('oauth.rotate') }}</UiButton>
    </template>
  </UiDialog>
</template>
