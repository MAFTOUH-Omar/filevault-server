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
  `MAX_FILE_SIZE`, `COOKIE_SECURE`, `CORS_ORIGINS`, `STORAGE_ROOT`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`,
  `REDIS_HOST` (default `localhost`), `REDIS_PORT` (default `6379`), `REDIS_PASSWORD` (default empty).
- `app.rate-limit.global.{limit,window}` and `app.rate-limit.auth.{limit,window}` configure the two
  Redis-backed rate limits (see "Rate limiting & abuse protection" below) — change these, not the
  filter code, to retune thresholds.
- `spring.jpa.hibernate.ddl-auto` is `validate` — Hibernate never modifies the schema. All schema
  changes must go through a new Liquibase changeset; do not rely on JPA auto-DDL.
- `app.*` keys in `application.yaml` define JWT issuer/TTL, refresh-token cookie settings, signed-link
  TTL for `LINK_SECRET`, default signup role, local file storage root, and CORS origins — read these
  before implementing auth/link/storage features rather than inventing new config keys.

## Naming conventions

- **Tables and columns**: every table has a short domain prefix, and every one of its own columns
  repeats that prefix (`usr_users` → `usr_id`, `usr_email`, `usr_password_hash`, ...). Current
  prefixes: `usr` (users), `rol` (roles), `prm` (permissions), `tok` (refresh tokens), `fil` (stored
  files), `sec` (security/abuse-prevention, e.g. `sec_blacklisted_ips`). A table's primary key is always `<its own prefix>_id`; a foreign key column reuses the
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
- `sec_blacklisted_ips` — bigint PK, unique `sec_ip_address` (varchar(45), fits IPv4 and IPv6),
  optional `sec_reason`. Source of truth for `IpBlacklistFilter`; administer it directly (no CRUD
  endpoint exists yet — add one under `roles:manage`-equivalent protection if/when needed).

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

Code is organized **by layer** (not by feature — this is a deliberate departure from the generic
Spring Boot convention, to keep request/model/business-logic/authorization concerns visually
separate):

- `request/<domain>/` — input DTOs (`request/auth/RegisterRequest`, `request/role/CreateRoleRequest`, ...).
- `response/<domain>/` — output DTOs. `RoleResponse.from(Role)` is a static factory the entity-facing
  side builds itself; controllers never return entities directly.
- `models/` — JPA entities (`User`, `Role`, `Permission`, `RefreshToken`), flat, no sub-packages.
- `controller/` — `@RestController`s. Thin: validate via `@Valid`, delegate to one Action, map the
  result to a `ResponseEntity`. No business logic here.
- `policy/` — authorization decisions that don't fit a declarative `@PreAuthorize` string (currently
  just `RolePolicy`, see below). A policy throws its own exception on denial; it doesn't return a
  boolean for the caller to check.
- `action/<domain>/` — one class per use case/write operation (`CreateRoleAction`, `LoginAction`, ...),
  each with a single `execute(...)` method. This replaces a monolithic `*Service` — if a use case
  needs to reuse logic (token issuance, hashing), that shared piece is its own small
  package-private class in the same `action/<domain>/` package (`TokenIssuer`, `TokenHasher`), not a
  public service everything depends on.
- `repository/<domain>/` — Spring Data JPA repositories, one subpackage per domain
  (`repository/role/`, `repository/auth/`, `repository/user/`, `repository/security/`). Each one is
  also where "find-or-404" lives as a `default` interface method (`RoleRepository.getOrThrow(id)`,
  `UserRepository.getOrThrow(id)`) so actions don't repeat the lookup-or-throw boilerplate.
- `exception/<domain>/` — one subpackage per domain (`exception/role/`, `exception/auth/`,
  `exception/user/`), one class per domain error, no logic beyond a message. A cross-cutting concern
  that isn't tied to one domain (there currently is none) would get its own `exception/<name>/`
  rather than being forced into role/auth/user.
- `middleware/` — two different mechanisms, both "turn something into an HTTP response":
  `GlobalExceptionHandler` (`@RestControllerAdvice`) + its `ErrorResponse` DTO is the single place
  every exception *thrown inside a controller* becomes an HTTP status + body. The perimeter filters
  (`PathTraversalFilter`, `IpBlacklistFilter`, `GlobalRateLimitFilter`, `AuthRateLimitFilter`) are
  plain servlet `Filter`s that run *before* DispatcherServlet, so they can't rely on
  `@RestControllerAdvice` — they write the same `ErrorResponse` shape themselves via the shared
  `ErrorResponseWriter`. See "Rate limiting & abuse protection" below.
- `security/` — infrastructure that doesn't belong to any one domain: JWT encode/decode
  (`JwtService`), `SecurityConfig`, `AuthorityMapper`, `RedisRateLimiter`, and a typed
  `@ConfigurationProperties` record per `app.*` config block. Not part of the layering above on
  purpose — it's cross-cutting.
- `config/` — `@Configuration` classes that only wire beans together, no business logic:
  `FilterConfig` (registers the `middleware/` filters with explicit order/URL patterns) and
  `OpenApiConfig` (API title + the `bearerAuth` security scheme).

When adding a new use case: DTO(s) in `request`/`response`, the write/read logic as a new class in
`action/<domain>/`, a policy check in `policy/` only if authorization needs more than
`hasAuthority(...)`, wire it into the relevant `controller/`. Don't add a `*Service` facade over the
actions — the controller calling 2–7 single-method Action beans directly is the intended shape here.

### Stateless JWT, self-issued
The app is its own OAuth2 resource server. `SecurityConfig` builds a `JwtEncoder`/`JwtDecoder` from
the same HMAC secret (`app.jwt.secret`, HS256) — there's no external IdP. A user's roles and
permissions are flattened into the access token's `authorities` claim at login/register/refresh time
(`AuthorityMapper`: `ROLE_<NAME>` per role + the permission name per permission), so **authorization
never re-queries the DB per request** — `JwtAuthenticationConverter` reads authorities straight from
the token. A role/permission change only takes effect for a user's *next* token (next login or
refresh) — there is no live revocation of already-issued access tokens short of waiting out
`app.jwt.access-token-ttl` (15m).

Refresh tokens are opaque, not JWTs, stored hashed (SHA-256) in `tok_refresh_tokens`, delivered only
via an `HttpOnly` cookie (`app.refresh-token.cookie-name`, scoped to `/auth`). `/auth/refresh` rotates
them (old one revoked, new one issued) rather than reusing the same token.

### Permissions, and the dynamic "roles:give:&lt;name&gt;" policy
Permission names are the actual Spring Security authorities (e.g. `roles:create`), checked via
`@PreAuthorize("hasAuthority('roles:create')")` on controller methods — not `hasRole(...)`. `ROLE_<NAME>`
authorities exist too but nothing currently checks them.

Assigning a role to a user is a two-layer check, not one:
1. **Coarse gate, on the controller**: `@PreAuthorize("hasAuthority('roles:assign')")` — can this
   caller reach the assign/unassign endpoints at all.
2. **Fine-grained gate, in `RolePolicy.checkCanGiveRole(role)`**, called from inside
   `AssignRoleAction`/`UnassignRoleAction`: does this caller specifically hold
   `roles:give:<that role's name>`.

