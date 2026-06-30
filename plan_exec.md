# Phase 2 — Execution Notes (Token Usage Tracking & Cost Estimation)

Technical, file-level notes for implementing Phase 2. Keep `plan.md` for the
high-level roadmap; this file is the build sheet.

## Hard constraints

- **Do NOT change the `/api/email/generate` response shape.** It returns `String`
  (plain text reply). The Chrome extension (`E:/email-generator-ext/content.js`)
  does `await response.text()` and inserts it directly. Returning JSON here breaks
  the extension. Usage is tracked server-side + exposed on a *separate* endpoint.
- Backend is the servlet stack (`spring-boot-starter-web`) with a blocking
  `.block()` Gemini call. Phase 2 does not touch that; stays synchronous.

## Gemini response shape (what we parse)

```json
{
  "candidates": [ { "content": { "parts": [ { "text": "<reply>" } ] } } ],
  "usageMetadata": {
    "promptTokenCount": 12,
    "candidatesTokenCount": 34,
    "totalTokenCount": 46
  },
  "modelVersion": "gemini-2.0-flash"
}
```

- `usageMetadata` may be **absent** on error / safety-blocked responses → parse
  null-safe. With Jackson `JsonNode`, `path(...)` never returns null; use
  `.path("usageMetadata").path("promptTokenCount").asInt(0)` so missing → 0.
- `candidatesTokenCount` can be missing if generation was blocked even when
  `promptTokenCount` is present. Default each field independently to 0.

## New / changed files

### 1. `model/TokenUsage.java` (new)
```java
public record TokenUsage(
    int promptTokens,
    int completionTokens,
    int totalTokens,
    double estimatedCostUsd
) {}
```

### 2. `services/CostEstimator.java` (new, @Component)
- Reads two rates from properties (USD per **1,000,000** tokens, matching Gemini's
  published units):
  - `gemini.cost.input-per-1m`
  - `gemini.cost.output-per-1m`
- `double estimate(int promptTokens, int completionTokens)`:
  `prompt/1_000_000.0 * inputRate + completion/1_000_000.0 * outputRate`.
- Defaults (placeholders — confirm against current Gemini pricing before relying
  on them): input `0.10`, output `0.40` per 1M for gemini-2.0-flash.

### 3. `services/UsageTracker.java` (new, @Component)
- Thread-safe cumulative counters: `AtomicLong promptTokens, completionTokens,
  totalTokens; DoubleAdder costUsd;` plus `AtomicLong requestCount`.
- `void record(TokenUsage u)` — adds to all counters.
- `UsageSnapshot snapshot()` — returns an immutable view (record) for the endpoint.
- Note: in-memory only; resets on restart. A future phase could persist to Redis
  (ties into Phase 3) — leave a TODO.

### 4. `services/EmailGeneratorService.java` (modify)
- Inject `CostEstimator` and `UsageTracker` via constructor (alongside existing
  WebClient.Builder). Keep `generateEmailReply` returning `String`.
- Replace `System.out.println("generateEmailReply method called")` with SLF4J
  `Logger log = LoggerFactory.getLogger(...)`.
- Make `ObjectMapper` a **single reused instance** (private static final or a bean),
  not `new ObjectMapper()` on every call.
- After getting `response`, parse the tree **once**:
  - extract reply text (existing logic).
  - extract `promptTokenCount` / `candidatesTokenCount` / `totalTokenCount`
    (null-safe, default 0). If `totalTokenCount` missing, fall back to
    `prompt + completion`.
  - build `TokenUsage` (with cost from `CostEstimator`), call
    `usageTracker.record(...)`, and `log.info("Gemini usage prompt={} completion={} total={} cost=${}", ...)`.
- Refactor: split the single parse into `extractReplyText(JsonNode)` and
  `extractUsage(JsonNode)` so we read the tree once, not twice.
- On parse failure, log a warning and record zero usage; still return the reply
  text (or the existing error string) — never throw from usage tracking.

### 5. `controller/EmailGeneratorController.java` (modify)
- Add `GET /api/email/usage` → returns `UsageTracker.snapshot()` as JSON
  (`ResponseEntity<UsageSnapshot>`). Inject `UsageTracker`.
- Leave `POST /generate` exactly as is.

### 6. `application.properties` (add)
```
gemini.cost.input-per-1m=0.10
gemini.cost.output-per-1m=0.40
```

## Interactions / gotchas

