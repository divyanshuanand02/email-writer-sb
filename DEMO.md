# 🎤 Demo & Presentation Runbook — AI Email Reply Assistant

A step-by-step guide to demonstrate the project from IntelliJ through a live AI
reply inside real Gmail, with every command and the talking points that make it
land.

> **Stack:** Spring Boot 4 · Gemini API · Redis (Spring Cache) · Bucket4j · WebClient · Chrome Extension (MV3)
> **Backend port:** `8081` · **Model:** `gemini-2.5-flash`

---

## 0. One-time prerequisites (already done on this machine)

- JDK 21 (Microsoft OpenJDK) installed; IntelliJ project SDK = **21 Microsoft OpenJDK**.
- IntelliJ run config **EmailWriterSbApplication** with `GEMINI_URL` + `GEMINI_KEY` env vars.
- Portable Redis at `E:\email-writer-sb\.redis\` (no Docker needed).
- Chrome extension at `E:\email-writer-sb\extension\`.

> ⚠️ **Before sharing your screen:** rotate the Gemini key (it may have been pasted in chat).
> Update it in IntelliJ → **Run → Edit Configurations → Environment variables → `GEMINI_KEY`**.
> Use model **`gemini-2.5-flash`** — `gemini-2.0-flash` has zero free-tier quota on this key.

---

## 1. The 30-second architecture pitch

> "Gmail → a Chrome extension detects the compose window with a **MutationObserver**
> and injects an **AI Reply** button + tone selector → it POSTs the email text and
> chosen tone to my **Spring Boot** API → which **rate-limits** per IP (Bucket4j),
> checks a **Redis cache**, calls **Gemini** via WebClient, **tracks token usage &
> cost**, and returns the reply → the extension drops it into the compose box."

```
Gmail UI → Chrome Extension → Spring Boot REST API → Redis cache? → Gemini API
                                     ↑ Bucket4j rate limit · Jackson token/cost tracking
```

---

## 2. Start everything

### 2a. (Optional) Start Redis — for the caching demo
Open a PowerShell window and leave it running:
```powershell
& "E:\email-writer-sb\.redis\redis-server.exe" --port 6379
```
Verify in another window:
```powershell
& "E:\email-writer-sb\.redis\redis-cli.exe" -p 6379 ping   # -> PONG
```
> The app runs fine without Redis (graceful degradation) — Redis just enables caching.

### 2b. Start the backend from IntelliJ
1. Open the project `E:\email-writer-sb` in IntelliJ.
2. Top-right: run config = **EmailWriterSbApplication**.
3. Click the green **▶ Run**.
4. Ready when the Run panel shows:
   ```
   Tomcat started on port 8081 (http)
   Started EmailWriterSbApplication in ~X seconds
   ```

---

## 3. Backend sanity check (do this privately before presenting)

> Run all API commands in a **separate** PowerShell window.

```powershell
# define a reusable request body
$body = '{"emailContent":"Hi, can we move our meeting to Friday?","tone":"professional"}'

# generate a reply
Invoke-RestMethod -Uri "http://localhost:8081/api/email/generate" -Method POST -ContentType "application/json" -Body $body

# cumulative token usage + estimated cost
Invoke-RestMethod -Uri "http://localhost:8081/api/email/usage"
```
If both return sensible output, the backend + key are healthy.

---

## 4. Load the Chrome extension (one-time)

1. Chrome → `chrome://extensions`
2. Enable **Developer mode** (top-right)
3. **Load unpacked** → select **`E:\email-writer-sb\extension`**
4. "Email Writer Assistant" appears. (After code changes, click its **↻ reload** icon.)

---

## 5. 🎯 The live Gmail demo

1. Open **mail.google.com**. If already open, **refresh** so the content script loads.
2. Open any email → click **Reply** (the compose box opens).
3. In the compose toolbar: a **tone dropdown** + an **AI Reply** button appear.
4. Pick **Professional** → click **AI Reply** → button shows "Generating…" → the reply
   is inserted into the compose box.
5. Change tone to **Casual** → click **AI Reply** again → noticeably more relaxed wording.

