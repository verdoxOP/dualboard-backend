# LU1: Full-Stack Architecture — DualBoard

## 1. Entity Model (Hibernate / PostgreSQL)

### 1.1 Entity Relationship Diagram (Text)

```
┌──────────────┐       ┌──────────────────┐       ┌──────────────────┐
│     User     │       │       Room       │       │  DrawingEvent    │
├──────────────┤       ├──────────────────┤       ├──────────────────┤
│ id (UUID) PK │──┐    │ id (UUID) PK     │──────▶│ id (Long) PK     │
│ email        │  ├───▶│ name             │       │ room_id (FK)     │
│ displayName  │  │    │ inviteCode (6ch) │       │ user_id (FK)     │
│ avatarUrl    │  │    │ createdAt        │       │ type (ENUM)      │
│ provider     │  │    │ owner_id (FK)    │       │ data (JSONB)     │
│ providerId   │  │    └──────────────────┘       │ timestamp        │
└──────────────┘  │           ▲                   └──────────────────┘
                  │           │
                  │    ┌──────┴───────┐
                  └───▶│ room_members │ (Join Table)
                       │ user_id (FK) │
                       │ room_id (FK) │
                       └──────────────┘
```

### 1.2 Entity Details

#### User
| Field | Type | Constraints | Source |
|---|---|---|---|
| `id` | UUID | PK, generated | Application-generated |
| `email` | String | NOT NULL, UNIQUE | OAuth provider |
| `displayName` | String | NOT NULL | OAuth provider |
| `avatarUrl` | String | NULLABLE | OAuth provider |
| `provider` | Enum (GOOGLE, GITHUB) | NOT NULL | Application-set based on OAuth flow |
| `providerId` | String | NOT NULL | OAuth provider (their unique user ID) |

**Relationships:**
- **One-to-Many → Room** (as owner): A user can own many rooms.
- **Many-to-Many → Room** (as member): A user can be a member of many rooms; a room has many members.

**Design justification:**
- `provider` + `providerId` together uniquely identify a user from an OAuth provider. This allows
  the same person to potentially have accounts via different providers (though MVP treats them as
  separate users).
- UUID for `id` rather than auto-increment: non-guessable, safe to expose in URLs/JWTs.

#### Room
| Field | Type | Constraints | Source |
|---|---|---|---|
| `id` | UUID | PK, generated | Application-generated |
| `name` | String | NOT NULL | User input on creation |
| `inviteCode` | String (6 chars) | NOT NULL, UNIQUE | Application-generated (random alphanumeric) |
| `createdAt` | Timestamp | NOT NULL | Application-generated |
| `owner_id` | UUID (FK → User) | NOT NULL | Set to creating user's ID |

**Relationships:**
- **Many-to-One → User** (owner): Each room has exactly one owner.
- **Many-to-Many → User** (members): A room can have many members (including the owner).
- **One-to-Many → DrawingEvent**: A room contains many drawing events.

**Design justification:**
- `inviteCode` is a short, human-shareable code (e.g., "AX72Z") for joining rooms — easier than
  sharing a full UUID.
- UUID for `id`: non-guessable, prevents enumeration attacks on room URLs.

#### DrawingEvent
| Field | Type | Constraints | Source |
|---|---|---|---|
| `id` | Long | PK, auto-generated | Database sequence |
| `room_id` | UUID (FK → Room) | NOT NULL | From WebSocket destination |
| `user_id` | UUID (FK → User) | NOT NULL | From authenticated session |
| `type` | Enum (STROKE, SHAPE) | NOT NULL | From WebSocket message |
| `data` | JSONB | NOT NULL | From WebSocket message payload |
| `timestamp` | Timestamp | NOT NULL | Server-assigned on receipt |

**Design justification:**
- `Long` for `id` (not UUID): Drawing events are internal, never exposed in URLs, and a
  sequential ID makes ordering trivial and indexing efficient.
