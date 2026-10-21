<script setup lang="ts"></script>

<template>
  <div class="flex min-h-screen flex-col items-center px-6 pt-16 pb-10 sm:justify-center sm:pt-10">
    <div class="mb-8 flex items-center gap-2 text-[17px] font-semibold tracking-tight">
      <svg class="size-6 text-[var(--app-accent)]" viewBox="0 0 20 20" fill="none" aria-hidden="true">
        <path d="M3 6c4-3 10 3 14 0M3 10c4-3 10 3 14 0M3 14c4-3 10 3 14 0" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
      </svg>
      GroupLoom
    </div>
    <main
      class="w-full max-w-[380px] sm:rounded-2xl sm:border sm:border-[var(--app-line)]/60 sm:bg-[var(--app-surface)] sm:p-8 sm:shadow-[0_1px_3px_rgba(0,0,0,0.04)]"
    >
      <slot />
    </main>
  </div>
</template>
