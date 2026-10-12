<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { computed } from 'vue'
import { encode } from 'uqr'

/**
 * A QR code of a text, such as an otpauth link for an authenticator app, encoded by uqr and drawn as one SVG path. It
 * stays dark on white in either theme, with the four-module quiet zone around it that scanners need, since a light code
 * on a dark page is read by fewer apps.
 */
const { value, label } = defineProps<{ value: string; label: string }>()
// The quiet zone is part of the picture rather than of the page around it, so it survives any background.
const QUIET = 4
const code = computed(() => encode(value, { ecc: 'M', border: 0 }))
const box = computed(() => code.value.size + QUIET * 2)
const path = computed(() => {
  const cells: string[] = []
  code.value.data.forEach((row, y) => row.forEach((dark, x) => { if (dark) cells.push(`M${x + QUIET} ${y + QUIET}h1v1h-1z`) }))
  return cells.join('')
})
</script>

<template>
  <svg
    role="img"
    :aria-label="label"
    :viewBox="`0 0 ${box} ${box}`"
    shape-rendering="crispEdges"
    class="block rounded-xl border border-line bg-white"
    data-qr
  >
    <rect :width="box" :height="box" fill="#ffffff" />
    <path :d="path" fill="#0b1220" />
  </svg>
</template>
