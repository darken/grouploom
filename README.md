# GroupLoom

GroupLoom helps a group (a company, a club, a team) organize its events.
Organizers sign in to manage event types, schedule events and register
members. Members never create an account: they manage their preferences
through personal links they receive by email.

## Features

- **Organizer accounts**: sign-up, login and logout, with short-lived JWT
  access tokens and refresh tokens.
- **Event types**: create, edit and archive the kinds of events your group
  runs. Event types that are in use can't be deleted.
- **Events**: schedule events with time zone support. Dates in the past are
  rejected.
- **Members**: a searchable member directory.
- **Member preferences**: each member gets a tokenized link to a public
  preferences page. Organizers can copy the link or send it by email.
- **Transactional email**: outgoing mail goes through a database outbox,
  and a background dispatcher retries failed deliveries.

## Tech stack

| Area     | Stack                                                                 |
| -------- | --------------------------------------------------------------------- |
| Backend  | Java 25, Spring Boot 4, Spring Security 7, Spring Data JPA, Flyway     |
| Database | PostgreSQL 17                                                         |
| API      | REST under `/api`, documented with OpenAPI (springdoc)                |
| Frontend | Vue 3, Vite, TypeScript, Pinia, Vue Router, Tailwind CSS              |
| Testing  | JUnit 5, Mockito, Testcontainers · Vitest, Vue Test Utils, Playwright |
| Local    | Docker Compose (Postgres + [Mailpit](https://mailpit.axllent.org/))   |

## Repository layout

```
backend/    Spring Boot API (Gradle)
frontend/   Vue single-page app (pnpm)
```

There is no root-level build: each part is built and run on its own.

## Prerequisites

- JDK 25
- Docker (Postgres and Mailpit for local dev, Testcontainers for tests)
- Node.js 22.18+ or 24.12+
- pnpm

## Getting started

1. **Start the backend** on http://localhost:8080. Spring Boot starts the
   containers in `backend/compose.yaml` (Postgres and Mailpit) for you and
   runs the Flyway migrations.

   ```bash
   cd backend && ./gradlew bootRun
   ```

2. **Start the frontend** on http://localhost:5173. The Vite dev server
   proxies `/api` to the backend.

   ```bash
   cd frontend && pnpm install && pnpm dev
   ```

3. Open http://localhost:5173 and sign up as an organizer.

Useful local URLs:

| URL                                            | What                             |
| ---------------------------------------------- | -------------------------------- |
| http://localhost:5173                          | Web app                          |
| http://localhost:8080/api/swagger-ui/index.html | Swagger UI                       |
| http://localhost:8080/api/v3/api-docs          | OpenAPI spec                     |
| http://localhost:8025                          | Mailpit inbox (all outgoing mail) |

## Configuration

Settings live in `backend/src/main/resources/application.yaml`.
`./gradlew bootRun` activates the `dev` profile, which sends mail to
Mailpit on `localhost:1025`.

| Property                     | Default                           | Notes                                                                                                                          |
| ---------------------------- | --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `GROUPLOOM_JWT_SECRET`       | _(empty)_                         | HS256 signing key, at least 32 bytes. If empty, a random key is generated on each start and sessions don't survive a restart. **Set it in production.** |
| `grouploom.public-base-url`  | `http://localhost:5173`           | Base URL used to build the links in member emails.                                                                             |
| `grouploom.mail.from`        | `no-reply@grouploom.localhost`    | Sender address for outgoing email.                                                                                             |
| `grouploom.auth.cookie-secure` | `false`                         | Set to `true` when served over HTTPS.                                                                                          |

## Demo

With `GROUPLOOM_DEMO_ENABLED=true` the backend seeds a sample group on
startup, unless the demo user already exists. The group, "Riverside Running
Club", has event types (one archived), 15 members, past and upcoming events
with replies (one event cancelled), and a couple of member preferences.
Dates are relative to the day it was seeded. Members use `@example.com`
addresses, and seeding never queues email.

| Email              | Password         |
| ------------------ | ---------------- |
| `demo@example.com` | `grouploom-demo` |

Override them with `GROUPLOOM_DEMO_EMAIL` and `GROUPLOOM_DEMO_PASSWORD`.
To try it locally:

```bash
cd backend && GROUPLOOM_DEMO_ENABLED=true ./gradlew bootRun
```

## Deploy (Render)

`render.yaml` is a [Render Blueprint](https://render.com/docs/blueprint-spec)
with two free services: `grouploom-api` (the backend, built from
`backend/Dockerfile` with the `prod` profile) and `grouploom` (the frontend
as a static site). The static site rewrites `/api/*` to the backend, so the
browser only ever talks to one origin. The database is on
[Neon](https://neon.tech) and email goes through [Brevo](https://www.brevo.com).

1. **Neon**: create a project and copy the **direct** connection string
   (not the pooled one; Flyway needs a direct connection). Turn it into a
   JDBC URL plus a username and password:
   `jdbc:postgresql://<host>/<database>?sslmode=require`.
2. **Brevo**: verify a sender address (Senders & IP → Senders), then
   create an SMTP key (SMTP & API → SMTP). Note the SMTP login shown there.
3. **Render**: New → Blueprint, pick this repository, and fill in the
   secrets it asks for:

   | Variable                     | Value                                   |
   | ---------------------------- | --------------------------------------- |
   | `SPRING_DATASOURCE_URL`      | The Neon JDBC URL                       |
   | `SPRING_DATASOURCE_USERNAME` | Neon role                               |
   | `SPRING_DATASOURCE_PASSWORD` | Neon password                           |
   | `SPRING_MAIL_USERNAME`       | Brevo SMTP login                        |
   | `SPRING_MAIL_PASSWORD`       | Brevo SMTP key                          |
   | `GROUPLOOM_MAIL_FROM`        | The sender address verified in Brevo    |

   `GROUPLOOM_JWT_SECRET` is generated by Render, and `GROUPLOOM_DEMO_ENABLED`
   is on, so the first start seeds the [demo group](#demo). If Render gives either
   service a URL other than `grouploom.onrender.com` /
   `grouploom-api.onrender.com`, update `GROUPLOOM_PUBLIC_BASE_URL` and the
   `/api/*` rewrite in `render.yaml`.

Free-tier caveats: the backend sleeps after about 15 minutes idle, so the
first request takes 30–60 s while it starts, and queued email is only sent
while it's awake. Render's free tier blocks SMTP ports 25, 465 and 587,
which is why the `prod` profile uses Brevo's port 2525.

## Development

### Backend

```bash
cd backend
./gradlew build    # compile and run all tests
./gradlew test     # tests only (Testcontainers, needs Docker)
```

Database schema changes go in new Flyway migrations under
`backend/src/main/resources/db/migration` (`V<n>__description.sql`).
Committed migrations are never edited.

### Frontend

```bash
cd frontend
pnpm lint && pnpm type-check && pnpm test:unit --run   # checks to run before committing
pnpm test:e2e      # Playwright end-to-end tests
pnpm build         # production build
```

### API contract

The backend's OpenAPI spec is the contract between the two halves. The
frontend's TypeScript types in `frontend/src/api/schema.d.ts` are generated
from it. Don't edit that file by hand. After changing an endpoint, with the
backend running:

```bash
cd frontend && pnpm gen:api
```

Then fix any type errors in the frontend.

### Conventions

- Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/),
  e.g. `feat(member): add preference endpoint`.
- Detailed backend and frontend conventions are in
  [`backend/CLAUDE.md`](backend/CLAUDE.md) and
  [`frontend/CLAUDE.md`](frontend/CLAUDE.md).

## License

GroupLoom is released under the [MIT License](LICENSE.txt).