Without step 2, anyone holding the broad `roles:assign` permission could hand out *any* role,
including `admin` — privilege escalation. `roles:give:<name>` exists per-role so "can assign roles in
general" and "can grant the admin role specifically" are different, separately-grantable things.

This permission is **created dynamically, not just seeded**: `CreateRoleAction` creates
`roles:give:<name>` the moment a role is created, and grants it to every role that currently holds
`roles:manage` (the platform-operator catch-all) — so admins automatically gain the ability to assign
a brand-new role without a manual migration step, while anyone with only a narrower permission set
stays unable to grant it until explicitly authorized. `UpdateRoleAction` renames the permission when
the role is renamed; `DeleteRoleAction` deletes it when the role is deleted. The initial backfill for
the 4 roles that existed before this mechanism was added lives in
`db/changelog/migrations/2026_10_09_100000_seed_dynamic_role_grant_permissions.yaml` — any role
created *after* that migration gets its `roles:give:<name>` purely from `CreateRoleAction`, never from
a migration.

Self-registration (`RegisterAction`) always assigns the single configured default role
(`app.signup.default-role`) — there is no caller-supplied role on that path, so it isn't a
privilege-escalation surface and doesn't go through `RolePolicy`.

### User-facing identity: register/login/refresh/me all return the same shape
`response/user/UserSummaryResponse` (id, email, fullName, roles, permissions — plain names, not JWT
`ROLE_`-prefixed authorities) is built from a `User` entity via `UserSummaryResponse.from(user)`, using
`AuthorityMapper.roleNames`/`permissionNames`. `AuthResponse` embeds one (`register`/`login`/`refresh`
all return it, so the client always knows what the new token can do without decoding the JWT).
`GET /auth/me` returns the same shape, freshly read from the DB (not from the JWT claims) — it's the
one place that intentionally re-queries per request, since its entire purpose is "what does the DB say
right now", unlike authorization checks which must stay token-only. `/auth/me` requires a valid access
token (`SecurityConfig` only `permitAll`s `/auth/register`, `/auth/login`, `/auth/refresh`,
`/auth/logout` — everything else, including `/auth/me`, needs `Authorization: Bearer <token>`).