- `JSONB` for `data`: Different event types have different shapes (a STROKE has `points[]`,
  `color`, `brushSize`; a SHAPE might have `shapeType`, `x`, `y`, `width`, `height`). JSONB
  lets us store these flexibly without a separate table per shape type. PostgreSQL can also
  index inside JSONB if needed later.
- `timestamp` is **server-assigned**, not client-provided. This prevents clock-skew issues
  between users and ensures consistent ordering.

---

## 2. REST API Contract (Iteration 1)

> All endpoints require a valid OAuth 2.0 JWT in the `Authorization: Bearer <token>` header.
> All responses use `application/json`.

### 2.1 Authentication

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/oauth2/authorization/{provider}` | Initiates OAuth login (Spring Security handles this) | Public |
| GET | `/api/v1/auth/me` | Returns the currently authenticated user's profile | Authenticated |
| POST | `/api/v1/auth/logout` | Invalidates the session/token | Authenticated |

#### `GET /api/v1/auth/me`
**Response (200):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "student@university.edu",
  "displayName": "Alex Student",
  "avatarUrl": "https://avatars.githubusercontent.com/u/12345",
  "provider": "GITHUB"
}
```

### 2.2 Room Management

| Method | Path | Description | Auth |
|---|---|---|---|
| POST | `/api/v1/rooms` | Create a new room | Authenticated |
| POST | `/api/v1/rooms/join/{inviteCode}` | Join a room by invite code | Authenticated |
| GET | `/api/v1/rooms` | List rooms the user owns or is a member of | Authenticated |
| GET | `/api/v1/rooms/{id}` | Get room details | Authenticated + Room Member |
| GET | `/api/v1/rooms/{id}/history` | Get all drawing events for a room | Authenticated + Room Member |

#### `POST /api/v1/rooms`
**Request:**
```json
{
  "name": "Project Brainstorm"
}
```
**Response (201):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Project Brainstorm",
  "inviteCode": "AX72Z",
  "createdAt": "2026-03-06T14:30:00Z"
}
```

#### `POST /api/v1/rooms/join/{inviteCode}`
**Response (200):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Project Brainstorm",
  "inviteCode": "AX72Z",
  "createdAt": "2026-03-06T14:30:00Z"
}
```
**Response (404):** `{ "error": "Room not found" }`
**Response (409):** `{ "error": "Already a member of this room" }`

#### `GET /api/v1/rooms`
**Response (200):**
```json
[
  {
    "id": "uuid-1",
    "name": "Project Brainstorm",
    "inviteCode": "AX72Z",
    "createdAt": "2026-03-06T14:30:00Z",
    "isOwner": true
  }
]
```

#### `GET /api/v1/rooms/{id}/history`
**Response (200):**
```json
[
  {
    "id": 1,
    "userId": "uuid-user-1",
    "type": "STROKE",
    "data": {
      "color": "#FF5733",
      "brushSize": 5,
      "points": [{"x": 10, "y": 10}, {"x": 15, "y": 22}]
    },
    "timestamp": "2026-03-06T14:35:00Z"
  }
]
```

---

## 3. WebSocket / STOMP Contract (Iteration 2)

### 3.1 Connection
- **Endpoint:** `ws://localhost:8080/ws` (STOMP handshake)
- **Authentication:** JWT passed as query parameter or via STOMP `CONNECT` headers.

### 3.2 Destinations

| Direction | Destination | Purpose |
|---|---|---|
| Client → Server | `/app/draw/{roomId}` | Send a completed stroke/shape to the server |
| Server → Client | `/topic/room/{roomId}` | Broadcast drawing events to all room subscribers |
| Client → Server | `/app/draw/{roomId}/preview` | Send in-progress stroke preview (NOT persisted) |
| Server → Client | `/topic/room/{roomId}/preview` | Broadcast stroke previews for real-time feel |

### 3.3 Message Format: Final Stroke (Persisted)

