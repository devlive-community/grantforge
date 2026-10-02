<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import { useAuth } from '@/stores/auth'

/** Shows its content only if the user may use the console resource `code`; the `denied` slot otherwise. */
const { code } = defineProps<{ code: string }>()
const auth = useAuth()
</script>
<template>
  <slot v-if="auth.can(code)"></slot>
  <slot v-else name="denied"></slot>
</template>
