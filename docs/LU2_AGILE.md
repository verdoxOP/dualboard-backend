# LU2: Agile Development — DualBoard

## 1. Business Context

### 1.1 Problem Statement
Students collaborating on group projects need a way to brainstorm visually in real time.
Standard text chats and screen sharing are insufficient for co-creating technical diagrams,
mind maps, and freehand sketches together. There is no lightweight, purpose-built tool
that lets students instantly create a shared canvas, draw together, and persist their work
for later review.

### 1.2 Vision Statement
For **students collaborating on group projects** who **need a shared visual workspace
for brainstorming**, DualBoard is a **real-time collaborative whiteboard** that **lets
users create private rooms, draw together with zero latency, and save their work**.
Unlike **screen sharing or text-based collaboration tools**, our product **provides a
persistent, bi-directional drawing canvas purpose-built for visual collaboration**.

### 1.3 Stakeholders
| Stakeholder | Role | Needs / Interests | Influence |
|---|---|---|---|
| Students (primary users) | End users | Fast, intuitive real-time drawing with classmates; ability to save and revisit boards | High — product is built for them |
| Teacher / Assessor | Academic evaluator | Documented evidence of architectural decisions, Agile workflow, CI/CD, testing (LU1–LU4) | Very High — defines success criteria |
| Developer (you) | Builder & learner | Demonstrate full-stack competency; learn WebSockets, OAuth, CI/CD | High — makes all implementation decisions |

### 1.4 Business Goals
- **Goal 1:** Deliver a working MVP that demonstrates real-time collaborative drawing within a 2-week sprint.
- **Goal 2:** Produce documented evidence of Agile practices, architectural justification, CI/CD automation, and test coverage aligned to LU1–LU4.
- **Success metric:** A user can create a room, share a link, and draw in real time with another user — with the canvas state persisted to PostgreSQL.

---

## 2. Project Case

### 2.1 Scope

**IN scope (MVP — Sprint 1):**
- User authentication via OAuth 2.0
- Create and join private rooms (unique room codes)
- Real-time freehand drawing synced via WebSockets/STOMP
- Basic shape tools (rectangle, circle, line)
- Save canvas state to PostgreSQL
- Load/restore canvas state on rejoin

**OUT of scope (future iterations):**
- Text tool / sticky notes
- Color picker / brush size customization
- Export to image/PDF
- User presence indicators / cursors
- Undo/redo history
- Mobile-optimized layout

### 2.2 Constraints
- **Time:** ~2 weeks for MVP delivery
- **Team size:** Solo developer
- **Tech stack:** Prescribed — Next.js, Spring Boot, PostgreSQL, OAuth 2.0
- **Infrastructure:** Local development; deployment target TBD (e.g., Docker, cloud)

### 2.3 Assumptions
- Users have a modern browser with WebSocket support
- PostgreSQL is available locally or via a managed service
- OAuth provider (e.g., GitHub or Google) is available and free-tier sufficient
- Two concurrent users per room is sufficient for MVP demonstration

### 2.4 Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **Scope creep** — adding polish features before core works | High | High | Strict MVP backlog; no new features until room+draw+save works end-to-end |
| **State synchronization conflicts** — two users draw simultaneously in the same area | Medium | High | Treat each stroke as an independent event (append-only model); defer conflict resolution to post-MVP |
| **Reconnection data loss** — user loses connection and misses drawing events | Medium | Medium | On reconnect, fetch full canvas state from server/DB rather than replaying missed events |
| **Unfamiliar tech** — first time with WebSockets/STOMP or OAuth integration | High | Medium | Timebox learning spikes; build isolated prototypes before integrating |

---

## 3. Epics & User Stories

### Epic 1: Authentication & Identity
> *As a student, I need to securely log in so that my boards are associated with my account.*