Sent on `mouseup` / `pointerup`. This is saved to the `drawing_events` table.

```json
{
  "type": "STROKE",
  "payload": {
    "color": "#FF5733",
    "brushSize": 5,
    "points": [
      {"x": 10, "y": 10},
      {"x": 15, "y": 22},
      {"x": 25, "y": 30}
    ]
  }
}
```

> Note: `senderName` and `timestamp` are **not sent by the client**. The server enriches
> the message with the authenticated user's info and a server-side timestamp before
> broadcasting and persisting. This prevents spoofing.

**Broadcast message (what other clients receive):**
```json
{
  "id": 42,
  "userId": "uuid-of-sender",
  "senderName": "Alex",
  "type": "STROKE",
  "payload": {
    "color": "#FF5733",
    "brushSize": 5,
    "points": [
      {"x": 10, "y": 10},
      {"x": 15, "y": 22},
      {"x": 25, "y": 30}
    ]
  },
  "timestamp": "2026-03-06T14:35:00Z"
}
```

### 3.4 Message Format: Shape (Persisted)

```json
{
  "type": "SHAPE",
  "payload": {
    "shapeType": "RECTANGLE",
    "x": 100,
    "y": 150,
    "width": 200,
    "height": 100,
    "color": "#3366FF",
    "strokeWidth": 2
  }
}
```

### 3.5 Message Format: Preview (NOT Persisted)

Sent periodically during drawing (e.g., every 50ms or every 5 points) to give real-time
feedback. These are **broadcast but never saved to the database**.

```json
{
  "type": "PREVIEW",
  "payload": {
    "color": "#FF5733",
    "brushSize": 5,
    "points": [
      {"x": 10, "y": 10},
      {"x": 15, "y": 22}
    ]
  }
}
```

### 3.6 Performance Strategy: Preview vs. Final

| Event | Trigger | Persisted? | Purpose |
|---|---|---|---|
| Preview | Every ~50ms or every 5 points during drawing | No | Real-time visual feedback |
| Final Stroke | `mouseup` / `pointerup` | Yes (JSONB in DB) | Permanent canvas state |

This keeps the database from growing uncontrollably while maintaining a snappy UI.
A user drawing a 3-second line might generate 60 preview messages but only 1 persisted event.

---

## 4. Architectural Decision Records (ADRs)

### ADR-1: UUID for User and Room IDs
- **Decision:** Use UUID v4 for User and Room primary keys.
- **Rationale:** Non-guessable and safe to expose in URLs, API responses, and JWTs. Prevents
  enumeration attacks (e.g., `/api/v1/rooms/1`, `/api/v1/rooms/2`...).
- **Trade-off:** Slightly larger than auto-increment integers; not human-readable. Acceptable
  for this use case.

### ADR-2: JSONB for DrawingEvent Data
- **Decision:** Store drawing payload as a JSONB column rather than normalized relational columns.
- **Rationale:** Different event types (STROKE, SHAPE) have different data shapes. JSONB avoids
  a complex inheritance hierarchy in the database and allows flexible querying via PostgreSQL
  JSON operators if needed.
- **Trade-off:** Less type-safety at the database level; validation must happen in the application
  layer. Acceptable because Hibernate + DTOs enforce structure before persistence.

### ADR-3: Server-Assigned Timestamps
- **Decision:** Timestamps on DrawingEvents are assigned by the server, not the client.
- **Rationale:** Client clocks can be skewed or manipulated. Server timestamps ensure consistent
  ordering of events across all users.

### ADR-4: Preview/Final Split for Drawing Messages
- **Decision:** Separate "preview" (not persisted) and "final" (persisted) WebSocket message channels.
- **Rationale:** Persisting every mouse-move event would cause massive DB growth (~60 writes per
  3-second stroke). Previews provide real-time feedback without storage cost. Only the completed
  stroke is persisted.

