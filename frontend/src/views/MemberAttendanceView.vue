<script setup lang="ts">
import { computed, nextTick, onMounted, ref, useTemplateRef, watch } from 'vue'
import { useRoute } from 'vue-router'

import BaseButton from '@/components/BaseButton.vue'
import ErrorBanner from '@/components/ErrorBanner.vue'
import EventSummaryCard from '@/components/EventSummaryCard.vue'
import LinkProblem from '@/components/LinkProblem.vue'
import ResponseChoice from '@/components/ResponseChoice.vue'
import TextAreaField from '@/components/TextAreaField.vue'
import { useMemberAttendanceStore } from '@/stores/memberAttendance'

const route = useRoute()
const store = useMemberAttendanceStore()

function open(hash: string) {
  store.init(hash)
  void store.load().then(() => {
    noteOpen.value = Boolean(store.note)
  })
}

const noteOpen = ref(false)
const noteField = useTemplateRef<{ $el: HTMLElement }>('noteField')

onMounted(() => open(route.hash || window.location.hash))

// A different link opened in this same tab only changes the fragment.
watch(
  () => route.hash,
  (hash) => {
    if (hash) open(hash)
  },
)

const data = computed(() => store.data)
const closed = computed(() => data.value !== null && !data.value.open)
const cancelled = computed(() => data.value?.event.cancelled === true)
const answeredBefore = computed(() => Boolean(data.value?.response))
const sendLabel = computed(() => (answeredBefore.value ? 'Update reply' : 'Send reply'))

const ANSWER_TEXT = { GOING: 'Going', NOT_GOING: "Can't go" } as const

async function showNote() {
  noteOpen.value = true
  await nextTick()
  noteField.value?.$el.querySelector('textarea')?.focus()
}

function changeAnswer() {
  store.edit()
  noteOpen.value = Boolean(store.note)
}
</script>

<template>
  <div>
    <div v-if="store.status === 'loading'" aria-busy="true" class="mt-2">
      <div data-skeleton class="h-9 w-1/3 animate-pulse rounded-lg bg-[var(--app-fill)]" />
      <div class="mt-6 space-y-3 rounded-2xl border border-[var(--app-line)]/60 bg-[var(--app-surface)] p-5">
        <div data-skeleton class="h-4 w-24 animate-pulse rounded bg-[var(--app-fill)]" />
        <div data-skeleton class="h-7 w-2/3 animate-pulse rounded bg-[var(--app-fill)]" />
        <div data-skeleton class="h-5 w-1/2 animate-pulse rounded bg-[var(--app-fill)]" />
      </div>
    </div>

    <LinkProblem v-else-if="store.status === 'invalid' || store.status === 'expired'" :kind="store.status" />

    <div v-else-if="store.status === 'error' && store.error" class="mt-6">
      <ErrorBanner :error="store.error">
        <BaseButton variant="secondary" @click="store.load()">Retry</BaseButton>
      </ErrorBanner>
    </div>

    <template v-else-if="data">
      <h1 class="text-[28px] leading-tight font-semibold tracking-tight">Hi {{ data.memberName }}</h1>

      <p v-if="closed" data-closed class="mt-4 rounded-xl bg-[var(--app-fill)] px-4 py-3 text-[15px]">
        <template v-if="cancelled">
          <span class="font-medium">This event was cancelled.</span>
          <span v-if="data.event.cancellationMessage" class="mt-1 block whitespace-pre-line text-[var(--app-muted)]">{{ data.event.cancellationMessage }}</span>
        </template>
        <span v-else class="font-medium">This event has started. Replies are closed.</span>
      </p>

      <EventSummaryCard data-event-card class="mt-4" :event="data.event" />

      <p v-if="closed && data.response" data-current-answer class="mt-4 text-[17px]">
        Your reply: {{ ANSWER_TEXT[data.response] }}
      </p>

      <section v-else-if="!closed && store.saved" class="mt-8">
        <p role="status" class="text-[19px] leading-snug font-medium">
          <template v-if="data.response === 'GOING'">Thanks, {{ data.memberName }}. You're going.</template>
          <template v-else>Thanks for letting us know.</template>
        </p>
        <BaseButton class="mt-4" variant="secondary" @click="changeAnswer">Change answer</BaseButton>
      </section>

      <form v-else-if="!closed" class="mt-8" @submit.prevent="store.submit()">
        <h2 class="mb-3 text-xl font-semibold tracking-tight">Will you come?</h2>
        <ResponseChoice v-model="store.choice" />

        <div class="mt-5">
          <TextAreaField
            v-if="noteOpen"
            ref="noteField"
            v-model="store.note"
            label="Add a note for the organizer (optional)"
            name="note"
            :maxlength="280"
          />
          <button
            v-else
            type="button"
            class="min-h-11 rounded-lg text-[15px] text-[var(--app-accent)] hover:underline"
            @click="showNote"
          >
            + Add a note
          </button>
        </div>

        <p v-if="store.error" role="alert" class="mt-4 text-[15px] text-[var(--app-danger)]">
          Couldn't send your reply. Try again.
        </p>

        <BaseButton class="mt-6" type="submit" block :loading="store.saving" :disabled="!store.choice">{{ sendLabel }}</BaseButton>
        <p class="mt-3 text-center text-[13px] text-[var(--app-muted)]">You can change your answer until the event starts.</p>
      </form>
    </template>
  </div>
</template>
