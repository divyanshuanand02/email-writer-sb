# AI Email Reply Generator ✉️🤖

An AI-powered email reply generator that integrates with Gmail using a Chrome Extension and a Spring Boot backend.
The extension extracts email content from the Gmail UI and sends it to a backend service that uses Google's Gemini API to generate a contextual reply instantly.

---

## 🚀 Features

* Generate AI-powered email replies directly inside Gmail
* Multiple tone support (Professional, Casual, Sarcastic, etc.)
* Chrome Extension integration with Gmail UI
* Spring Boot backend service
* Google Gemini API integration
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
* Spring Boot
* Spring WebFlux
* WebClient
* Jackson
* Maven

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
email-writer/
│
├── backend/
│   ├── controller
│   │   └── EmailGeneratorController.java
│   ├── service
│   │   └── EmailGeneratorService.java
│   ├── model
│   │   └── EmailRequest.java
│   └── application.properties
│
├── extension/
│   ├── manifest.json
│   ├── content.js
│   └── styles.css
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

### 3️⃣ Run the Spring Boot application

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

* Tone selection dropdown
* Reply editing suggestions
* Email summarization
* Multi-language support
* Gmail thread context understanding
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
