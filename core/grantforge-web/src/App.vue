<!--
  Copyright (c) 2026 devlive-community/grantforge

  Licensed under the MIT License. See the LICENSE file in the
  project root for full license text.
-->

<script setup lang="ts">
import StepUpDialog from '@/components/StepUpDialog.vue'
import ToastHub from '@/components/ToastHub.vue'
import TooltipLayer from '@/components/TooltipLayer.vue'
</script>
<template><RouterView /><ToastHub /><StepUpDialog /><TooltipLayer /></template>
