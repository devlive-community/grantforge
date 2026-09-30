<script setup lang="ts">
const { variant = 'primary', type = 'button', loading = false, disabled = false } = defineProps<{
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost'; type?: 'button' | 'submit' | 'reset'; loading?: boolean; disabled?: boolean
}>()
const variants = {
  primary: 'bg-brand text-white shadow-sm hover:bg-[#534bec]',
  secondary: 'border border-line bg-surface text-ink hover:bg-canvas',
  danger: 'bg-rose-600 text-white hover:bg-rose-700',
  ghost: 'text-muted hover:bg-canvas hover:text-ink',
}
</script>
<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    :aria-busy="loading"
    :class="variants[variant]"
    class="inline-flex min-h-10 items-center justify-center gap-2 rounded-xl px-4 text-[13px] font-medium transition disabled:opacity-50"
  >
    <span v-if="loading" class="size-4 animate-spin rounded-full border-2 border-current border-r-transparent" aria-hidden="true"></span>
    <slot></slot>
  </button>
</template>
