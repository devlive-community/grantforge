<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, useTemplateRef } from 'vue'
import { Languages } from '@lucide/vue'
import { useI18n } from 'vue-i18n'
import { LOCALES, LOCALE_LABELS, currentLocale, setLocale } from '@/i18n'
import type { Locale } from '@/i18n'

const open = ref(false)
const root = useTemplateRef<HTMLElement>('root')
const { t } = useI18n()
// The active language is read on every render, so the list marks it as soon as a pick changes it.
const active = computed(() => currentLocale())

function pick(locale: Locale) {
  open.value = false
  setLocale(locale)
}

function onPointerDown(event: PointerEvent) {
  if (!root.value?.contains(event.target as Node)) open.value = false
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') open.value = false
}

onMounted(() => {
  document.addEventListener('pointerdown', onPointerDown)
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onPointerDown)
  document.removeEventListener('keydown', onKeydown)
})
</script>
<template>
  <div ref="root" class="relative">
    <button
      type="button"
      class="icon-button"
      :aria-label="t('layout.switchLanguage')"
      :aria-expanded="open"
      @click="open = !open"
    >
      <Languages :size="18" />
    </button>
    <ul v-if="open" class="absolute right-0 top-11 z-40 min-w-32 overflow-hidden whitespace-nowrap rounded-xl border border-line bg-surface py-1">
      <li v-for="locale in LOCALES" :key="locale">
        <button
          type="button"
          :lang="locale"
          :aria-current="active === locale ? 'true' : undefined"
          :class="active === locale ? 'bg-brand-soft font-medium text-brand' : 'text-muted hover:bg-canvas hover:text-ink'"
          class="block w-full px-3 py-2 text-left text-sm transition"
          @click="pick(locale)"
        >
          {{ LOCALE_LABELS[locale] }}
        </button>
      </li>
    </ul>
  </div>
</template>
