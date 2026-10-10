# CollabNotes — Backend

Real-time collaborative note-taking. Spring Boot 3.3 + STOMP/WebSocket + JPA.
Frontend lives in the sibling `collabnotes-frontend` repo and ships to Vercel.

## Run locally

```bash
mvn spring-boot:run
# API on http://localhost:8080
# H2 console at /h2-console (jdbc:h2:mem:collabnotes, user sa, blank password)
```

## Build JAR

```bash
mvn -DskipTests package
java -jar target/collabnotes.jar
```

## Smoke test (against a running local server; needs `brew install jq`)

```bash
./scripts/smoke.sh
```

## Configuration (env vars)

| Var | Default | Purpose |
|---|---|---|
| `PORT` | 8080 | listening port (Render sets this automatically) |
| `APP_JWT_SECRET` | dev-only string | HS256 signing key — **must override in prod** |
| `APP_JWT_EXPIRATION_MS` | 86400000 | token lifetime |
| `APP_CORS_ALLOWED_ORIGINS` | `*` | comma-separated list of frontend origins (Vercel URL etc.) |

## Deploy to Render

The included `render.yaml` describes a free-tier Web Service:

```bash
# After pushing to GitHub, in Render dashboard: New → Blueprint → pick the repo.
# It will pick up APP_JWT_SECRET as a generated secret and APP_CORS_ALLOWED_ORIGINS from the YAML.
# Override APP_CORS_ALLOWED_ORIGINS with your real Vercel domain once it's deployed.
```

## REST API quick reference

| Method | Endpoint | Role |
|---|---|---|
| `POST` | `/api/auth/register` & `/login` | — |
| `GET` | `/api/workspaces` | member |
| `POST` | `/api/workspaces` | — |
| `GET` | `/api/workspaces/{id}/members` | member |
| `POST` | `/api/workspaces/{id}/invite` | OWNER |
| `PATCH` | `/api/workspaces/{id}/members/{userId}` | OWNER |
| `DELETE` | `/api/workspaces/{id}/members/{userId}` | OWNER |
| `GET` | `/api/workspaces/{id}/search?q=&limit=` | member |
| `GET` | `/api/workspaces/search?q=&limit=` | auth |
| `POST` | `/api/workspaces/{id}/pages` | EDITOR+ |
| `GET` | `/api/workspaces/{id}/pages` | member |
| `GET` | `/api/pages/{id}` | member |
| `PUT` | `/api/pages/{id}` | EDITOR+ |
| `DELETE` | `/api/pages/{id}` | EDITOR+ |
| `GET` | `/api/pages/{id}/versions` | member |
| `GET` | `/api/pages/{id}/versions/{n}` | member |
| `POST` | `/api/pages/{id}/versions/{n}/restore` | EDITOR+ |

## WebSocket

Endpoint `ws(s)://<host>/ws?token=<JWT>` via SockJS. Subscribe `/topic/page/{pageId}`; send `/app/page/{pageId}/edit` with `{type, content?, cursorPos?, blocks?}`. Events: `CONTENT_UPDATE`, `BLOCKS_UPDATE`, `CURSOR_MOVE`, `PRESENCE_JOIN`, `PRESENCE_LEAVE`, `TYPING`. STOMP edits go through the same workspace-membership guard as REST — VIEWER edits are dropped silently.
