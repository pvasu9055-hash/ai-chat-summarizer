<div align="center">

# 💬 AI Chat Summarizer

**A real-time, WhatsApp-style messaging platform with AI-powered conversation intelligence.**

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Database-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![AWS](https://img.shields.io/badge/AWS-Elastic%20Beanstalk-FF9900?logo=amazonaws&logoColor=white)](https://aws.amazon.com/elasticbeanstalk/)
[![Vercel](https://img.shields.io/badge/Vercel-Frontend-black?logo=vercel&logoColor=white)](https://vercel.com/)
[![Gemini](https://img.shields.io/badge/Google%20Gemini-AI-4285F4?logo=googlegemini&logoColor=white)](https://ai.google.dev/)
[![WebSocket](https://img.shields.io/badge/WebSocket-STOMP%2FSockJS-010101?logo=websocket&logoColor=white)](#-websocket-protocol)

**[Live App](https://aichatsummarizer.vasutech.online)** · **[Backend Health](https://chat-api.vasutech.online)** · **[Report Issue](https://github.com/pvasu9055-hash/ai-chat-summarizer/issues)**

</div>

---

## ✨ Overview

**AI Chat Summarizer** is a full-stack real-time messaging application where every conversation comes with a built-in AI assistant. Users register with email + OTP verification, add contacts, message 1:1 or in groups over WebSockets, and at any point ask Gemini to:

- 📝 **Summarize** the entire conversation in a few clear sentences
- ✅ **Extract action items** — task, owner, and deadline — as structured data

Built to demonstrate a complete production pipeline: authenticated real-time messaging, relational data modeling, third-party AI integration, and independent cloud deployment of frontend and backend across two different providers.

---

## 🏗️ Architecture

```
┌────────────────────────────────┐         HTTPS (REST)          ┌─────────────────────────────────┐
│            FRONTEND              │ ─────────────────────────────▶│              BACKEND               │
│  aichatsummarizer.vasutech.online │                               │      chat-api.vasutech.online       │
│         (Vercel · Static)          │◀───────────────────────────  │  (AWS Elastic Beanstalk · Docker)    │
│                                     │      WSS (STOMP / SockJS)     │                                     │
│    index.html + config.js           │◀──────────────────────────▶│      Spring Boot 3.2 · Java 17       │
│    (zero build step)                │                              │  ┌───────────────────────────────┐ │
└────────────────────────────────┘                                 │  │ AuthController                    │ │
                                                                     │  │ ContactController                  │ │
                                                                     │  │ ChatController (WS + AI)             │ │
                                                                     │  │ WebSocketConfig                       │ │
                                                                     │  │ CorsConfig                             │ │
                                                                     │  └───────────────────────────────┘ │
                                                                     │                 │                     │
                                                                     │                 ▼                     │
                                                                     │      ┌─────────────────────┐        │
                                                                     │      │    PostgreSQL DB       │        │
                                                                     │      │   (JPA / Hibernate)     │        │
                                                                     │      └─────────────────────┘        │
                                                                     │                 │                     │
                                                                     │                 ▼                     │
                                                                     │      ┌─────────────────────┐        │
                                                                     │      │      Resend API         │  (OTP emails)
                                                                     │      └─────────────────────┘        │
                                                                     └────────────────┬────────────────────┘
                                                                                       │ HTTPS
                                                                                       ▼
                                                                        ┌───────────────────────────────┐
                                                                        │       Google Gemini API           │
                                                                        │       (gemini-3.6-flash)           │
                                                                        │    — summarize conversation         │
                                                                        │    — extract action items            │
                                                                        └───────────────────────────────┘
```

**DNS:** Both subdomains live under `vasutech.online`, managed via **Cloudflare** in DNS-only (grey-cloud) mode — required for both Vercel's and AWS's SSL certificate validation on custom domains.

---

## 🧰 Tech Stack

| Layer               | Technology                                                  |
|---------------------|--------------------------------------------------------------|
| **Frontend**        | Vanilla HTML / CSS / JavaScript — no framework, no build step |
| **Real-time**       | SockJS + STOMP.js over WebSocket                             |
| **Backend**         | Java 17, Spring Boot 3.2.3                                    |
| **Persistence**     | Spring Data JPA + Hibernate, PostgreSQL                       |
| **Auth**            | Email + password (BCrypt hash) + Email OTP verification       |
| **Email delivery**  | Resend API                                                     |
| **AI**              | Google Gemini (`gemini-3.6-flash`) via raw HTTP calls          |
| **Backend hosting** | AWS Elastic Beanstalk (Docker container)                       |
| **Frontend hosting**| Vercel (static site)                                            |
| **DNS**             | Cloudflare (`vasutech.online`)                                  |
| **Containerization**| Docker (multi-stage Gradle build)                                |

---

## 🗃️ Data Model

Five JPA entities power the whole app:

| Entity | Table | Purpose |
|--------|-------|---------|
| `User` | `app_user` | id, name, email (unique), passwordHash (BCrypt), emailVerified, otpCode |
| `Conversation` | `conversation` | id, createdAt, isGroup, name (for groups) |
| `ConversationMember` | `conversation_member` | links a `User` ↔ `Conversation`, tracks `lastReadAt` for unread/catch-up summaries |
| `Message` | `message` | id, conversation, sender, content, sentAt, deletedForEveryone, hiddenFor |
| `ContactRequest` | `contact_request` | sender, receiver, status (`PENDING` / `ACCEPTED` / `REJECTED`) |

```
User ──< ConversationMember >── Conversation ──< Message >── User (sender)
User ──< ContactRequest >── User
```

A 1:1 chat is simply a `Conversation` with `isGroup = false` and exactly two `ConversationMember` rows — created automatically the moment a `ContactRequest` is accepted.

---

## 📡 API Reference

Base URL: `https://chat-api.vasutech.online`

### 🔐 Auth — `AuthController`
| Method | Endpoint         | Body                          | Description                              |
|--------|------------------|---------------------------------|--------------------------------------------|
| `POST` | `/register`      | `{ name, email, password }`     | Creates unverified user, sends OTP email    |
| `POST` | `/verify-otp`    | `{ email, otp }`                | Marks the account as email-verified          |
| `POST` | `/resend-otp`    | `{ email }`                     | Regenerates and resends the OTP code          |
| `POST` | `/login`         | `{ email, password }`           | Returns `{ success, name }`; blocks unverified accounts |

### 👥 Contacts — `ContactController` (`/contacts`)
| Method | Endpoint          | Params / Body                              | Description                              |
|--------|-------------------|-----------------------------------------------|---------------------------------------------|
| `GET`  | `/search`         | `?query=&myEmail=`                             | Search users by name or email                |
| `POST` | `/request`        | `{ myEmail, targetEmail }`                     | Send a contact request                        |
| `GET`  | `/pending`        | `?myEmail=`                                    | List incoming pending requests                |
| `POST` | `/accept`         | `{ requestId }`                                | Accept request → auto-creates 1:1 conversation |
| `POST` | `/group/create`   | `{ myEmail, groupName, memberEmails[] }`       | Create a group conversation                   |
| `GET`  | `/list`           | `?myEmail=`                                    | List all conversations (1:1 + groups)         |

### 💬 Chat & Messages — `ChatController`
| Method   | Endpoint                              | Description                                                     |
|----------|-----------------------------------------|---------------------------------------------------------------------|
| `GET`    | `/messages/{conversationId}`            | `?myEmail=` — fetch history (respects hidden/deleted state)          |
| `DELETE` | `/messages/{messageId}`                 | `?myEmail=&forEveryone=` — soft-delete a message                      |
| `GET`    | `/catchup/{conversationId}`             | `?myEmail=` — AI summary of unread messages since last read            |
| `POST`   | `/summarize/{conversationId}`           | Full AI summary of the conversation                                    |
| `POST`   | `/extractActions/{conversationId}`      | Returns a JSON array of `{ task, owner, deadline }`                     |
| `POST`   | `/summarize`                            | *(legacy)* raw text body → summary                                       |
| `POST`   | `/extractActions`                       | *(legacy)* raw text body → action items JSON                              |

### 🩺 Health
| Method | Endpoint | Description |
|--------|----------|--------------|
| `GET`  | `/`      | `"AI Chat Summarizer backend is running."` |

---

## 🔌 WebSocket Protocol

- **Endpoint:** `wss://chat-api.vasutech.online/ws` (SockJS fallback enabled)
- **Broker:** In-memory STOMP broker — `/topic` for broadcasts, `/app` for client-sent messages

| Direction        | Destination                              | Payload                                                                 |
|-------------------|---------------------------------------------|-----------------------------------------------------------------------------|
| Client → Server   | `/app/chat/{conversationId}`                | `{ senderEmail, content }`                                                    |
| Server → Client   | `/topic/conversation/{conversationId}`      | New message: `{ messageId, conversationId, senderEmail, senderUsername, content, sentAt }` |
| Server → Client   | `/topic/conversation/{conversationId}`      | Delete event: `{ type: "delete", messageId, deletedForEveryone: true }`        |

Every connected client subscribes to `/topic/conversation/{id}` for whichever chat is currently open in the UI.

---

## 🤖 AI Integration (Gemini)

The backend talks directly to Gemini over `HttpClient` — no SDK — at:

```
https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key={GEMINI_API_KEY}
```

**Two prompt modes:**
1. **Summarize** — a tight 3–5 sentence summary covering who said what, key topics, and any decisions made.
2. **Extract actions** — forces the model to return a *raw JSON array* of `{ task, owner, deadline }` objects (or `[]`), with no markdown fences, ready to render directly in the UI.

> ⚠️ **Free tier limit:** Gemini's free tier caps out at **20 requests/day per model** (`generate_content_free_tier_requests`). Exceeding it returns HTTP `429` with a `retryDelay`. See [Known Limitations](#-known-limitations).

---

## 🔑 Auth Flow

```
Register ──▶ OTP emailed via Resend ──▶ Verify OTP ──▶ Login ──▶ Session cached in localStorage
   │                                          │
   └── unverified accounts can't log in ──────┘  (login responds with needsVerification: true)
```

There's no JWT/session token layer yet — the frontend persists `myEmail` / `myUsername` in `localStorage` after a successful login and re-hydrates the session on page load.

---

## 📁 Project Structure

```
ai-chat-summarizer/
├── backend/
│   ├── src/main/java/com/chat/chatsummarizer/
│   │   ├── AiChatSummarizerApplication.java
│   │   ├── controller/
│   │   │   ├── AuthController.java       # register / login / OTP
│   │   │   ├── ContactController.java    # contacts, requests, groups
│   │   │   ├── ChatController.java       # messages, WebSocket, Gemini AI
│   │   │   ├── WebSocketConfig.java      # STOMP broker config
│   │   │   ├── CorsConfig.java           # CORS (frontend.origin)
│   │   │   └── PageController.java       # health check "/"
│   │   ├── model/          # User, Conversation, ConversationMember, Message, ContactRequest
│   │   ├── repository/     # Spring Data JPA repositories
│   │   └── service/EmailService.java     # Resend OTP emails
│   ├── src/main/resources/application.properties
│   ├── build.gradle
│   └── Dockerfile
├── frontend/
│   ├── index.html      # entire UI + client-side logic (auth, chat, AI panel)
│   ├── config.js         # window.BACKEND_URL — the only "env config" a static site needs
│   ├── logo.png            # app icon / social preview image
│   ├── vercel.json          # cleanUrls, no trailing slash
│   └── verify.html
└── README.md
```

---

## 🚀 Deployment

### Backend → AWS Elastic Beanstalk (Docker)
1. `backend/Dockerfile` runs a multi-stage Gradle build (`eclipse-temurin:17-jdk-alpine` → `17-jre-alpine`) and produces a slim runtime image.
2. Deployed to **AWS Elastic Beanstalk** as a single-container Docker application; exposes port `8080` (mapped via `${PORT}`).
3. Custom domain: `chat-api.vasutech.online` → CNAME in Cloudflare (DNS-only) → Elastic Beanstalk environment URL.

### Frontend → Vercel (Static)
1. Zero build step — Vercel serves the `frontend/` folder exactly as-is.
2. Custom domain: `aichatsummarizer.vasutech.online` → CNAME (`*.vercel-dns-*.com`) in Cloudflare, **proxy disabled (DNS only)** — required for Vercel's automatic SSL issuance to succeed.
3. `frontend/config.js` hardcodes `window.BACKEND_URL` since a plain static HTML/JS site has no build pipeline to inject environment variables — Vercel's dashboard-level env vars simply don't apply here.

### DNS (Cloudflare — `vasutech.online`)
| Subdomain          | Type  | Target                                | Proxy     |
|---------------------|-------|------------------------------------------|-----------|
| `aichatsummarizer`  | CNAME | Vercel-issued `*.vercel-dns-*.com`         | DNS only  |
| `chat-api`          | CNAME | AWS Elastic Beanstalk environment domain    | DNS only  |

---

## ⚙️ Environment Variables

### Backend (`application.properties`, set via Elastic Beanstalk environment properties)
| Variable          | Purpose                                                   | Default     |
|--------------------|---------------------------------------------------------------|---------------|
| `PORT`             | Server port                                                       | `8080`        |
| `GEMINI_API_KEY`   | Google AI Studio Gemini API key                                    | *(required)*  |
| `FRONTEND_ORIGIN`  | Allowed CORS / WebSocket origin (deployed Vercel URL)                | `*`           |
| `DATABASE_URL`     | PostgreSQL JDBC connection string                                     | *(required)*  |

### Frontend (`frontend/config.js`)
| Variable              | Purpose                                                        |
|------------------------|---------------------------------------------------------------------|
| `window.BACKEND_URL`  | Full backend base URL — hardcoded directly in the file (see [Deployment](#-deployment)) |

---

## 💻 Local Development

```bash
# Backend
cd backend
./gradlew bootRun
# Runs on http://localhost:8080 — requires DATABASE_URL + GEMINI_API_KEY set locally

# Frontend
# Just open frontend/index.html directly in a browser —
# config.js defaults to http://localhost:8080 for local testing
```

---

## ⚠️ Known Limitations

- **Gemini free-tier cap** — 20 requests/day per model. Summarize/extract-actions calls return HTTP `429` once exceeded; no retry-with-backoff is implemented yet in `ChatController`.
- **No JWT/session tokens** — auth state lives only in `localStorage` on the client, so there's no server-side session invalidation across devices.
- **In-memory STOMP broker** — doesn't scale horizontally; fine for a single-instance deployment, but would need an external broker (RabbitMQ/ActiveMQ) to scale out.
- **No message pagination** — `/messages/{conversationId}` returns full history on every load.

## 🗺️ Roadmap

- [ ] Retry-with-backoff for Gemini `429` responses
- [ ] WhatsApp-style contact/presence system improvements
- [ ] Message pagination / infinite scroll
- [ ] Typing indicators & read receipts (groundwork already present via `lastReadAt`)
- [ ] JWT-based session auth

---

<div align="center">

Built by **[Penkey Sri Vasu](https://vasutech.online)**

</div>
