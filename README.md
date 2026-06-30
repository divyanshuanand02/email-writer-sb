# AI Email Reply Generator ✉️🤖

An AI-powered email reply generator that integrates with Gmail using a Chrome Extension and a Spring Boot backend.
The extension extracts email content from the Gmail UI and sends it to a backend service that uses Google's Gemini API to generate a contextual reply instantly.

---

## 🚀 Features

* Generate AI-powered email replies directly inside Gmail
* Multiple tone support via an in-toolbar dropdown (Professional, Casual, Friendly, Formal, Concise, Sarcastic)
* Chrome Extension integration with Gmail UI
* Spring Boot backend service
* Google Gemini API integration via Spring's `WebClient`
* Per-IP rate limiting with Bucket4j (token-bucket, returns HTTP 429)
* Redis response caching via Spring Cache (identical requests skip Gemini; graceful degradation if Redis is down)
* Token usage tracking & cost estimation parsed from Gemini `usageMetadata` (Jackson), exposed at `/api/email/usage`
* Automatic reply insertion into Gmail compose box
* MutationObserver-based DOM detection for Gmail compose windows

---

## 🏗️ Architecture

```
Gmail UI
   ↓
Chrome Extension (Content Script)
   ↓
Spring Boot REST API
   ↓
Google Gemini API
   ↓
AI Generated Reply
   ↓
Inserted back into Gmail Compose Box
```

---

## 🛠️ Tech Stack

### Backend

* Java 21
* Spring Boot 4
* Spring Web (servlet MVC — the request stack)
* Spring `WebClient` (from WebFlux) for the outbound Gemini call — used synchronously (`.block()`), since the response is cached and the request path is servlet-based
* Spring Cache + Spring Data Redis (response caching)
* Bucket4j (rate limiting)
* Jackson (response parsing, token usage)
* Maven

> Note: `WebClient` here is the reactive HTTP client used in blocking mode for a single outbound call — the application itself is not a reactive/non-blocking WebFlux service.

### Frontend (Extension)

* JavaScript
* Chrome Extensions API
* MutationObserver
* Gmail DOM manipulation

### AI

* Google Gemini API

---

## 📂 Project Structure

```
email-writer-sb/
│
├── email-writer-sb/                         # Spring Boot backend (Maven module)
│   ├── src/main/java/com/email/writer/app/
│   │   ├── controller/EmailGeneratorController.java
│   │   ├── services/
│   │   │   ├── EmailGeneratorService.java
│   │   │   ├── RateLimitingService.java
│   │   │   ├── CostEstimator.java
│   │   │   └── UsageTracker.java
│   │   ├── config/                          # WebClient, Web (interceptors), Cache (Redis)
│   │   ├── ratelimit/RateLimitInterceptor.java
│   │   ├── exception/GeminiUnavailableException.java
│   │   └── model/                           # EmailRequest, TokenUsage
│   ├── src/main/resources/application.properties
│   └── docker-compose.yml                   # local Redis
│
├── extension/                               # Chrome extension (MV3)
│   ├── manifest.json
│   ├── content.js
│   └── content.css
│
└── README.md
```

---

## ⚙️ Backend Setup (Spring Boot)

### 1️⃣ Clone the repository

```
git clone https://github.com/yourusername/email-writer.git
cd email-writer
```

### 2️⃣ Configure Gemini API

Add the following in `application.properties`:

```
gemini.api.url=https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=
gemini.api.key=YOUR_GEMINI_API_KEY
```

### 3️⃣ (Optional) Start Redis for response caching

```
docker compose up -d redis
```

Redis is optional — if it isn't running, caching is skipped and the app still
serves replies (it degrades gracefully). Configure host/port via
`spring.data.redis.host` / `spring.data.redis.port`.

### 4️⃣ Run the Spring Boot application

```
mvn spring-boot:run
```

Backend runs on:

```
http://localhost:8081
```

---

## 🧩 Chrome Extension Setup

1. Open Chrome
2. Go to

```
chrome://extensions
```

3. Enable **Developer Mode**

4. Click **Load Unpacked**

5. Select the `extension` folder from the project

6. Open Gmail

```
https://mail.google.com
```

You will see the **AI Reply button** in the compose toolbar.

---

## 📡 API Endpoint

### Generate Email Reply

**POST**

```
/api/email/generate
```

Example request:

```
{
  "emailContent": "Hello! Thank you for reaching out to us",
  "tone": "professional"
}
```

Example response:

```
Dear [Name],

Thank you for your message. I appreciate you reaching out and will review the details you provided.

Best regards,
[Your Name]
```

On a Gemini failure (bad key, quota, network) the endpoint returns **502 Bad Gateway**
instead of a raw 500. Each client IP is rate limited (HTTP **429** when exceeded).

### Cumulative Token Usage

**GET**

```
/api/email/usage
```

Returns cumulative tokens and estimated cost since the process started:

```
{
  "requestCount": 1,
  "promptTokens": 11,
  "completionTokens": 7,
  "totalTokens": 18,
  "estimatedCostUsd": 3.9e-6
}
```

> Cost is an estimate from configurable per-1M-token rates
> (`gemini.cost.input-per-1m` / `output-per-1m`) — verify against current Gemini pricing.

---

## 🔐 CORS Configuration

Since the Chrome Extension runs inside Gmail (`mail.google.com`) and the backend runs on `localhost`, CORS is enabled:

```
@CrossOrigin(origins = "*")
```

This allows the extension to call the backend API.

---

## 🧠 How It Works

1. User opens Gmail and clicks **Compose**
2. The Chrome extension detects the compose window using `MutationObserver`
3. An **AI Reply button** is injected into the toolbar
4. When clicked:

   * Email content is extracted from the DOM
   * A request is sent to the Spring Boot API
5. Backend sends prompt to **Gemini API**
6. AI-generated reply is returned
7. Reply is automatically inserted into the Gmail compose box

---

## 📸 Demo Flow

```
Open Gmail
   ↓
Click Compose
   ↓
Click "AI Reply"
   ↓
AI Generated Response Appears
```

---

## 📈 Future Improvements

* Reply editing suggestions
* Email summarization
* Multi-language support
* Gmail thread context understanding
* Persist usage totals (e.g. in Redis) across restarts
* Deployment to cloud (AWS / GCP)

---

## 🤝 Contributing

Contributions are welcome!

1. Fork the repository
2. Create a feature branch

```
git checkout -b feature-name
```

3. Commit your changes

```
git commit -m "Added new feature"
```

4. Push to branch

```
git push origin feature-name
```

5. Open a Pull Request

---

## 📜 License

This project is licensed under the MIT License.

---

## 👨‍💻 Author

Divyanshu Anand

Software Engineer | Java | Spring Boot | AI Integrations
