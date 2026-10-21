# Frontend

Vue 3 + Vite + TypeScript, Pinia, Vue Router. Package manager: pnpm.

## Commands
```bash
pnpm install
pnpm dev                 # Vite dev server on :5173, proxies /api to :8080
pnpm build               # vue-tsc type-check + vite build (in parallel)
pnpm type-check
pnpm test:unit           # Vitest (watch mode); `pnpm test:unit --run` for a single pass
pnpm test:unit src/__tests__/App.spec.ts       # single unit test file
pnpm test:e2e            # Playwright (starts dev server automatically)
pnpm test:e2e --project=chromium e2e/vue.spec.ts
pnpm lint                # oxlint then eslint, both with --fix
pnpm format              # prettier on src/
pnpm gen:api             # regenerate src/api/schema.d.ts (backend must be running)
```

## Done criteria
A frontend task isn't done until this passes; run it and report the result:

```bash
pnpm lint && pnpm type-check && pnpm test:unit --run
```

Lint is oxlint first, then ESLint. Don't disable rules to silence errors;
fix the cause.

## Structure
```
src/
  api/          generated types + thin API client (no logic)
  components/   reusable, presentational components
  views/        route-level pages
  stores/       Pinia stores (setup-store style)
  composables/  reusable `useXxx` logic
  router/
```

## Conventions
- Composition API with `<script setup lang="ts">` only; no Options API.
- TypeScript strict. No `any`; no `@ts-ignore` without a comment explaining why.
- API types come from the generated OpenAPI types; don't hand-write
  duplicates of backend DTOs.
- All HTTP calls go through `src/api/`. Components never call `fetch`/axios
  directly. Stores own server state and loading/error state.
- `src/api/client.ts` exports `api`, an `openapi-fetch` client with
  `baseUrl: '/api'`. Paths are written without the prefix:
  `api.GET('/groups')`.
- Props down, events up. Use `defineProps`/`defineEmits` with types.
- Route-level components are lazy-loaded.
- Don't use `v-html` with anything user-supplied.
- Styling: Tailwind, and stick to it. It isn't installed yet; ask before adding it.

## Testing
- Vitest + Vue Test Utils for components and stores; test behavior
  (what the user sees/does), not implementation details.
- Mock the API client module, not `fetch`.
- Playwright only for a few critical end-to-end flows (login, create group…).

## Gotchas
- `src/stores/counter.ts` and the empty router are placeholders; delete
  them once real ones exist.
