# Email Writer SB — Implementation Plan

Goal: make the codebase match the resume/README claims. The repo currently ships
a working Spring Boot + Gemini backend with Bucket4j rate limiting. The missing or
misleading pieces are: Redis caching, token/cost tracking, the Chrome extension,
and true non-blocking I/O.

This plan continues the existing phase numbering (Phase 0 = baseline, Phase 1 =
rate limiting, both already merged).

---

## Current state (verified against code, commit `6b9a291`)

| Claim | Status |
|-------|--------|
| Spring Boot backend + Gemini integration | ✅ Done |
| Layered Controller → Service | ✅ Done (no separate Client layer) |
| Bucket4j per-IP rate limiting | ✅ Done |
| WebClient for Gemini calls | ⚠️ Uses `.block()` on servlet stack — effectively blocking |
| Redis + Spring Cache response caching | ❌ `CacheConfig` is a disabled `TODO(Phase 2)` stub |
| Token usage tracking + cost estimation | ❌ Not implemented |
| Chrome extension (manifest, content.js) | ✅ Built — lives in separate folder `E:/email-generator-ext` (MV3, MutationObserver, button injection, fetch, insert). Phase 4.1 fixes applied: loop bugs fixed + tone selector added. |

---

## Phase 2 — Token usage tracking & cost estimation

**Why:** Smallest, self-contained, high-signal resume item. Gemini already returns
`usageMetadata` in every response; we just aren't reading it.

**Changes**
- `EmailGeneratorService.extractResponseContent()` currently parses only
  `candidates[0].content.parts[0].text`. Extend parsing (Jackson) to also read:
  - `usageMetadata.promptTokenCount`
  - `usageMetadata.candidatesTokenCount`
  - `usageMetadata.totalTokenCount`
- Add a small `TokenUsage` model (record) + a `CostEstimator` helper that multiplies
  token counts by configurable per-1K rates (`gemini.cost.input-per-1k`,
  `gemini.cost.output-per-1k` in `application.properties`).
- Log per-request usage and running totals (start with `Logger`, not `System.out`).
- Optional: expose `GET /api/email/usage` returning cumulative tokens + estimated cost.

**Acceptance**
- A generate call logs prompt/completion/total tokens and an estimated USD cost.
- Unit test feeds a canned Gemini JSON body and asserts parsed token counts.

---

## Phase 3 — Redis + Spring Cache response caching

**Why:** Identical (emailContent + tone) requests should not re-hit Gemini. This is
the `CacheConfig` `TODO(Phase 2)` made real.

**Decisions**
- Cache key = hash of `(emailContent, tone)`.
- Backend = Redis (matches resume). Provide a local fallback / docker-compose Redis
  so the app still runs without a cloud Redis.

**Changes**
- `pom.xml`: add `spring-boot-starter-data-redis` and `spring-boot-starter-cache`.
- `application.properties`: `spring.data.redis.host/port`, `spring.cache.type=redis`,
  a TTL (e.g. 24h) via `RedisCacheConfiguration`.
- `CacheConfig`: uncomment/enable `@EnableCaching`, define a `RedisCacheManager` bean
  with TTL + JSON serialization.
- Annotate `generateEmailReply` (or a thin cached method) with
  `@Cacheable(value="emailReplies", key="#emailRequest.hashCode()")`.
  - Ensure `EmailRequest` has stable `equals`/`hashCode` (Lombok `@Data` provides it).
- Note interaction with Phase 2: cache hits consume **zero** Gemini tokens — usage
  logging should reflect cache hit vs miss.
- Add `docker-compose.yml` with a Redis service for local dev.

**Acceptance**
- Same request twice → second call returns from Redis, no Gemini HTTP call, sub-ms.
- `redis-cli KEYS emailReplies*` shows the cached entry; entry expires after TTL.

---

## Phase 4 — Chrome Extension (Gmail integration) — ✅ BUILT (needs fixes)

**Status:** Already implemented in a separate folder `E:/email-generator-ext`
(not committed to the backend repo). It has MV3 `manifest.json`, `content.js`
(MutationObserver compose detection, "AI Reply" button injection, fetch to
`http://localhost:8081/api/email/generate`, insert via `execCommand`), and `content.css`.

**Bugs found — FIXED in Phase 4.1:**
- ✅ `getEmailContent()` — `return ''` was **inside** the `for` loop, so only the first
  selector (`.h7`) was ever checked. Moved `return '';` after the loop so all
  selectors are tried.
- ✅ `findComposeToolbar()` — same bug: `return null` was inside the loop. Moved it
  after the loop.
- ✅ Tone was hard-coded to `"professional"`. Added a `createToneSelector()` dropdown
  (Professional/Casual/Friendly/Formal/Concise/Sarcastic) injected into the toolbar;
  its value is sent in the fetch body. Backend accepts arbitrary tone strings, so no
  backend change needed. Styled in `content.css` (was empty).

**Remaining tech debt (not blocking):**
- `execCommand('insertText', ...)` is deprecated; acceptable for now.

**Optional enhancements:**
- `popup.html` + `popup.js`: backend URL config + default tone.
- Tighten backend `@CrossOrigin(origins = "*")` to the extension origin once stable.

**Decision needed:** keep the extension as its own repo, or vendor it into the backend
repo under `extension/` (or add it as a git submodule) so the README's structure is real.

**Acceptance**
- Load unpacked extension → open Gmail → Reply → "AI Reply" button appears →
  click → generated reply populates the compose box.
- Tone selector changes the generated reply's tone.

---

## Phase 5 — (Stretch) Honest non-blocking I/O + Client layer

**Why:** Resume says "non-blocking via WebClient," but the code calls `.block()` on the
servlet stack. Either make it true or soften the wording.

**Option A — make it real**
- Introduce a dedicated `GeminiClient` class (the missing "Client" layer) wrapping
  WebClient and returning `Mono<String>`.
- Make controller return `Mono<ResponseEntity<String>>`; remove `.block()`.
- Caveat: rate-limit interceptor is servlet `HandlerInterceptor`; going fully reactive
  means moving to WebFlux `WebFilter`. Scope this carefully.

**Option B — soften the claim**
- Keep blocking call; update resume/README to "WebClient-based integration" without the
  "non-blocking performance" claim.

**Decision:** default to Option B unless we want the reactive refactor; revisit after
Phases 2–4.

---

## Suggested order & branching

1. Phase 2 (token tracking) — branch `phase-2-token-tracking`
2. Phase 3 (Redis cache) — branch `phase-3-redis-cache`
3. Phase 4.1 (extension bug fixes + tone selector + decide repo home) — branch `phase-4-extension-polish`
4. Phase 5 (non-blocking / wording) — branch `phase-5-nonblocking`

(Phase 4 core is already built in `E:/email-generator-ext`; only the polish pass remains.)

Each phase: implement → build with `./mvnw test` → update README section → commit with
`Phase N: ...` message (matching existing history) → merge to `main`.

## Cross-cutting cleanups (fold in as we go)
- Replace `System.out.println` in `EmailGeneratorService` with a real logger.
- Externalize the Gemini URL/key validation (fail fast if env vars missing).
- Add real unit/integration tests (current test is the default context-load stub).
- Keep README claims in lockstep with what each phase actually ships.
