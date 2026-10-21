# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

GroupLoom is a monorepo: `backend/` (Spring Boot, Java 25, Gradle) and
`frontend/` (Vue 3 + Vite + TypeScript). No root-level build. See
`backend/CLAUDE.md` and `frontend/CLAUDE.md` for commands and conventions.

## Cross-cutting rules
- Backend runs on :8080, frontend dev server on :5173. The API base path is
  `/api` (backend `spring.mvc.servlet.path`; Vite dev proxy to :8080 in dev,
  reverse proxy in prod). The frontend calls the backend only via `/api`;
  never hardcode backend URLs in frontend code.
- The OpenAPI spec is the API contract. When an endpoint changes, update
  the backend, regenerate the frontend types, and fix the frontend in the
  same task.
- Contract workflow: start backend → `cd frontend && pnpm gen:api` → fix compile errors.
  Spec URL: http://localhost:8080/api/v3/api-docs. Never hand-edit `src/api/schema.d.ts`.
- Never commit secrets or `.env` files. Never read or write real credentials.
- Don't add dependencies (Gradle or npm) without asking first.
- Don't edit or rename Flyway migrations that have already been committed;
  add a new one.

## Workflow
- Work in vertical slices: migration → entity → repository → service →
  controller → tests → API client → store → component. For anything
  touching more than ~3 files, propose a plan first.
- Write tests with the code. A task isn't done until the relevant build and
  tests pass (plus the frontend checks in `frontend/CLAUDE.md`); run them and
  report the result.
- Keep diffs small and focused. Don't refactor unrelated code.
- Commit messages: Conventional Commits with a short imperative subject,
  e.g. `feat(member): add preference endpoint`, `fix(event): reject past dates`.

## Domain notes
GroupLoom is a platform for managing a group's (company, club, etc.) events.
A group has users, members, events, and event types.
- **Users** are organizers who log in. They create events and event types,
  schedule notifications for upcoming events, and register members.
- **Members** never log in. They set their preferences and confirm event
  attendance through links they receive by email. Those endpoints are public
  but protected by a token in the link, not by a user session.
