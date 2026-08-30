# AI Chat Summarizer

A real-time, WhatsApp-style chat application with AI-powered conversation summarization and automatic action-item extraction, built as a full-stack Spring Boot + vanilla JS project.

**Live app:** https://aichatsummarizer.vasutech.online
**Backend health check:** https://chat-api.vasutech.online

---

## Table of Contents
- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Data Model](#data-model)
- [API Reference](#api-reference)
- [WebSocket Protocol](#websocket-protocol)
- [AI Integration (Gemini)](#ai-integration-gemini)
- [Auth Flow](#auth-flow)
- [Project Structure](#project-structure)
- [Deployment](#deployment)
- [Environment Variables](#environment-variables)
- [Local Development](#local-development)
- [Known Limitations](#known-limitations)
- [Roadmap](#roadmap)

---

## Overview

AI Chat Summarizer lets users register, verify their email via OTP, add contacts, create 1:1 or group chats, message in real time over WebSockets, and — at any point — ask an AI assistant to **summarize the conversation** or **extract action items** (task, owner, deadline) from it, powered by Google's Gemini API.

It's split into two independently deployed pieces:
- A **Spring Boot backend** (REST + WebSocket/STOMP) handling auth, contacts, messaging, and Gemini calls.
- A **static vanilla JS frontend** (single `index.html`, no build step) that talks to the backend over HTTPS/WSS.

---

## Architecture

```
┌─────────────────────────────┐         HTTPS (REST)          ┌──────────────────────────────┐
│         FRONTEND            │ ─────────────────────────────▶│           BACKEND             │
│  aichatsummarizer.vasutech  │                                │   chat-api.vasutech.online    │
│  .online (Vercel)           │◀───────────────────────────── │   (Azure App Service, Docker)  │
│                              │        WSS (STOMP/SockJS)      │                                │
│  index.html + config.js     │◀──────────────────────────────▶│  Spring Boot 3.2 / Java 17     │
│  (static, no build step)    │                                │  ┌──────────────────────────┐  │
└─────────────────────────────┘                                │  │ AuthController            │  │
                                                                │  │ ContactController          │  │
                                                                │  │ ChatController (WS + AI)   │  │
                                                                │  │ WebSocketConfig             │  │
                                                                │  │ CorsConfig                  │  │
                                                                │  └──────────────────────────┘  │
                                                                │             │                   │
                                                                │             ▼                   │
                                                                │   ┌──────────────────┐          │
                                                                │   │  PostgreSQL DB     │          │
                                                                │   │  (JPA/Hibernate)   │          │
                                                                │   └──────────────────┘          │
                                                                │             │                   │
                                                                │             ▼                   │
                                                                │   ┌──────────────────┐          │
                                                                │   │  Resend API        │  (OTP emails)
                                                                │   └──────────────────┘          │
                                                                └──────────────┬────────────────┘
                                                                               │ HTTPS
                                                                               ▼
                                                                 ┌───────────────────────────┐
                                                                 │  Google Gemini API          │
                                                                 │  (gemini-3.6-flash)          │
                                                                 │  — summarize                 │
                                                                 │  — extract action items      │
                                                                 └───────────────────────────┘
```

**DNS:** Both subdomains sit on `vasutech.online`, managed via Cloudflare (DNS-only / grey-cloud mode, required for Vercel + Azure custom-domain SSL validation).

---

## Tech Stack

| Layer            | Technology                                               |
|------------------|-----------------------------------------------------------|
| Frontend         | Vanilla HTML/CSS/JS (no framework, no build step)         |
| Real-time        | SockJS + STOMP.js over WebSocket                          |
| Backend          | Java 17, Spring Boot 3.2.3                                |
| Persistence      | Spring Data JPA + Hibernate, PostgreSQL                   |
| Auth             | Email + password (BCrypt) + Email OTP verification        |
| Email delivery   | Resend API                                                |
| AI               | Google Gemini (`gemini-3.6-flash`) via raw HTTP calls      |
| Hosting (backend)| Azure App Service (Docker container)                      |
| Hosting (frontend)| Vercel (static site)                                     |
| DNS              | Cloudflare (`vasutech.online`)                             |
| Containerization | Docker (multi-stage build, Gradle)                        |

---

## Data Model

Five JPA entities:

- **`User`** (`app_user`) — id, name, email (unique), passwordHash (BCrypt), emailVerified, otpCode
- **`Conversation`** (`conversation`) — id, createdAt, isGroup, name (for groups)
- **`ConversationMember`** (`conversation_member`) — links a `User` to a `Conversation`, tracks `lastReadAt` (used for catch-up summaries)
- **`Message`** (`message`) — id, conversation, sender, content, sentAt, deletedForEveryone, hiddenFor (comma-separated emails, for "delete for me")
- **`ContactRequest`** (`contact_request`) — sender, receiver, status (`PENDING` / `ACCEPTED` / `REJECTED`)

Relationships:
```
User ──< ConversationMember >── Conversation ──< Message >── User (sender)
User ──< ContactRequest >── User
```

A 1:1 chat is just a `Conversation` with `isGroup=false` and exactly 2 `ConversationMember` rows, created automatically when a `ContactRequest` is accepted.

---

## API Reference

Base URL: `https://chat-api.vasutech.online`

### Auth (`AuthController`)
| Method | Path             | Body                                   | Notes                                  |
|--------|------------------|-----------------------------------------|-----------------------------------------|
| POST   | `/register`      | `{ name, email, password }`             | Creates unverified user, sends OTP      |
| POST   | `/verify-otp`    | `{ email, otp }`                        | Marks email verified                    |
| POST   | `/resend-otp`    | `{ email }`                             | Regenerates + resends OTP               |
| POST   | `/login`         | `{ email, password }`                   | Returns `{ success, name }`; blocks unverified accounts |

### Contacts (`ContactController`, prefix `/contacts`)
| Method | Path              | Params/Body                                        | Notes                                  |
|--------|-------------------|------------------------------------------------------|------------------------------------------|
| GET    | `/search`         | `?query=&myEmail=`                                   | Search users by name/email               |
| POST   | `/request`        | `{ myEmail, targetEmail }`                            | Send contact request                     |
| GET    | `/pending`        | `?myEmail=`                                           | List incoming pending requests           |
| POST   | `/accept`         | `{ requestId }`                                       | Accepts request, auto-creates 1:1 conversation |
| POST   | `/group/create`   | `{ myEmail, groupName, memberEmails[] }`              | Creates a group conversation             |
| GET    | `/list`           | `?myEmail=`                                           | List all conversations (contacts + groups) |

### Chat / Messages (`ChatController`)
| Method | Path                              | Notes                                                       |
|--------|-----------------------------------|--------------------------------------------------------------|
| GET    | `/messages/{conversationId}`      | `?myEmail=` — fetch message history (respects hidden/deleted) |
| DELETE | `/messages/{messageId}`           | `?myEmail=&forEveryone=` — soft-delete a message              |
| GET    | `/catchup/{conversationId}`       | `?myEmail=` — returns AI summary of unread messages since last read |
| POST   | `/summarize/{conversationId}`     | Full-conversation AI summary                                  |
| POST   | `/extractActions/{conversationId}`| Returns JSON array of `{task, owner, deadline}`                |
| POST   | `/summarize`                      | Legacy: raw text body → summary                                |
| POST   | `/extractActions`                 | Legacy: raw text body → action items JSON                      |

### Misc
| Method | Path | Notes                          |
|--------|------|---------------------------------|
| GET    | `/`  | Health check — "AI Chat Summarizer backend is running." |

---

## WebSocket Protocol

- **Endpoint:** `wss://chat-api.vasutech.online/ws` (SockJS fallback supported)
- **Broker:** Simple in-memory STOMP broker (`/topic` prefix for broadcasts, `/app` for client-sent messages)

| Direction | Destination                        | Payload                                          |
|-----------|--------------------------------------|---------------------------------------------------|
| Client → Server | `/app/chat/{conversationId}`  | `{ senderEmail, content }`                         |
| Server → Client | `/topic/conversation/{conversationId}` | New message: `{ messageId, conversationId, senderEmail, senderUsername, content, sentAt }` |
| Server → Client | `/topic/conversation/{conversationId}` | Delete event: `{ type: "delete", messageId, deletedForEveryone: true }` |

Each connected client subscribes to `/topic/conversation/{id}` for the conversation currently open in the UI.

---

## AI Integration (Gemini)

Backend calls `gemini-3.6-flash` directly over `HttpClient` (no SDK) at:
```
https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key={GEMINI_API_KEY}
```

Two prompt modes:
1. **Summarize** — 3–5 sentence summary of the transcript, mentioning who said what and any decisions made.
2. **Extract actions** — forces the model to return a raw JSON array of `{task, owner, deadline}` objects (or `[]`), with no markdown fences.

⚠️ **Free tier limit:** 20 requests/day per model on the Gemini free tier (`generativelanguage.googleapis.com/generate_content_free_tier_requests`). Exceeding this returns HTTP 429 with a `retryDelay`. See [Known Limitations](#known-limitations).

---

## Auth Flow

```
Register ──▶ OTP emailed via Resend ──▶ Verify OTP ──▶ Login ──▶ Session stored in localStorage
   │                                         │
   └── unverified accounts can't log in ─────┘  (login returns needsVerification:true)
```

No JWT/session tokens currently — the frontend persists `myEmail` / `myUsername` in `localStorage` after a successful login and re-hydrates on page load.

---

## Project Structure

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
│   │   ├── model/                        # User, Conversation, ConversationMember, Message, ContactRequest
│   │   ├── repository/                   # Spring Data JPA repositories
│   │   └── service/EmailService.java     # Resend OTP emails
│   ├── src/main/resources/application.properties
│   ├── build.gradle
│   └── Dockerfile
├── frontend/
│   ├── index.html      # entire UI + client-side logic (auth, chat, AI panel)
│   ├── config.js        # window.BACKEND_URL — only env-style config for static site
│   ├── logo.png          # app icon / OG image
│   ├── vercel.json       # cleanUrls, no trailing slash
│   └── verify.html
└── README.md
```

---

## Deployment

### Backend → Azure App Service (Docker)
1. `backend/Dockerfile` does a multi-stage Gradle build (`eclipse-temurin:17-jdk-alpine` → `17-jre-alpine`).
2. Push to Azure App Service as a container; exposes port `8080` (mapped via `${PORT}`).
3. Custom domain: `chat-api.vasutech.online` → CNAME in Cloudflare (DNS-only) → Azure.

### Frontend → Vercel (static)
1. No build step — Vercel serves `frontend/` as-is.
2. Custom domain: `aichatsummarizer.vasutech.online` → CNAME (`*.vercel-dns-XXX.com`) in Cloudflare, **proxy disabled (DNS only)** — required for Vercel's SSL cert issuance to succeed.
3. `frontend/config.js` hardcodes `window.BACKEND_URL` since a static HTML/JS site has no build step to inject environment variables — Vercel's dashboard env vars do **not** apply here.

### DNS (Cloudflare, `vasutech.online`)
| Subdomain              | Type  | Target                              | Proxy      |
|-------------------------|-------|----------------------------------------|------------|
| `aichatsummarizer`      | CNAME | Vercel-provided `*.vercel-dns-*.com`   | DNS only   |
| `chat-api`              | CNAME | Azure App Service custom domain verify | DNS only   |

---

## Environment Variables

### Backend (`application.properties`, set as Azure App Settings)
| Variable          | Purpose                                             | Default            |
|-------------------|--------------------------------------------------------|----------------------|
| `PORT`            | Server port                                             | `8080`               |
| `GEMINI_API_KEY`  | Google AI Studio Gemini API key                          | *(required)*         |
| `FRONTEND_ORIGIN` | Allowed CORS/WebSocket origin (e.g. deployed Vercel URL) | `*`                  |
| `DATABASE_URL`    | PostgreSQL JDBC connection string                        | *(required)*         |

### Frontend (`frontend/config.js`)
| Variable            | Purpose                          |
|----------------------|--------------------------------------|
| `window.BACKEND_URL` | Full backend base URL (hardcoded, since static sites have no build-time env injection) |

---

## Local Development

```bash
# Backend
cd backend
./gradlew bootRun
# Runs on http://localhost:8080, needs DATABASE_URL + GEMINI_API_KEY set locally

# Frontend
# Just open frontend/index.html directly in a browser —
# config.js defaults to http://localhost:8080 for local testing
```

---

## Known Limitations

- **Gemini free tier cap:** 20 requests/day per model — summarize/extract-actions calls will return HTTP 429 once exceeded. No retry/backoff currently implemented in `ChatController`.
- **No JWT/session tokens:** auth state lives only in `localStorage` on the client; not suitable for multi-device session invalidation.
- **In-memory STOMP broker:** doesn't scale horizontally — fine for single-instance deployment, would need an external broker (RabbitMQ/ActiveMQ) to scale out.
- **No message pagination:** `/messages/{conversationId}` returns the full history every time.

## Roadmap

- Add retry-with-backoff for Gemini 429s
- WhatsApp-style contact/presence system improvements
- Message pagination / infinite scroll
- Typing indicators & read receipts (UI groundwork already present via `lastReadAt`)
