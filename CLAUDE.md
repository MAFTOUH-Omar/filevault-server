# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Spring Boot 4.1.1 / Java 27 backend for FileVault, a file storage service with JWT auth, role-based
permissions, and per-user storage quotas. PostgreSQL + Liquibase for schema management. Auth
(register/login/refresh/logout) and role/permission management (CRUD + assigning roles to users) are
implemented; file upload/storage endpoints are not yet.

## Commands

Build/run via the Maven wrapper (do not rely on a globally installed `mvn`):

```
./mvnw clean install       # build
./mvnw spring-boot:run     # run the app (port 8080)
./mvnw test                # run all tests
./mvnw test -Dtest=FilevaultServerApplicationTests          # run a single test class
./mvnw test -Dtest=FilevaultServerApplicationTests#methodName  # run a single test method
```

Liquibase changelog can also be driven directly via the Maven plugin (reads DB URL/credentials from
the `liquibase-maven-plugin` config in `pom.xml`, which expects `env.DB_USER`/`env.DB_PASSWORD`):

```
./mvnw liquibase:update
./mvnw liquibase:rollback -Dliquibase.rollbackCount=1
```

On Windows use `mvnw.cmd` instead of `./mvnw` from `cmd.exe`/PowerShell if the wrapper script isn't resolved.

## Configuration

- `src/main/resources/application.yaml` is the single config file; all secrets/environment-specific
  values are pulled from env vars with `${VAR:default}` placeholders, loaded from a local `.env` file
  (`spring.config.import: optional:file:.env[.properties]`) via `spring-boot-starter-liquibase`'s dotenv
  support. Required vars: `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `LINK_SECRET`. Optional: `DB_URL`,
  `MAX_FILE_SIZE`, `COOKIE_SECURE`, `CORS_ORIGINS`, `STORAGE_ROOT`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`.
- `spring.jpa.hibernate.ddl-auto` is `validate` — Hibernate never modifies the schema. All schema
  changes must go through a new Liquibase changeset; do not rely on JPA auto-DDL.
- `app.*` keys in `application.yaml` define JWT issuer/TTL, refresh-token cookie settings, signed-link
  TTL for `LINK_SECRET`, default signup role, local file storage root, and CORS origins — read these
  before implementing auth/link/storage features rather than inventing new config keys.

## Naming conventions

- **Tables and columns**: every table has a short domain prefix, and every one of its own columns
  repeats that prefix (`usr_users` → `usr_id`, `usr_email`, `usr_password_hash`, ...). Current
  prefixes: `usr` (users), `rol` (roles), `prm` (permissions), `tok` (refresh tokens), `fil` (stored
  files). A table's primary key is always `<its own prefix>_id`; a foreign key column reuses the
  *referenced* table's prefix (e.g. `fil_stored_files.usr_id` references `usr_users.usr_id`), so the
  same column name always means the same thing across tables. Pure join tables are named by
  concatenating both prefixes (`rol_prm` for role↔permission, `usr_rol` for user↔role) and hold only
  the two FK columns. Apply this to every new table/column — do not add a bare `id`, `name`, etc.
  without its table's prefix.
- **Units in names**: any field/variable holding a measured quantity gets a unit suffix (`_bytes`,
  `_cm`, `_km`, `_unix`, etc.), e.g. `rol_storage_quota_bytes`, `fil_size_bytes`. This applies to
  Liquibase columns, JPA entity fields, and plain Java variables alike — never a bare `size` or
  `taille` when a unit is known.

## Database schema (Liquibase)

Changelog root is `src/main/resources/db/changelog/db.changelog-master.yaml`, which does
`includeAll: db/changelog/migrations/`. New changesets go in that `migrations/` directory, named
`YYYY_MM_DD_HHMMSS_description.yaml` so they sort and apply in order — append new files rather than
editing existing ones (the existing changesets below were edited in place only because the project
has never been deployed/committed yet; once a changeset has run anywhere, add a new one instead of
editing it). Current schema (see naming convention above for the prefix scheme):

- `rol_roles` / `prm_permissions` / `rol_prm` — RBAC. A role has an optional
  `rol_storage_quota_bytes` (`NULL` = unlimited).
- `usr_users` — UUID PK (`usr_id`), `usr_email`/`usr_password_hash`, `usr_storage_used_bytes` counter
  maintained alongside uploads.
- `usr_rol` — many-to-many between users and roles.
- `tok_refresh_tokens` — UUID PK, stores a hash of the refresh token (`tok_token_hash`, not the raw
  token), FK `usr_id` to `usr_users` with cascade delete.
- `fil_stored_files` — UUID PK, FK `usr_id` to the owning user, `fil_storage_key` is the unique
  on-disk/physical key (distinct from `fil_original_name`), indexed by `(usr_id, fil_created_at)` for
  listing.

**Indexing rule**: every FK column must be queryable fast in both directions. Postgres only indexes
the *leading* column of a composite PK/unique constraint for free, so:
- A FK that is the leading column of its table's PK (e.g. `rol_prm.rol_id`, `usr_rol.usr_id`,
  `tok_refresh_tokens`/`fil_stored_files`'s own composite indexes) is already covered — don't add a
  redundant single-column index on it.
- A FK that is a *trailing* PK column, or not part of any PK/unique constraint at all, needs its own
  explicit `createIndex` (e.g. `idx_rol_prm_prm_id`, `idx_usr_rol_rol_id`, `idx_tok_refresh_tokens_usr_id`).
- Columns scanned by background/cleanup queries (not just point lookups) get an index too, e.g.
  `idx_tok_refresh_tokens_expires_at` for sweeping expired refresh tokens.