> **For a technical audience — open DevTools (F12) first:**
> - **Console:** `"Compose Window Detected"`, `"Toolbar found, creating AI button"`.
> - **Network:** the live `POST` to `localhost:8081/api/email/generate` and the reply.

---

## 6. Feature showcase (the "wow" moments)

> Keep the `$body` variable from step 3 defined in your PowerShell window.

### 6a. Redis caching — instant repeat
Click **AI Reply** on the *same* email + tone again → near-instant (~30 ms vs ~3–4 s).
Prove it didn't call Gemini:
```powershell
Invoke-RestMethod -Uri "http://localhost:8081/api/email/usage"
```
> "Identical requests are served from Redis — no Gemini call, **zero extra tokens**.
> `requestCount` doesn't increase on the repeat."

### 6b. Token usage & cost tracking
The same `/usage` response shows `promptTokens`, `completionTokens`, `totalTokens`,
and `estimatedCostUsd` — parsed from Gemini's `usageMetadata` with Jackson.

### 6c. Rate limiting (Bucket4j) — burst until 429
```powershell
$body = '{"emailContent":"Quick test for rate limiting.","tone":"professional"}'
1..12 | ForEach-Object {
    $n = $_
    try {
        Invoke-WebRequest -Uri "http://localhost:8081/api/email/generate" -Method POST -ContentType "application/json" -Body $body -UseBasicParsing | Out-Null
        "req ${n}: 200 OK"
    } catch {
        "req ${n}: $([int]$_.Exception.Response.StatusCode) rate limited"
    }
}
```
Expected: `req 1..10: 200 OK`, then `req 11/12: 429 rate limited`.
> "Each client IP gets 10 tokens/minute; the 11th request is rejected at the
> interceptor before it ever reaches Gemini — protecting quota and cost."

> **PowerShell gotchas in that command:**
> - Wrap a variable in `${}` when a `:` follows it in a string (`"req ${n}:"`), else
>   PowerShell reads `$n:` as a drive reference.
> - Inside `catch`, `$_` becomes the **error object**, so capture the loop number
>   first (`$n = $_`) to print it.

### 6d. Resilience — graceful degradation
Close the Redis window, then generate again → still works (just slower); the log shows
`continuing without cache`.
> "Redis is an optimization, not a dependency."

---

## 7. The tight 90-second script

1. Run from IDE (already up).
2. Gmail → Reply → **Professional** → **AI Reply** (watch it insert).
3. Switch to **Casual** → **AI Reply** (different voice).
4. Click **AI Reply** once more (instant = cache hit).
5. Show `/usage` → tokens + cost.
6. (Optional) Run the 429 burst.

That sequence hits every headline feature: extension injection, multi-tone, Gemini,
caching, token/cost tracking, rate limiting.

---

## 8. API reference

| Method | Endpoint | Purpose |
|--------|----------|---------|
| `POST` | `/api/email/generate` | Body `{"emailContent":"...","tone":"..."}` → plain-text reply. 502 on Gemini failure, 429 when rate limited. |
| `GET`  | `/api/email/usage` | Cumulative `requestCount`, prompt/completion/total tokens, `estimatedCostUsd`. |

---

## 9. Troubleshooting

| Symptom | Fix |
|---------|-----|
| Button/dropdown missing in Gmail | Refresh Gmail; confirm extension enabled; reload it on `chrome://extensions` |
| `/generate` returns **502** | Key/quota — confirm run config uses **`gemini-2.5-flash`** |
| Reply doesn't insert | Make sure the **Reply/compose box is open and focused** before clicking AI Reply |
| Every call is **429** | Hit the 10/min limit — wait ~60s |
| App won't start in IDE | Project Structure → SDK = **21 Microsoft OpenJDK** |
| `docker ... not recognized` | Docker isn't installed — use the portable Redis command in §2a |
| `Variable reference is not valid` in PowerShell | Use `${name}` before a `:` in strings (see §6c) |

---

## 10. Start Redis without Docker (reference)

Docker is **not** required. The `docker-compose.yml` works only if Docker is installed.
Without it, use the portable Redis:
```powershell
& "E:\email-writer-sb\.redis\redis-server.exe" --port 6379
```
