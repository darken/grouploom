<script setup lang="ts">
defineProps<{ modelValue: boolean; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

defineSlots<{ default?: () => unknown }>()
</script>

<template>
  <!--
    Space and Enter are handled on keydown (and Space's keyup swallowed) so a keypress toggles exactly once
    in every browser; the 44px hit area is larger than the 51px x 31px track drawn inside it.
  -->
  <button
    type="button"
    role="switch"
    :aria-checked="modelValue"
    :disabled="disabled"
    class="group relative inline-flex h-11 w-[3.75rem] shrink-0 items-center justify-center rounded-full disabled:cursor-not-allowed"
    @click="emit('update:modelValue', !modelValue)"
    @keydown.enter.prevent="!disabled && emit('update:modelValue', !modelValue)"
    @keydown.space.prevent="!disabled && emit('update:modelValue', !modelValue)"
    @keyup.space.prevent
  >
    <span
      class="relative block h-[1.9375rem] w-[3.1875rem] rounded-full transition-colors duration-150 motion-reduce:transition-none"
      :class="modelValue ? 'bg-[var(--app-accent)]' : 'bg-[var(--app-switch-off)]'"
      aria-hidden="true"
    >
      <span
        data-thumb
        class="absolute top-[0.125rem] left-[0.125rem] size-[1.6875rem] rounded-full bg-white shadow-[0_1px_3px_rgba(0,0,0,0.3)] transition-transform duration-150 ease-out motion-reduce:transition-none"
        :class="modelValue ? 'translate-x-[1.25rem]' : 'translate-x-0'"
      />
    </span>
    <span v-if="$slots.default" class="sr-only"><slot /></span>
  </button>
</template>