- **Rate-limit interceptor** matches `/api/email/**`, so `GET /usage` is also rate
  limited. That's fine for now; if it's annoying during testing, exclude `/usage`
  via `excludePathPatterns("/api/email/usage")` in `WebConfig`.
- **Phase 3 (Redis cache) interaction:** once caching lands, a cache *hit* must NOT
  call Gemini and therefore records **zero** new tokens. Keep usage recording inside
  the Gemini-call path (not the cached wrapper) so cache hits naturally cost 0.
  Leave a comment noting this so Phase 3 doesn't double-count.
- Cost numbers are estimates from published per-token pricing, not billed amounts —
  label them "estimated" everywhere (field name `estimatedCostUsd`, log text).

## Test plan

- `EmailGeneratorServiceTest` (unit):
  - Feed a canned Gemini JSON string (with `usageMetadata`) into the extraction
    helpers; assert prompt=12, completion=34, total=46 and reply text.
  - Feed JSON **without** `usageMetadata`; assert all counts default to 0 and no
    exception is thrown.
- `CostEstimatorTest`: assert `estimate(1_000_000, 1_000_000)` == input+output rate.
- Manual: start app, POST a sample email, confirm log line + `GET /api/email/usage`
  shows cumulative totals incrementing across calls.

## Build / verify

- `./mvnw test` (PowerShell: set JAVA_HOME + PATH from machine env first, as during
  setup) must pass.
- Then a manual smoke test against a real Gemini key (needs `GEMINI_URL` +
  `GEMINI_KEY` env vars; server on port 8081).

## Commit

- Branch `phase-2-token-tracking`; message `Phase 2: token usage tracking & cost estimation`.
- Update README to add a "Token usage & cost tracking" subsection once verified.

---

# Phase 3 — Execution Notes (Redis + Spring Cache)

## Hard constraints / decisions

- **Graceful degradation is mandatory.** There is no Redis running in this env
  (no Docker). A failed cache op MUST NOT break `/generate`. Implement
  `CachingConfigurer.errorHandler()` returning a `CacheErrorHandler` that logs and
  swallows get/put/evict/clear errors → requests fall through to the real Gemini
  call when Redis is unreachable.
- **Cache hits cost 0 tokens.** Put `@Cacheable` on `generateEmailReply` itself, so
  a hit returns the cached String without entering the method — usage recording
  never runs, so cached responses naturally record zero tokens. This is the Phase 2
  interaction note made concrete; do NOT move usage recording above the cache.
- Connection is **lazy** (Lettuce) — adding the starter + cache manager bean does
  NOT connect at startup, so `contextLoads` and unit tests still pass without Redis.

## Changes

### `pom.xml`
- `spring-boot-starter-data-redis`
- `spring-boot-starter-cache`

### `config/CacheConfig.java` (rewrite the stub)
- `@Configuration @EnableCaching`, implement `CachingConfigurer`.
- `@Bean RedisCacheManager` with `RedisCacheConfiguration`:
  - `entryTtl(Duration.ofHours(24))`
  - keys: `StringRedisSerializer`
  - values: `GenericJackson2JsonRedisSerializer`
  - `disableCachingNullValues()`
- Override `errorHandler()` → custom `CacheErrorHandler` (log WARN, swallow).

### `services/EmailGeneratorService.java`
- Annotate `generateEmailReply`:
  `@Cacheable(value = "emailReplies", key = "#emailRequest.emailContent + '::' + #emailRequest.tone")`
- Note: a `null` tone renders as the string `"null"` in the key — acceptable.

### `application.properties`
```
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

### `docker-compose.yml` (new, repo root or backend dir)
- `redis:7-alpine`, port 6379, for local dev / real cache testing.

## Gotchas
- `@Cacheable` only applies on calls through the Spring proxy (controller →
  service). Internal self-calls would bypass it — current flow is external, fine.
- `EmailRequest` (Lombok `@Data`) already has value-based equals/hashCode, but the
  cache key here is an explicit SpEL string, not hashCode — deterministic.
- Cost rates note from Phase 2 still applies.

## Test / verify
- `./mvnw test` must still pass with NO Redis running (proves graceful degradation
  path + lazy connection).
- Optional real test (needs Docker/Redis): `docker compose up -d redis`, start app,
  POST same body twice → 2nd is instant, `usage` requestCount increments only on the
  miss, `redis-cli KEYS 'emailReplies*'` shows the entry.

## Commit
- Branch `phase-3-redis-cache`; message `Phase 3: Redis response caching via Spring Cache`.
