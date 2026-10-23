<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, useTemplateRef, watch } from 'vue'

const DEBOUNCE_MS = 250

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const text = ref(props.modelValue)
const input = useTemplateRef<HTMLInputElement>('input')
let timer: ReturnType<typeof setTimeout> | undefined

watch(
  () => props.modelValue,
  (value) => {
    // The route changed under us (Clear search, back/forward): drop any pending typing.
    clearTimeout(timer)
    text.value = value
  },
)

function onInput() {
  clearTimeout(timer)
  timer = setTimeout(() => emit('update:modelValue', text.value), DEBOUNCE_MS)
}

function clear() {
  clearTimeout(timer)
  text.value = ''
  emit('update:modelValue', '')
  input.value?.focus()
}

function isEditable(target: EventTarget | null) {
  return target instanceof HTMLElement && (target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName))
}

function onGlobalKeydown(event: KeyboardEvent) {
  if (event.key !== '/' || event.metaKey || event.ctrlKey || event.altKey || isEditable(event.target)) return
  event.preventDefault()
  input.value?.focus()
}

onMounted(() => document.addEventListener('keydown', onGlobalKeydown))
onBeforeUnmount(() => {
  clearTimeout(timer)
  document.removeEventListener('keydown', onGlobalKeydown)
})
</script>

<template>
  <div class="relative">
    <svg
      class="pointer-events-none absolute top-1/2 left-3.5 size-4 -translate-y-1/2 text-[var(--app-muted)]"
      viewBox="0 0 20 20"
      fill="none"
      aria-hidden="true"
    >
      <circle cx="8.5" cy="8.5" r="5.5" stroke="currentColor" stroke-width="1.8" />
      <path d="m13 13 4 4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
    </svg>
    <input
      ref="input"
      v-model="text"
      type="search"
      name="q"
      autocomplete="off"
      placeholder="Search"
      aria-label="Search members"
      class="h-10 w-full rounded-full border border-transparent bg-[var(--app-fill)] pr-10 pl-10 text-[16px] text-[var(--app-ink)] transition-shadow duration-150 ease-out [appearance:none] placeholder:text-[var(--app-muted)] focus:border-[var(--app-accent)] focus:ring-4 focus:ring-[var(--app-accent)]/20 focus:outline-none [&::-webkit-search-cancel-button]:hidden"
      @input="onInput"
      @keydown.esc="clear"
    />
    <button
      v-if="text"
      type="button"
      aria-label="Clear search"
      class="absolute top-1/2 right-1.5 grid size-7 -translate-y-1/2 place-items-center rounded-full text-[var(--app-muted)] hover:bg-[var(--app-line)]/50"
      @click="clear"
    >
      <svg class="size-4" viewBox="0 0 20 20" fill="none" aria-hidden="true">
        <path d="m6 6 8 8M14 6l-8 8" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
      </svg>
    </button>
  </div>
</template>
