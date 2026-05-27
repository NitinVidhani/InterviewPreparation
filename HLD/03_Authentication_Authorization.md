# 🔐 Authentication & Authorization — The Complete Guide

> **Difficulty**: Medium | **Frequency**: Asked in EVERY system design interview  
> **Why This Matters**: Every real-world system needs auth. Interviewers expect you to know the tradeoffs.

---

## 📌 Table of Contents

1. [Authentication vs Authorization](#authentication-vs-authorization)
2. [Basic Authentication](#1-basic-authentication)
3. [Session-Based Authentication](#2-session-based-authentication)
4. [Cookie-Based Authentication](#3-cookie-based-authentication)
5. [Token-Based Authentication (JWT)](#4-token-based-authentication-jwt)
6. [OAuth 1.0](#5-oauth-10)
7. [OAuth 2.0](#6-oauth-20)
8. [SSO (Single Sign-On)](#7-sso-single-sign-on)
9. [API Key Authentication](#8-api-key-authentication)
10. [Comparison Table](#comparison-table)
11. [Which One to Use When?](#which-one-to-use-when)
12. [Interview Tips](#interview-tips)

---

## 🔑 Authentication vs Authorization

Think of it like a hotel:

| Concept | Hotel Analogy | Technical Meaning |
|---|---|---|
| **Authentication (AuthN)** | Showing your ID at check-in → "Who are you?" | Verifying the identity of a user |
| **Authorization (AuthZ)** | Your room key only opens YOUR room → "What can you do?" | Determining what the user is allowed to access |

```
Authentication answers: "Are you really Nitin?"
Authorization answers:  "Can Nitin access the admin dashboard?"
```

```mermaid
graph LR
    User["User: I am Nitin"] -->|AuthN| Server["Server: Prove it!<br/>Enter password / token"]
    Server -->|Verified ✅| AuthZ["Authorization Check:<br/>Can Nitin access /admin?"]
    AuthZ -->|Yes ✅| Resource["Admin Dashboard"]
    AuthZ -->|No ❌| Denied["403 Forbidden"]
```

> 💡 **Key Insight**: Authentication ALWAYS happens first. Authorization happens after identity is confirmed.

---

## 1️⃣ Basic Authentication

### What Is It? (Layman Terms)

Imagine every time you enter a building, the guard asks your name and password. You tell them, they verify, and let you in.  
**But** — you have to tell them your name and password **every single time** you enter. There's no memory of past visits.

### How It Works

```mermaid
sequenceDiagram
    participant Client as Browser
    participant Server as Server

    Client->>Server: GET /dashboard
    Server-->>Client: 401 Unauthorized<br/>WWW-Authenticate: Basic

    Note over Client: User enters username + password
    Note over Client: Encode: Base64("nitin:mypassword123")<br/>= "bml0aW46bXlwYXNzd29yZDEyMw=="

    Client->>Server: GET /dashboard<br/>Authorization: Basic bml0aW46bXlwYXNzd29yZDEyMw==

    Note over Server: Decode Base64 → "nitin:mypassword123"<br/>Check against DB → ✅ Match!

    Server-->>Client: 200 OK — Here's your dashboard
```

### The HTTP Header

```
GET /api/data HTTP/1.1
Authorization: Basic bml0aW46bXlwYXNzd29yZDEyMw==

This is just Base64 encoding of "nitin:mypassword123"
Base64 is NOT encryption! Anyone can decode it.
```

### Real-World Use Cases

| Use Case | Example |
|---|---|
| **Internal APIs** | Microservice A calls Microservice B within a VPN |
| **Simple webhooks** | GitHub webhook sending data to your server |
| **CLI tools** | `curl -u username:password https://api.example.com` |
| **Router admin pages** | `192.168.1.1` login page |

### ✅ Pros
- Dead simple to implement
- Supported by every HTTP client/browser ever made
- No session management needed

### ❌ Cons
- **Credentials sent with every request** — attackable if intercepted
- **Base64 ≠ encryption** — anyone can decode it
- **No logout** — browser caches the credentials
- Must use **HTTPS**, otherwise credentials are visible in plain text

> ⚠️ **NEVER use Basic Auth without HTTPS.** Base64 is trivially decoded.

---

## 2️⃣ Session-Based Authentication

### What Is It? (Layman Terms)

Now imagine the building has a **reception desk**. You show your ID **once** at the reception. They give you a **visitor badge** (Session ID). From then on, you just flash the badge — no need to repeat your ID every time.

The reception desk keeps a **logbook** (session store) that maps badge numbers to visitors. When you leave (logout), they tear up your badge entry from the logbook.

### How It Works
```mermaid
sequenceDiagram
    participant Browser
    participant Server
    participant SessionStore as Session Store

    Browser->>Server: POST /login
    Note over Server: Verify credentials

    Server->>SessionStore: Create session
    SessionStore-->>Server: sessionId=abc123

    Server-->>Browser: Set-Cookie sessionId=abc123

    Browser->>Server: GET /dashboard with cookie
    Server->>SessionStore: Validate session
    SessionStore-->>Server: User data found

    Server-->>Browser: Dashboard response

    Browser->>Server: POST /logout
    Server->>SessionStore: Delete session
    Server-->>Browser: Clear cookie
```

### Session Storage Options

| Storage | Pros | Cons |
|---|---|---|
| **In-Memory (HashMap)** | Fastest | Lost on server restart, can't scale horizontally |
| **Redis** ✅ Recommended | Fast, shared across servers, TTL support | Extra infrastructure |
| **Database** | Persistent, reliable | Slowest, DB load |
| **File System** | Simple | Can't share across servers |

### Real-World Use Cases

| Platform | How They Use Sessions |
|---|---|
| **Amazon** | Session tracks your cart, logged-in state. Stored in DynamoDB |
| **Facebook** | Session cookie keeps you logged in. Stored in TAO (distributed store) |
| **Django/Rails apps** | Default auth mechanism. Session in Redis |
| **Bank websites** | Short-lived sessions (15 min). Strict HttpOnly + Secure cookies |

### ✅ Pros
- **Server has full control** — can invalidate any session instantly (kick user out)
- **Simple for the client** — browser auto-sends cookies
- **Secure** — session data stays on the server, only the ID travels on the wire

### ❌ Cons
- **Stateful** — server must store session data (memory/Redis/DB)
- **Scalability problem** — if user hits Server A but session is on Server B → broken
  - Fix: **Sticky sessions** (route user to same server) or **centralized Redis**
- **CSRF vulnerability** — cookies are auto-sent, so attackers can trick your browser

> 💡 **Scalability Fix**: Use **Redis as a shared session store**. All servers read/write sessions from the same Redis cluster.

```mermaid
graph TD
    Browser["Browser<br/>Cookie: sessionId=abc123"]
    LB["Load Balancer"]
    S1["Server 1"]
    S2["Server 2"]
    S3["Server 3"]
    Redis[("Redis<br/>Shared Session Store<br/>abc123 → userId:42")]

    Browser --> LB
    LB --> S1
    LB --> S2
    LB --> S3
    S1 --> Redis
    S2 --> Redis
    S3 --> Redis
```

---

## 3️⃣ Cookie-Based Authentication

### What Is It? (Layman Terms)

Cookies are like **stickers on your bag at a theme park**. When you enter, they put a sticker on your bag. Every time you go to a ride, the staff checks your sticker — no need to show your ticket again.

Cookies are the **transport mechanism**. Sessions and tokens can both be stored in cookies.

### How Cookies Work

```mermaid
sequenceDiagram
    participant Browser
    participant Server

    Browser->>Server: POST /login (credentials)
    Server-->>Browser: 200 OK<br/>Set-Cookie: auth=xyz; HttpOnly; Secure; SameSite=Strict; Path=/; Max-Age=3600

    Note over Browser: Browser automatically stores the cookie

    Browser->>Server: GET /profile<br/>Cookie: auth=xyz
    Note over Server: Reads cookie, identifies user

    Server-->>Browser: 200 OK — profile data
```

### Cookie Attributes (Security Critical!)

| Attribute | What It Does | Why It Matters |
|---|---|---|
| `HttpOnly` | JS cannot access the cookie (`document.cookie` won't show it) | **Prevents XSS attacks** from stealing cookies |
| `Secure` | Cookie only sent over HTTPS | Prevents man-in-the-middle sniffing |
| `SameSite=Strict` | Cookie not sent on cross-site requests | **Prevents CSRF attacks** |
| `SameSite=Lax` | Cookie sent for top-level navigations only | Good balance of usability + security |
| `Max-Age=3600` | Cookie expires in 1 hour | Auto-logout for security |
| `Domain=.example.com` | Cookie shared across subdomains | Enables SSO across subdomains |
| `Path=/api` | Cookie only sent for `/api/*` routes | Limits exposure |

### Cookie vs LocalStorage vs SessionStorage

| Feature | Cookie | LocalStorage | SessionStorage |
|---|---|---|---|
| **Sent with requests** | ✅ Auto-sent | ❌ Manual | ❌ Manual |
| **Size limit** | 4 KB | 5 MB | 5 MB |
| **Access from JS** | When HttpOnly=false | ✅ Always | ✅ Always |
| **Expiry** | Configurable | Never | Tab close |
| **Vulnerable to XSS** | No (with HttpOnly) | ✅ Yes | ✅ Yes |
| **Vulnerable to CSRF** | ✅ Yes | No | No |

> 💡 **Best Practice**: Store auth tokens in **HttpOnly Secure cookies**, NOT in localStorage. localStorage is vulnerable to XSS (any injected script can steal your token).

### Real-World Use Cases

| Platform | Cookie Usage |
|---|---|
| **Google** | `SID`, `HSID` cookies for across-service authentication |
| **YouTube** | Shares Google's auth cookie (same domain family) |
| **Any e-commerce** | Cart persistence, logged-in state, preferences |
| **GDPR consent banners** | Track whether user accepted cookies |

---

## 4️⃣ Token-Based Authentication (JWT)

### What Is It? (Layman Terms)

Imagine instead of the reception keeping a logbook (sessions), they give you a **sealed, tamper-proof letter** that contains ALL your info — name, role, expiry time. Everywhere you go, you show this letter. People can read it and trust it because the seal proves it's genuine.

**JWT = JSON Web Token** — a self-contained token with user info + digital signature.

No one needs to call the reception (server) to verify your identity. The letter (token) itself is proof.

### JWT Structure

```
A JWT looks like this:
eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjQyLCJyb2xlIjoiYWRtaW4ifQ.sGh_kN7G7y...

It has 3 parts separated by dots:

HEADER.PAYLOAD.SIGNATURE

┌─────────────────────────────────────────────────────────────┐
│ HEADER (Algorithm + Token Type)                              │
│ {                                                            │
│   "alg": "HS256",    ← Signing algorithm                    │
│   "typ": "JWT"       ← Token type                           │
│ }                                                            │
├─────────────────────────────────────────────────────────────┤
│ PAYLOAD (Claims = User Data)                                 │
│ {                                                            │
│   "userId": 42,                                              │
│   "username": "nitin",                                       │
│   "role": "admin",                                           │
│   "exp": 1717200000,  ← Expiry timestamp                    │
│   "iat": 1717196400   ← Issued-at timestamp                 │
│ }                                                            │
├─────────────────────────────────────────────────────────────┤
│ SIGNATURE (Tamper-proof seal)                                │
│                                                              │
│ HMACSHA256(                                                  │
│   base64UrlEncode(header) + "." + base64UrlEncode(payload), │
│   SECRET_KEY   ← Only the server knows this!                │
│ )                                                            │
└─────────────────────────────────────────────────────────────┘
```

### How JWT Works

```mermaid
sequenceDiagram
    participant Browser
    participant Server
    participant DB

    Browser->>Server: POST /login<br/>{username: "nitin", password: "secret123"}
    Server->>DB: Verify credentials
    DB-->>Server: ✅ Valid user, userId=42, role=admin

    Note over Server: Create JWT:<br/>payload = {userId:42, role:"admin", exp:1hr}<br/>signature = HMAC(header+payload, SECRET_KEY)<br/>token = header.payload.signature

    Server-->>Browser: 200 OK<br/>{token: "eyJhbGci..."}

    Note over Browser: Store token<br/>(HttpOnly cookie or memory)

    Browser->>Server: GET /admin/dashboard<br/>Authorization: Bearer eyJhbGci...

    Note over Server: Verify Signature:<br/>1. Decode header + payload<br/>2. Recalculate HMAC with SECRET_KEY<br/>3. Compare with received signature<br/>Match ✅ → Token is genuine!<br/>Check exp claim → Not expired ✅

    Server-->>Browser: 200 OK — Admin Dashboard

    Note over Server: ⚠️ No DB call needed!<br/>The token itself contains all info.
```

### Access Token + Refresh Token Pattern

```mermaid
sequenceDiagram
    participant Browser
    participant Server

    Browser->>Server: POST /login (credentials)
    Server-->>Browser: {<br/>  accessToken: "eyJ..." (15 min),<br/>  refreshToken: "dGhpcyBpcyB..." (7 days)<br/>}

    Note over Browser: Uses accessToken for all API calls

    Browser->>Server: GET /api/data<br/>Authorization: Bearer eyJ...
    Server-->>Browser: 200 OK

    Note over Browser: 15 minutes later...<br/>accessToken expired!

    Browser->>Server: GET /api/data<br/>Authorization: Bearer eyJ... (expired)
    Server-->>Browser: 401 Unauthorized - Token expired

    Browser->>Server: POST /auth/refresh<br/>{refreshToken: "dGhpcyBpcyB..."}

    Note over Server: Verify refreshToken is valid<br/>and not revoked → Issue new pair

    Server-->>Browser: {<br/>  accessToken: "NEW_eyJ..." (15 min),<br/>  refreshToken: "NEW_dGhp..." (7 days)<br/>}
```

### Why Two Tokens?

| Token | Lifetime | Purpose |
|---|---|---|
| **Access Token** | Short (15 min) | Used for every API call. Short-lived = less damage if stolen |
| **Refresh Token** | Long (7 days) | Used ONLY to get new access tokens. Stored securely. Can be revoked |

> If someone steals your access token, they can only use it for 15 minutes. The refresh token is stored more securely and can be revoked server-side.

### Session vs JWT — Head to Head

| Feature | Session-Based | JWT |
|---|---|---|
| **State** | Stateful (server stores sessions) | Stateless (token has all info) |
| **Storage** | Server-side (Redis/DB) | Client-side (cookie/memory) |
| **Scalability** | Needs shared session store | ✅ Any server can verify (no shared state) |
| **Revocation** | ✅ Easy — delete from session store | ❌ Hard — can't invalidate until expiry |
| **Performance** | DB/Redis lookup per request | ✅ Just cryptographic verify (no DB) |
| **Token size** | Small (just session ID ~32 bytes) | Larger (~800 bytes with claims) |
| **Mobile-friendly** | ❌ Cookies work poorly on mobile | ✅ Token sent in header works everywhere |
| **Microservices** | Hard (shared session store) | ✅ Perfect (pass token between services) |

### Real-World Use Cases

| Platform | How They Use JWT |
|---|---|
| **Google APIs** | Access tokens for Calendar, Drive, Gmail APIs |
| **GitHub API** | Personal access tokens (similar concept) |
| **Stripe** | API tokens for payment processing |
| **Mobile apps** | Tokens stored in secure device storage |
| **Microservices** | Services pass JWTs to authenticate each other |

### ✅ Pros
- **Stateless** — server doesn't need to store anything
- **Scalable** — any server can verify the token (no shared session store)
- **Cross-domain** — works across different domains (unlike cookies)
- **Mobile-friendly** — just an HTTP header, no cookie issues
- **Microservices** — pass JWT between services, each verifies independently

### ❌ Cons
- **Can't be revoked** — once issued, valid until expiry (mitigate with short TTL + refresh tokens)
- **Larger than session ID** — ~800 bytes vs ~32 bytes
- **Sensitive data leak** — payload is Base64 encoded, not encrypted! Don't put passwords in JWT
- **Token theft** — if stolen, attacker has full access until expiry

---

## 5️⃣ OAuth 1.0

### What Is It? (Layman Terms)

Imagine you're staying at a hotel and you want to order food from a restaurant next door. But the restaurant doesn't know you or trust you.

So the hotel gives you a **signed note** that says "This person is a guest here, please deliver food to Room 42." The restaurant trusts the hotel's signature and delivers your food.

**OAuth 1.0** = A way for App A (restaurant) to trust that you're a legitimate user of App B (hotel), **without you sharing your hotel password** with the restaurant.

### Why OAuth Was Created

```
BEFORE OAuth:
  To connect your Twitter to a third-party app, you had to give
  that app YOUR Twitter username + password. 😱
  → The app could do ANYTHING with your account
  → If the app got hacked, your password was exposed

AFTER OAuth:
  You click "Login with Twitter" → Twitter asks YOU directly:
  "Do you want to allow this app to read your tweets?"
  → You say Yes → Twitter gives the app a TOKEN (not your password!)
  → The app can ONLY do what you approved
  → You can revoke access anytime from Twitter settings
```

### OAuth 1.0 Limitations
- Complex cryptographic signing for every request
- Not great for mobile apps (designed for server-to-server)
- Replaced by OAuth 2.0 in most modern systems

> 💡 OAuth 1.0 is mostly **historical**. You won't use it in new apps. But knowing it gives you context for why OAuth 2.0 was designed.

---

## 6️⃣ OAuth 2.0 (The Modern Standard)

### What Is It? (Layman Terms)

OAuth 2.0 is the improved version. Same concept — **letting apps access your data without your password** — but simpler, more flexible, and supports mobile and single-page apps.

Think of it like a **valet parking token**:
- You give the valet a **limited key** that can only start the car but can't open the trunk
- The valet can park your car but can't access your valuables
- You can take the key back anytime (revoke access)

### The 4 Roles in OAuth 2.0

| Role | Who | Example |
|---|---|---|
| **Resource Owner** | The user (YOU) | You, the person with a Google account |
| **Client** | The app wanting access | Spotify wanting to read your Google contacts |
| **Authorization Server** | Issues tokens | Google's OAuth server (accounts.google.com) |
| **Resource Server** | Holds the protected data | Google Contacts API |

### Flow 1: Authorization Code Flow (Most Common, Most Secure)

Used by: **Web apps**, **mobile apps**, any app that can keep a secret.

Real example: "Login with Google" on Spotify

```mermaid
sequenceDiagram
    participant User as User (Resource Owner)
    participant App as Spotify (Client App)
    participant Google as Google (Auth Server)
    participant API as Google Contacts API (Resource Server)

    User->>App: Click "Login with Google"

    App->>Google: Redirect to<br/>accounts.google.com/auth?<br/>response_type=code&<br/>client_id=spotify_123&<br/>redirect_uri=spotify.com/callback&<br/>scope=contacts.read

    Note over Google: Google shows consent screen:<br/>"Spotify wants to access your contacts.<br/>Allow / Deny?"

    User->>Google: Click "Allow"

    Google->>App: Redirect to<br/>spotify.com/callback?code=AUTH_CODE_xyz

    Note over App: App now has a short-lived auth code<br/>(valid for ~60 seconds)

    App->>Google: POST /token<br/>{code: AUTH_CODE_xyz,<br/>client_id: spotify_123,<br/>client_secret: spotify_SECRET,<br/>grant_type: authorization_code}

    Note over Google: Verify auth code + client secret ✅

    Google-->>App: {<br/>access_token: "ya29.abc...",<br/>refresh_token: "1//xyz...",<br/>expires_in: 3600<br/>}

    App->>API: GET /contacts<br/>Authorization: Bearer ya29.abc...

    API-->>App: Your Google Contacts data ✅
```

### Why the Extra Step? (Code → Token)

```
Q: Why not just give the access_token directly?

A: Security! The authorization code comes via the BROWSER URL
   (visible in URL bar, browser history, logs).
   
   The actual token exchange happens SERVER-TO-SERVER
   (Spotify backend → Google), which is hidden and secure.
   
   Even if someone steals the auth code, they can't exchange it
   without the client_secret (which only Spotify's server knows).
```

### Flow 2: Implicit Flow (Deprecated — but know it for interviews)

Used by: **Old single-page apps** (SPAs) that can't keep a secret.

```
Difference from Authorization Code flow:
  - Skips the code exchange step
  - Gives access_token directly in the URL
  - LESS SECURE because token is visible in browser URL bar
  - DEPRECATED in OAuth 2.1 — replaced by Auth Code + PKCE
```

### Flow 3: Client Credentials Flow (Machine-to-Machine)

Used by: **Server-to-server calls** where no user is involved.

```mermaid
sequenceDiagram
    participant ServiceA as Microservice A<br/>(e.g., Payment Service)
    participant AuthServer as Auth Server
    participant ServiceB as Microservice B<br/>(e.g., User Service)

    ServiceA->>AuthServer: POST /token<br/>{client_id: "payment_svc",<br/>client_secret: "secret_abc",<br/>grant_type: client_credentials}

    AuthServer-->>ServiceA: {access_token: "eyJ..."}

    ServiceA->>ServiceB: GET /users/42<br/>Authorization: Bearer eyJ...
    ServiceB-->>ServiceA: User data ✅
```

Real examples:
- AWS service calling another AWS service
- Your backend calling Stripe's API
- CronJob service calling your own API

### Flow 4: Resource Owner Password Credentials (Legacy, Avoid)

```
User gives username + password directly to the app.
App sends them to the auth server to get a token.

This defeats the whole purpose of OAuth! (sharing password with third party)
Only use for: your own first-party apps (e.g., Twitter's official app can do this)
```

### OAuth 2.0 + PKCE (Modern Standard for SPAs & Mobile)

```
Problem: SPAs and mobile apps can't keep a client_secret
         (JavaScript source code is visible, APK can be decompiled)

Solution: PKCE (Proof Key for Code Exchange)
          pronounced "pixy"

How it works:
  1. App generates a random string: code_verifier = "dBjftJeZ..."
  2. App creates a hash: code_challenge = SHA256(code_verifier)
  3. App sends code_challenge with the auth request
  4. When exchanging code → token, app sends code_verifier
  5. Auth server verifies: SHA256(code_verifier) == code_challenge ✅
  
Why this works:
  - Even if attacker intercepts the code_challenge, they can't
    reverse the SHA256 hash to get code_verifier
  - Only the original app has code_verifier to complete the exchange
```

### OAuth 2.0 Scopes

Scopes define **exactly what the app can access** — like giving the valet a key that only starts the engine but can't open the trunk.

```
Common Google Scopes:
  "openid"                  → basic user identity
  "profile"                 → name, profile picture
  "email"                   → email address
  "https://www.googleapis.com/auth/calendar.readonly"  → read calendar
  "https://www.googleapis.com/auth/drive.file"         → access files you've opened

Consent screen shows:
  ┌─────────────────────────────────────────────────┐
  │  Spotify wants to:                               │
  │  ✅ View your email address                      │
  │  ✅ See your personal info (name, profile pic)   │
  │  ✅ View your contacts                           │
  │                                                  │
  │  [Allow]          [Deny]                         │
  └─────────────────────────────────────────────────┘
```

### Real-World OAuth 2.0 Examples

| Service | What It Provides | Scopes |
|---|---|---|
| **Google** | "Sign in with Google" | openid, profile, email, calendar, drive |
| **GitHub** | "Sign in with GitHub" | repo, user, gist, admin:org |
| **Facebook** | "Continue with Facebook" | public_profile, email, user_friends |
| **Slack** | "Add to Slack" | chat:write, channels:read |
| **Stripe** | Payment processing | read_write on payments |

---

## 7️⃣ SSO (Single Sign-On)

### What Is It? (Layman Terms)

Imagine you work in a big office building. You badge in once at the main entrance. After that, every office inside — HR, Finance, Engineering — lets you in without another badge swipe.

**SSO = Log in once, access all connected apps without logging in again.**

### How SSO Works

```mermaid
sequenceDiagram
    participant User
    participant App1 as Gmail
    participant App2 as YouTube
    participant IDP as Google SSO<br/>(Identity Provider)

    User->>App1: Visit gmail.com
    App1->>IDP: Not logged in → Redirect to SSO
    IDP-->>User: Show login page
    User->>IDP: Enter credentials
    IDP-->>User: Set SSO cookie on google.com domain
    IDP->>App1: Redirect back with auth token
    App1-->>User: ✅ Gmail inbox

    Note over User: Later, visit youtube.com...

    User->>App2: Visit youtube.com
    App2->>IDP: Check SSO status
    Note over IDP: SSO cookie exists → Already authenticated!
    IDP->>App2: Token for YouTube
    App2-->>User: ✅ YouTube home — NO login needed!
```

### SSO Providers

| Provider | Protocol | Used By |
|---|---|---|
| **Google Workspace** | SAML 2.0 / OIDC | Gmail, Drive, Calendar, YouTube |
| **Okta** | SAML 2.0 / OIDC | Enterprise apps |
| **Microsoft Azure AD** | SAML 2.0 / OIDC | Office 365, Teams, Azure |
| **Auth0** | OIDC / OAuth 2.0 | Startups, SaaS apps |
| **Keycloak** | OIDC / SAML | Open-source, self-hosted |

### SSO Protocols

| Protocol | Full Name | Best For |
|---|---|---|
| **SAML 2.0** | Security Assertion Markup Language | Enterprise apps (XML-based) |
| **OIDC** | OpenID Connect | Modern apps (built on OAuth 2.0, JSON-based) |

> 💡 **OIDC (OpenID Connect) = OAuth 2.0 + Identity Layer.** OAuth 2.0 only gives you authorization (access tokens). OIDC adds authentication (ID tokens) — it tells you WHO the user is.

---

## 8️⃣ API Key Authentication

### What Is It? (Layman Terms)

An API Key is like a **library card number**. It identifies which app is making the request, but it doesn't tell you who the person is. It's used for **app identification**, not user authentication.

### How It Works

```
GET /api/weather?city=Delhi
X-API-Key: sk_live_abc123xyz789

or

GET /api/weather?city=Delhi&api_key=sk_live_abc123xyz789
```

### Real-World Use Cases

| Service | API Key Usage |
|---|---|
| **Google Maps** | `AIzaSyD...` in every maps embed |
| **OpenWeatherMap** | `appid=your_key` for weather data |
| **Stripe** | `sk_live_...` for payment API (secret key) |
| **SendGrid** | API key for sending emails |

### ✅ Pros
- Dead simple — just a string in the header
- Good for rate limiting per app

### ❌ Cons
- **Not user-specific** — identifies the app, not the user
- **Hard to rotate** — if leaked, all clients need updating
- **No fine-grained permissions** — all-or-nothing access

---

## 📊 Comparison Table

| Method | Stateful? | Where Stored | Scalability | Security | Best For |
|---|---|---|---|---|---|
| **Basic Auth** | No | Sent every request | ✅ Stateless | ⚠️ Low | Internal APIs |
| **Session** | ✅ Yes | Server (Redis/DB) | ⚠️ Needs shared store | ✅ High | Traditional web apps |
| **Cookie** | Transport | Browser | - | ✅ With HttpOnly+Secure | Session/token transport |
| **JWT** | No | Client | ✅ Stateless | ✅ Good | Mobile, microservices, SPAs |
| **OAuth 2.0** | No | Auth server + client | ✅ Delegated | ✅ Highest | Third-party access |
| **API Key** | No | Client | ✅ Stateless | ⚠️ Medium | Server-to-server, public APIs |
| **SSO** | ✅ Yes | Identity provider | ✅ Centralized | ✅ High | Enterprise, multi-app |

---

## 🎯 Which One to Use When?

```mermaid
graph TD
    Start{"What are you building?"}
    Start -->|"Traditional web app<br/>(Django, Rails)"| Session["✅ Session + Cookie"]
    Start -->|"SPA or Mobile app"| JWT["✅ JWT + Refresh Token"]
    Start -->|"Need 'Login with Google'"| OAuth["✅ OAuth 2.0 + OIDC"]
    Start -->|"Microservice to microservice"| ClientCred["✅ OAuth 2.0 Client Credentials<br/>or JWT"]
    Start -->|"Public API for developers"| APIKey["✅ API Key + Rate Limiting"]
    Start -->|"Enterprise with many apps"| SSO["✅ SSO with SAML/OIDC"]
    Start -->|"Simple internal tool"| Basic["✅ Basic Auth over HTTPS"]
```

### Decision Cheat Sheet

| Scenario | Use This |
|---|---|
| "I'm building a startup web app" | **JWT + HttpOnly cookies** |
| "I need social login" | **OAuth 2.0 + OIDC** (Login with Google/GitHub) |
| "I have 50 internal enterprise apps" | **SSO with Okta/Auth0** |
| "Microservices need to talk to each other" | **JWT or OAuth 2.0 Client Credentials** |
| "I'm building a public REST API" | **API Key** for identification + **OAuth 2.0** for user-level access |
| "Simple admin dashboard, 5 users" | **Session-based auth** with bcrypt passwords |
| "Quick webhook receiver" | **Basic Auth + HTTPS** or **API Key** |

---

## 💡 Interview Tips

### Clarifying Questions to Ask
1. "Is this a first-party or third-party authentication?" → Decides OAuth vs Sessions
2. "Web app, mobile, or API?" → Cookies vs Bearer tokens
3. "How many services need to share auth state?" → SSO vs standalone
4. "Do we need fine-grained permissions?" → Role-based vs scope-based

### What Impresses Interviewers
- ✅ Explaining **why** JWT is stateless and how signature verification works
- ✅ Knowing the access token + refresh token pattern
- ✅ Explaining CSRF vs XSS and how cookies mitigate each
- ✅ Knowing OAuth 2.0 Authorization Code flow with PKCE
- ✅ Discussing where to store tokens (HttpOnly cookie > localStorage)
- ✅ Explaining SSO and how it reduces friction in enterprise

### Common Mistakes
- ❌ Saying "JWT is more secure than sessions" (not always true — depends on context)
- ❌ Storing JWT in localStorage (XSS vulnerability!)
- ❌ Not mentioning refresh tokens when discussing JWT
- ❌ Confusing authentication with authorization
- ❌ Not knowing that OAuth 2.0 is for AUTHORIZATION, OIDC adds AUTHENTICATION

---

## 🎯 Quick Summary Card

```
┌──────────────────────────────────────────────────────────────────┐
│            AUTH CHEAT SHEET FOR INTERVIEWS                        │
├──────────────────────────────────────────────────────────────────┤
│ Basic Auth:   username:pass in every request (Base64, not secure)│
│ Session:      Server stores state, client gets session ID cookie │
│ JWT:          Self-contained token, stateless, signed by server  │
│ OAuth 2.0:    Delegate access without sharing password           │
│ OIDC:         OAuth 2.0 + "who is this user?" (ID token)        │
│ SSO:          Login once, access all connected apps              │
│ API Key:      Identifies the app, not the user                   │
│                                                                  │
│ KEY RULE: Authentication first, Authorization second.            │
│ STORE TOKENS: HttpOnly + Secure cookie (never localStorage!)    │
│ JWT REVOCATION: Short TTL (15 min) + Refresh Token (7 days)     │
└──────────────────────────────────────────────────────────────────┘
```

---

*Previous: [02_Pastebin.md](./02_Pastebin.md) | Next: [04_Rate_Limiter.md]*