| ID | User Story | Acceptance Criteria | Story Points |
|---|---|---|---|
| US-1.1 | As a user, I want to log in with my GitHub/Google account so I don't need to create a new password | - OAuth 2.0 login flow completes successfully<br>- User record created in PostgreSQL on first login<br>- JWT/session issued on success | TBD |
| US-1.2 | As a user, I want to log out so that my session is terminated | - Session/token invalidated<br>- User redirected to landing page | TBD |

### Epic 2: Room Management
> *As a student, I need to create and join private rooms so I can collaborate with specific classmates.*

| ID | User Story | Acceptance Criteria | Story Points |
|---|---|---|---|
| US-2.1 | As a user, I want to create a new whiteboard room so I have a private space to draw | - Room created with a unique code<br>- Room persisted in PostgreSQL<br>- Creator automatically joins the room | TBD |
| US-2.2 | As a user, I want to join an existing room by entering a code so I can collaborate with classmates | - Valid code connects user to the room's WebSocket channel<br>- Invalid code shows an error message | TBD |
| US-2.3 | As a user, I want to see a list of my previously created/joined rooms so I can return to them | - Rooms listed on dashboard after login<br>- Each room shows name, last modified date | TBD |

### Epic 3: Real-time Collaborative Drawing
> *As a student, I need to draw on a shared canvas and see my classmates' drawings in real time.*

| ID | User Story | Acceptance Criteria | Story Points |
|---|---|---|---|
| US-3.1 | As a user, I want to freehand draw on the canvas so I can sketch ideas | - Stroke appears on my canvas as I draw<br>- Stroke data sent to server via WebSocket | TBD |
| US-3.2 | As a user, I want to see other users' drawings appear in real time so we can collaborate | - Incoming strokes rendered on canvas within <200ms<br>- Drawing from multiple users does not conflict | TBD |
| US-3.3 | As a user, I want to place basic shapes (rectangle, circle, line) so I can create diagrams | - Shape rendered on canvas on placement<br>- Shape synced to other users in real time | TBD |

### Epic 4: Canvas Persistence
> *As a student, I need my whiteboard saved so I can come back to it later.*

| ID | User Story | Acceptance Criteria | Story Points |
|---|---|---|---|
| US-4.1 | As a user, I want the canvas state to be saved automatically so I don't lose work | - Canvas state persisted to PostgreSQL periodically or on each stroke<br>- No user action required to save | TBD |
| US-4.2 | As a user, I want to see the existing canvas when I rejoin a room so I can continue where I left off | - Full canvas state loaded from DB on room join<br>- Previously drawn strokes/shapes rendered correctly | TBD |

---

## 4. Agile Workflow

### 4.1 Methodology Choice & Justification

**Choice: Scrumban (Hybrid)**

Pure Scrum is ceremony-heavy for a solo developer — Daily Standups, Sprint Reviews, and
distinct Scrum roles (Scrum Master, Product Owner, Dev Team) add overhead with no
collaboration benefit when you are all three people. However, pure Kanban risks "infinite
polishing" — without time-boxed pressure, there is no forcing function to ship.

**Scrumban** combines the best of both:
- **From Kanban:** A visual board with columns (To Do → Doing → Testing → Done) and
  WIP limits (max 2 items in "Doing" at once) to prevent context-switching.
- **From Scrum:** Fixed 1-week iterations with a mini-review at the end of each, providing
  the time-boxed pressure needed to hit a 2-week deadline. This also creates a natural
  checkpoint to re-prioritize if a learning spike takes longer than expected.

### 4.2 Sprint / Iteration Structure

**Two 1-week iterations.**

| Iteration | Focus | Stories |
|---|---|---|
| **Iteration 1 — The Foundation** | OAuth setup, PostgreSQL schema via Hibernate, Room Management CRUD | US-1.1, US-2.1, US-2.2, US-2.3, US-1.2 |
| **Iteration 2 — The Value** | Real-time WebSocket/STOMP drawing sync, canvas persistence (save/load) | US-3.1, US-3.2, US-3.3, US-4.1, US-4.2 |

