# Smart Parking Slot Reallocation System

A multi-tenant system that dynamically reallocates reserved parking slots when
the owner is absent, arrives on a different vehicle type, or swaps with
another user — across colleges, shopping complexes, and apartment complexes.

## Project structure

```
parking-system/
├── backend/     Spring Boot 3 + PostgreSQL + Flyway + JWT auth
└── frontend/    React + TypeScript (Vite)
```

## Tech stack

- **Backend:** Java 21, Spring Boot 3, Spring Security (JWT), Spring Data JPA, Flyway
- **Database:** PostgreSQL 16
- **Frontend:** React 18 + TypeScript, Vite, react-router, axios
- **Containerization:** Docker (multi-stage build), Docker Compose for local dev

## User flow

```
Sign Up (name, email, password)
   ↓
Email Verification (OTP code — logged to console in dev, emailed in production)
   ↓
Sign In
   ↓
Organization Selection
   ├── Join Existing Organization → pick org + role (e.g. FACULTY/STAFF/RESIDENT/CUSTOMER) → User Dashboard
   └── Create New Organization → name + type (COLLEGE/SHOPPING_COMPLEX/APARTMENT) → becomes ADMIN → Admin Dashboard
           ↓
       Admin configures Zones + Parking Slots (no slot exists until an admin creates one)
           ↓
       Any org member: view available slots → claim one as a permanent slot
           ↓
       Later: release it, or exchange it with another user (one-time / weekly / date-range)
           ↓
       Temporary reallocation when needed: report absence or vehicle change →
       slot is released → first eligible user to claim it wins → auto-restored on expiry
```

There is **no seed data**. A fresh deployment starts with zero organizations,
users, zones, slots, events, claims, or swaps — everything is created through
the flow above.

## Known scope simplifications (documented on purpose)

These are deliberate simplifications appropriate for an academic project —
worth mentioning in your report so they read as design decisions, not gaps:

- **No separate "visitor" role.** The apartment reallocation policy treats
  "visitor pool" as "other residents in the same organization," since the
  system doesn't model unauthenticated visitors.
- **Swap effect is immediate, not calendar-aware.** Accepting a swap
  exchanges the two slots' current holder right away. `WEEKLY` swaps are
  treated as an ongoing arrangement (no day-by-day toggling); `DATE_RANGE`
  and `ONE_TIME` swaps are auto-reverted by the scheduled job once their
  dates pass. A production version would resolve "who holds this slot
  today" per-day instead.
- **First-come-first-served claims, no proximity ranking.** The first
  eligible claim on a still-available slot wins immediately. A destination
  zone and its proximity rank are still recorded on each claim as
  informational data, but they never decide who gets the slot.
- **Joining an organization is self-service, not approval-gated.** A user
  picks their role from the roles valid for that organization's type
  (enforced server-side) and joins immediately — there's no admin approval
  step, to keep the flow simple for this assignment.

## Email verification in local development

By default (`EMAIL_MODE=LOG`, no configuration needed), the backend prints
the verification code to its console/logs instead of sending a real email —
this is what you'll use for local development and your demo video. To send
real emails, set `EMAIL_MODE=SMTP` plus `SPRING_MAIL_HOST`,
`SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` (and `EMAIL_FROM`) as
environment variables. Verification is never faked or skipped in the
application logic — only *where the code is delivered* changes.

## Local development (Windows + WSL2 + Docker Desktop)

### 1. Backend + database

From `backend/`:

```bash
docker compose up --build
```

This starts Postgres and the Spring Boot app together. Flyway runs the
schema migrations automatically on startup — no seed data. The API is then
available at `http://localhost:8080` with an empty database: sign up, verify
your email (check the backend logs for the code), log in, and either create
a new organization or join one that already exists.

To run the backend outside Docker (e.g. from IntelliJ) instead, start just
the database:

```bash
docker compose up db
```

then run `ParkingSystemApplication` directly — it defaults to
`localhost:5432` when no environment variables are set.

### 2. Frontend

From `frontend/`:

```bash
pnpm install    # or npm install
pnpm dev        # or npm run dev
```

The app runs at `http://localhost:5173` and expects the API at
`http://localhost:8080` (see `.env.example` — copy to `.env` to override).

## Deploying to Render

### Backend

1. Push this repository to GitHub.
2. In Render: **New → Web Service** → connect the repo → set **Root
   Directory** to `backend` → Render detects the `Dockerfile` automatically.
3. In Render: **New → PostgreSQL** → create a free instance → copy its
   internal connection string.
4. On the Web Service, add these environment variables:
   - `SPRING_DATASOURCE_URL` (from the Render Postgres instance)
   - `SPRING_DATASOURCE_USERNAME`
   - `SPRING_DATASOURCE_PASSWORD`
   - `JWT_SECRET` (a long random string — do not reuse the dev default)
   - `CORS_ALLOWED_ORIGINS` (your deployed frontend URL, once you have it)
   - `EMAIL_MODE` — leave as `LOG` if you don't have SMTP credentials yet; verification
     codes will then appear in Render's log stream (Logs tab) instead of a real inbox,
     which is fine for grading/demo purposes. Set to `SMTP` with `SPRING_MAIL_*` vars
     to send real emails.
5. Deploy. Flyway will run the migrations against the managed database on
   first boot.

### Frontend

Deploy `frontend/` to Vercel, Netlify, or Render Static Site, with
`VITE_API_BASE_URL` set to your backend's Render URL. Then go back and
update `CORS_ALLOWED_ORIGINS` on the backend to match.

## API documentation

See `backend/API.md` for the full endpoint reference with example requests.

## Cloud architecture

```
Client (browser) → Frontend (Vercel/Render Static) → Backend (Render, Docker) → Render PostgreSQL (managed)
                                                              ↑
                                                    Scheduled job (auto-expiry),
                                                    running inside the same Spring Boot app
```