### Roles and bootstrap
`admin` and `full-access` both exist and are seeded with every management permission (including every
`roles:give:*`); `admin` is the intended role for platform operators, `full-access` is intended as an
unlimited-quota storage tier for end users. Keep granting new management permissions to both in seed
data unless a feature specifically wants to split them apart.

`AdminBootstrapRunner` (an `ApplicationRunner`, in `action/auth/`) creates one admin user from
`app.bootstrap.admin-email`/`admin-password` on startup if neither is blank and no user with that
email exists yet. This is the only way an admin account gets created from plain config — there is no
seeded admin user/password in Liquibase (passwords need BCrypt hashing at runtime, not in SQL).

## Rate limiting & abuse protection

Four servlet filters run **before Spring Security**, registered explicitly (not as `@Component`s —
see `config/FilterConfig`) in this order, cheapest/broadest rejection first:

1. `PathTraversalFilter` — percent-decodes the URI and query string (up to 3 passes, to also catch
   double/triple-encoded and mixed-encoding payloads like `..%2f` or `%252e%252e%252f`) and rejects
   any result containing `../`, `..\`, or a null byte. Stateless, no DB/Redis. Note: a *literal*
   `../` in the raw URI path never reaches this filter in practice — Tomcat normalizes it away before
   dispatch — so this filter's real job is the encoded variants that normalization doesn't touch.
2. `IpBlacklistFilter` — 403s any request whose `remoteAddr` exists in `sec_blacklisted_ips`. **DB,
   not Redis**, by design: it's small, changes rarely, and is administered data that should survive a
   cache flush — a plain indexed lookup is fast enough and there's no need to introduce a
   cache-invalidation problem for this. (Note: uses `request.getRemoteAddr()` directly, i.e. it does
   *not* trust `X-Forwarded-For` — if this app ever sits behind a reverse proxy, that needs explicit
   trusted-proxy configuration, otherwise the header is spoofable and this filter becomes bypassable.)
3. `GlobalRateLimitFilter` — a broad per-IP budget (`app.rate-limit.global`, default 100/min) across
   *all* routes, a coarse anti-DoS backstop.
4. `AuthRateLimitFilter` — scoped (via its `FilterRegistrationBean` URL patterns) to only
   `/auth/register` and `/auth/login`, with a tighter budget (`app.rate-limit.auth`, default 10/min
   per IP *per endpoint*) to blunt credential-stuffing/spam-registration specifically. A caller must
   pass both this and the global filter.

Both rate-limit filters share `security/RedisRateLimiter`: a fixed-window counter (`INCR` the key, set
its TTL only the first time it's created). This is simple and cheap, at the cost of allowing a short
burst right at a window boundary compared to a sliding window/token bucket — an accepted trade-off for
abuse protection, not precise metering. If that boundary burst ever becomes a real problem, replace the
counter logic in `RedisRateLimiter`, not the filters that call it.

All four filters write `middleware.ErrorResponse` JSON directly via `middleware/ErrorResponseWriter`
(a shared `ObjectMapper`-backed helper) instead of throwing — a `Filter` runs outside
`DispatcherServlet`, so an exception thrown here would **not** be caught by `GlobalExceptionHandler`.
Keep that pattern for any new perimeter filter.

## API documentation (OpenAPI / Scalar)

`springdoc-openapi-starter-webmvc-scalar` (pom.xml, pinned via `springdoc-openapi.version`) generates
the OpenAPI 3 spec from controllers automatically and serves it through Scalar's UI — a controller
doesn't need any annotation to *appear* in the docs. Once controllers exist:
- Raw OpenAPI JSON: `GET /v3/api-docs`
- Scalar UI (browsable reference): `GET /scalar`
Config lives in `application.yaml` under `springdoc.*`/`scalar.*`. Swagger UI itself is intentionally
not on the classpath — Scalar is the only UI, reading the same generated spec, so there is nothing to
keep in sync by hand.

That said, appearing isn't the same as being *documented*, and both of the following are required —
not optional polish — for every controller/endpoint in this project:

- **`@Tag(name = "...")` on every controller.** Without it, springdoc derives the sidebar group name
  from the Java class name (`role-controller`, `auth-controller`), which is an implementation detail
  leaking into client-facing docs. Use a clean, plural, capitalized noun (`"Roles"`, `"Auth"`) instead
  — see `RoleController`/`AuthController` for the pattern.
- **`@ApiResponses` listing every status code the endpoint can actually return, including error
  cases** — not just the 200/201/204 happy path. At minimum: validation failures (400), auth failures
  (401/403, naming the specific permission/policy involved), not-found (404), conflicts (409), and
  rate limiting (429, already declared once per controller via a class-level `@ApiResponses` — only
  add endpoint-specific codes at the method level). Reference `middleware.ErrorResponse` via
  `@Content(schema = @Schema(implementation = ErrorResponse.class))` for every non-2xx response so
  Scalar shows the actual error body shape, not just a bare status code. `@SecurityRequirement(name =
  "bearerAuth")` (scheme defined once in `config/OpenApiConfig`) goes on every endpoint that requires
  a token, so Scalar's "Authorize" flow applies to it.

When adding a new endpoint, write the `@ApiResponses` block by asking "what can `GlobalExceptionHandler`
or a `RolePolicy`/filter actually turn this into" — don't just document the success case and call it done.

### Responses are always in English

Every HTTP response — success bodies, `ErrorResponse` messages, Bean Validation messages — is in
English, regardless of the client's `Accept-Language` header. This is enforced by
`spring.mvc.locale: en` + `spring.mvc.locale-resolver: fixed` in `application.yaml` (a `FixedLocaleResolver`
ignores `Accept-Language` entirely, which also fixes Bean Validation's default messages, since those are
resolved via `LocaleContextHolder`). Consequences for anyone adding code:
- Never write a French (or any non-English) string into an exception message, `prm_description` seed
  value, `@Operation`/`@ApiResponse` description, or any other client-visible text — including inside
  a *new* Liquibase migration. If an existing migration's text needs correcting, add a new migration
  with `UPDATE` statements (see `2026_10_09_130000_translate_permission_descriptions_to_english.yaml`
  for the pattern) rather than editing the already-applied one in place.
- Comments in code/YAML, commit messages, and this file are not "responses" and may stay in whatever
  language — this rule is about what a client of the API receives.

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