**Trade-off justification:** A single 2-week block is risky — if OAuth blocks progress in
week one, there is no checkpoint to course-correct. Two 1-week iterations allow a "reset"
at the halfway mark: re-prioritize the backlog, drop low-value stories if needed, and
ensure a functional (if minimal) MVP exists to demonstrate to the assessor even if polish
features are incomplete.

### 4.3 Definition of Done

A user story is **Done** when ALL of the following are true:

1. **Acceptance Criteria Met:** Every criterion listed in the story is demonstrably satisfied.
2. **Code Quality:** Lombok is used for boilerplate reduction in Spring Boot entities/services;
   Next.js components are modular and follow project conventions.
3. **Testing:** Unit tests pass (JUnit/Mockito for Spring Boot, Jest for React) with ≥70%
   code coverage on the changed code.
4. **CI Green:** Code is pushed to `main` and the GitHub Actions CI pipeline passes
   (build, lint, test).
5. **Documentation:** API endpoints are documented (Swagger/OpenAPI for REST; message
   format documented for WebSocket channels).
6. **Deployable:** The feature is accessible in a production-like environment (Docker
   container or local production build).

### 4.4 Definition of Ready

A user story is **Ready** to move from "To Do" to "Doing" when:

1. **Clear Acceptance Criteria:** Success is unambiguous (e.g., "User can log in via Google
   and see their username on the dashboard").
2. **Backend/Frontend Contract Defined:** The JSON request/response shape for REST
   endpoints, or the WebSocket message format, is sketched out before coding begins.
3. **Technical Spike Completed:** If the story involves unfamiliar technology (e.g., STOMP
   handshake, OAuth callback flow), a timeboxed spike (≤30 min) has been done to confirm
   feasibility and identify integration risks.
4. **Dependencies Identified:** Any blocking stories are already Done (e.g., US-3.1 cannot
   start until US-2.2 is complete, because you need a room to draw in).

---

## 5. Product Backlog
> *Prioritized backlog — order reflects implementation dependencies and value delivery*

### Iteration 1 — The Foundation (Week 1)
| Priority | Story ID | Story | Status |
|---|---|---|---|
| 1 | US-1.1 | OAuth login (GitHub/Google) | To Do |
| 2 | US-2.1 | Create a new whiteboard room | To Do |
| 3 | US-2.2 | Join an existing room by code | To Do |
| 4 | US-2.3 | Room list / dashboard | To Do |
| 5 | US-1.2 | Logout | To Do |

### Iteration 2 — The Value (Week 2)
| Priority | Story ID | Story | Status |
|---|---|---|---|
| 6 | US-3.1 | Freehand drawing (local canvas) | To Do |
| 7 | US-3.2 | Real-time drawing sync via WebSocket | To Do |
| 8 | US-4.1 | Auto-save canvas state to PostgreSQL | To Do |
| 9 | US-4.2 | Load canvas state on room rejoin | To Do |
| 10 | US-3.3 | Basic shapes (rectangle, circle, line) | To Do |

---

## 6. Technical Risk Mitigation: State Synchronization (LU1)

**Risk:** If User A draws a circle and User B joins 5 seconds later, does User B see the circle?

**Mitigation strategy — "Full State on Join":**
1. Every stroke/shape is an **independent event** appended to a `drawing_events` table in PostgreSQL.
2. When a user joins a room, the backend fetches **all events for that room** from the DB and sends
   them to the joining client in a single payload.
3. After the initial load, new events arrive via the WebSocket subscription in real time.
4. This avoids the complexity of event replay or CRDT-based conflict resolution for the MVP.

This strategy directly informs how we design the Hibernate entity model (LU1) and what
we test for reliability (LU4).

---

*Document version: 0.3 — Agile workflow, iterations, and risk mitigation defined*
*Last updated: 2026-03-06*