### ADR-5: RoomMembership as a Full Entity (not a plain join table)
- **Decision:** Model the User↔Room many-to-many as a first-class `RoomMembership` entity with its
  own fields (`role`, `joinedAt`), rather than using `@ManyToMany` with a plain join table.
- **Rationale:** Even for MVP, we need to distinguish Owner (can delete room) from Member (can only
  draw). A plain join table makes adding `role` or `joinedAt` later a full refactoring effort.
- **Trade-off:** Slightly more Hibernate mapping complexity (two `@OneToMany` relationships instead
  of one `@ManyToMany`), but follows the Open-Closed Principle — schema is open for extension,
  closed for modification.

### ADR-6: Full History Replay (no snapshotting for MVP)
- **Decision:** The `/history` endpoint returns all drawing events for a room without pagination
  or snapshotting.
- **Rationale:** Implementing canvas snapshotting is an engineering rabbit hole that could consume
  the entire second iteration. For MVP scope, we anticipate <2,000 events per room at ~200 bytes
  each, yielding a payload <0.5MB — acceptable for modern networks.
- **Future proofing:** If the app scaled, we would implement "Canvas Flattening" — merging old
  strokes into a single background image and only replaying events after the last snapshot.

### ADR-7: @PreAuthorize with Custom SecurityService for Room Authorization
- **Decision:** Use Spring Security's `@PreAuthorize("@securityService.isMember(#roomId)")` to
  enforce room membership checks declaratively.
- **Rationale:** Manual membership checks in every service method (Option A) are error-prone —
  a developer will forget to add the check somewhere. Encoding membership in the JWT (Option C)
  is dangerous — if a user is removed from a room, their token remains valid until expiry.
  `@PreAuthorize` keeps security logic centralized in `SecurityService` and business logic clean.
- **Trade-off:** Requires Spring Security's method-level security to be enabled
  (`@EnableMethodSecurity`). Acceptable for this stack.

---

## 5. Updated Entity: RoomMembership

Replaces the plain `room_members` join table from the original ERD.

| Field | Type | Constraints | Source |
|---|---|---|---|
| `id` | Long | PK, auto-generated | Database sequence |
| `user_id` | UUID (FK → User) | NOT NULL | From authenticated session |
| `room_id` | UUID (FK → Room) | NOT NULL | From room creation/join |
| `role` | Enum (OWNER, MEMBER) | NOT NULL | OWNER on create, MEMBER on join |
| `joinedAt` | Timestamp | NOT NULL | Server-assigned |

**Relationships:**
- **Many-to-One → User**: Each membership links to one user.
- **Many-to-One → Room**: Each membership links to one room.
- **Unique constraint** on (`user_id`, `room_id`): A user can only have one membership per room.

**Updated ERD:**
```
┌──────────────┐       ┌──────────────────┐       ┌──────────────────┐
│     User     │       │       Room       │       │  DrawingEvent    │
├──────────────┤       ├──────────────────┤       ├──────────────────┤
│ id (UUID) PK │──┐    │ id (UUID) PK     │──────▶│ id (Long) PK     │
│ email        │  │    │ name             │       │ room_id (FK)     │
│ displayName  │  │    │ inviteCode (6ch) │       │ user_id (FK)     │
│ avatarUrl    │  │    │ createdAt        │       │ type (ENUM)      │
│ provider     │  │    │ owner_id (FK)    │       │ data (JSONB)     │
│ providerId   │  │    └──────────────────┘       │ timestamp        │
└──────────────┘  │           ▲                   └──────────────────┘
                  │           │
                  │    ┌──────┴──────────────┐
                  └───▶│  RoomMembership     │
                       ├─────────────────────┤
                       │ id (Long) PK        │
                       │ user_id (FK → User) │
                       │ room_id (FK → Room) │
                       │ role (ENUM)         │
                       │ joinedAt            │
                       └─────────────────────┘
```

---

*Document version: 0.2 — ADRs 5-7 added; RoomMembership entity defined*
*Last updated: 2026-03-06*