- Name every index `idx_<table>_<column(s)>`; unique constraints already create their own index, so
  don't duplicate those with a second `createIndex`.

Seed data (`2026_10_02_100400_seed_roles_and_permissions.yaml`) defines the default roles: `member`
(1 GiB quota, read/upload/delete own files), `premium` (10 GiB quota, same permissions), `full-access`
(unlimited quota, all permissions). `app.signup.default-role` controls which role new signups get.

## Auth & RBAC architecture

Code is organized by feature, not by layer: `auth/` (register/login/refresh/logout, `RefreshToken`
entity, `AdminBootstrapRunner`), `role/` (CRUD + assign, `Role` entity), `permission/` (`Permission`
entity, catalog only — no controller, permissions are only ever granted to roles via seed migrations
or future role-update endpoints), `user/` (`User` entity), `security/` (JWT encode/decode, Spring
Security config, typed `@ConfigurationProperties` records for every `app.*` config block).

- **Stateless JWT, self-issued**: the app is its own OAuth2 resource server. `SecurityConfig` builds
  a `JwtEncoder`/`JwtDecoder` from the same HMAC secret (`app.jwt.secret`, HS256) — there's no
  external IdP. A user's roles and permissions are flattened into the access token's `authorities`
  claim at login/register/refresh time (see `AuthorityMapper`: `ROLE_<NAME>` per role +
  the permission name per permission), so **authorization never re-queries the DB per request** —
  `JwtAuthenticationConverter` reads authorities straight from the token. This means a role/permission
  change only takes effect for a user's *next* token (next login or refresh) — there is no live
  revocation of already-issued access tokens short of waiting out `app.jwt.access-token-ttl` (15m).
- **Refresh tokens are opaque, not JWTs**, stored hashed (SHA-256) in `tok_refresh_tokens`, delivered
  only via an `HttpOnly` cookie (`app.refresh-token.cookie-name`, scoped to `/auth`). `/auth/refresh`
  rotates them (old one revoked, new one issued) rather than reusing the same token.
- **Permission names are the actual Spring Security authorities** (e.g. `roles:create`), checked via
  `@PreAuthorize("hasAuthority('roles:create')")` on controller methods — not `hasRole(...)`, since
  the granular `roles:*`/`users:*`/`files:*` permissions (seeded in
  `db/changelog/migrations/2026_10_02_100400_seed_roles_and_permissions.yaml` and
  `2026_10_09_090000_seed_role_management_permissions.yaml`) are the unit of authorization, not role
  names. `ROLE_<NAME>` authorities exist too (for any future `hasRole(...)` use) but nothing currently
  checks them.
- **`admin` vs `full-access` roles**: both exist and are seeded with every management permission;
  `admin` is the intended role for platform operators, `full-access` is intended as an unlimited-quota
  storage tier for end users. Keep granting new management permissions to both in seed data unless a
  feature specifically wants to split them apart.
- **Bootstrap admin**: `AdminBootstrapRunner` (an `ApplicationRunner`) creates one admin user from
  `app.bootstrap.admin-email`/`admin-password` on startup if neither is blank and no user with that
  email exists yet. This is the only way an admin account gets created from plain config — there is
  no seeded admin user/password in Liquibase (passwords need BCrypt hashing at runtime, not in SQL).
- **DTOs only at the boundary**: controllers never accept or return entities directly (`role/dto/`,
  `auth/dto/`) per the project's Spring Boot conventions skill.

## API documentation (OpenAPI / Scalar)

`springdoc-openapi-starter-webmvc-scalar` (pom.xml, pinned via `springdoc-openapi.version`) generates
the OpenAPI 3 spec from controllers automatically and serves it through Scalar's UI — no hand-written
API docs or Swagger annotations required for a controller to show up. Once controllers exist:
- Raw OpenAPI JSON: `GET /v3/api-docs`
- Scalar UI (browsable reference): `GET /scalar`
Config lives in `application.yaml` under `springdoc.*`/`scalar.*`. Swagger UI itself is intentionally
not on the classpath — Scalar is the only UI, reading the same generated spec, so there is nothing to
keep in sync by hand. Add `@Operation`/`@Schema` annotations only to enrich descriptions; they are
never required for an endpoint to appear.

## Skills to use automatically in this repo

These skills are vendored **inside the repo** at `.claude/skills/<name>/SKILL.md` (committed, real
files — not symlinks, so they work on a fresh clone regardless of OS/symlink support) via
`npx skills add <pkg> -a claude-code --copy`. Anyone who clones the repo already has them; Claude
Code should trigger them proactively based on what the work actually touches — do not wait for the
user to name a skill or ask for it explicitly:

- `java-springboot` (from `github/awesome-copilot`) — Spring Boot conventions/best practices. Use
  whenever writing or reviewing Java/Spring code in `src/main/java` (controllers, services, config,
  security).
- `postgresql-optimization` (from `github/awesome-copilot`) — PostgreSQL indexing/query/schema best
  practices. Use whenever touching anything under `src/main/resources/db/changelog/` or writing JPA
  queries/repositories.

If a task clearly falls in one of these domains, load the matching skill as part of normal work,
the same way an explicit `/skill-name` invocation would, rather than only using it when asked. When
adding another project-wide skill, install it the same way (`-a claude-code --copy`) so it stays
committed and OS-portable, then add it to this list.

## Security note

`.env` holds real secrets (DB password, JWT/link signing keys) and must never be committed. It's
excluded via `.gitignore` (`.env`, `.env.*`, with `.env.example` explicitly un-ignored as the
onboarding template with placeholder values). Before any commit, check `git status`/`git diff` to
make sure `.env` itself hasn't been re-added.
