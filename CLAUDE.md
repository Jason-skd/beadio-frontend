# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

NEVER TRY TO MODIFY "git" REPOSITORY!! SUCH AS COMMIT OR REVERT

you can read its history, however

## Project Overview

**beadio_frontend** is a Compose Multiplatform Desktop (JVM) application — the frontend client for the beadio backend (`/Users/skd/IdeaProjects/beadio_backend`). The backend automates watching online course videos via Playwright browser automation; this frontend provides the user interface for managing sessions, creating plans, investigating videos, and monitoring execution.

## Build & Development Commands

```bash
./gradlew :composeApp:run                          # Run the desktop app
./gradlew :composeApp:jvmTest                      # Run all tests
./gradlew :composeApp:jvmTest --tests "*TestName*" # Run specific test
./gradlew :composeApp:packageDmg                   # Package macOS DMG
./gradlew :composeApp:packageMsi                   # Package Windows MSI
./gradlew :composeApp:packageDeb                   # Package Linux DEB
```

Hot reload is enabled via the `composeHotReload` plugin.

## Tech Stack

- **Kotlin 2.3**, **Compose Multiplatform 1.10**, **Material 3**
- **androidx.lifecycle** (ViewModel + runtime-compose) for state management
- **kotlinx-coroutines-swing** for Swing dispatcher integration
- Target: Desktop JVM only (no Android/iOS)

## UI Design

**macOS Native Style**: The UI should mimic macOS native application appearance. Use:
- System fonts (San Francisco via system default)
- Native-like window chrome and title bar
- macOS-style navigation (sidebar, toolbar)
- Cupertino-like components where appropriate
- System colors that adapt to light/dark mode

## Project Structure

Single module: `composeApp/`

- `src/jvmMain/kotlin/com/github/jasonskd/` — all source code (entry point: `main.kt` → `App()`)
- `src/jvmMain/composeResources/` — Compose resources (images, etc.)
- `src/jvmTest/` — tests

The project was scaffolded from the Compose Multiplatform template. `commonMain` source set exists in the build config but currently has no Kotlin sources — all code lives in `jvmMain` since this targets Desktop only.

## Backend API Reference

The backend runs at `http://localhost:8080`. All responses are JSON with a `type` discriminator field (kotlinx.serialization sealed classes). Chinese text is used for user-facing `message` and `displayName` fields.

### Typical Workflow

```
1. GET /sites                    → Supported site names (e.g. ["liru"])
2. POST /sessions/{siteName}     → Create browser session
3. GET /sessions/{siteName}      → Poll until Ready (browser opens for user login)
4. POST /plans/new               → Create plan manager (body: { "sites": ["liru"] })
5. POST /plans/new/courses       → Start course collection
6. GET /plans/new                → Poll until AwaitingCourseSelection (data has course lists)
7. POST /plans/new/videos        → Submit course selection (body: { "selection": { "liru": [0, 2] } })
8. GET /plans/new                → Poll until AwaitingPlanSave
9. PUT /plans/new                → Save plan (body: { "planName": "my-plan" })
10. POST /investigation          → Start video metadata collection (body: { "planName": "..." })
11. GET /investigation           → Poll progress until Completed
12. POST /execution              → Start video playback (body: { "planName": "..." })
13. GET /execution               → Poll playback progress
14. DELETE /sessions             → Close all sessions
```

### Response Format

```typescript
// Success
interface GeneralResponse<T> {
  type: string       // sealed class discriminator
  message: string    // Chinese status text
  data?: T
}
// Error
interface ExceptionResponse<T> extends GeneralResponse<T> {
  exceptionType: string  // error type enum
}
```

Each module (Session, Plan, Investigation, Execution) has three response variants: `Ready`, `Processing`, `Failed`. Poll `Processing` states every 1-2 seconds.

### Key Endpoints

| Module | POST (create) | GET (poll state) | DELETE (cleanup) | Additional |
|---|---|---|---|---|
| Sessions | `/sessions/{site}` | `/sessions/{site}` | `/sessions` | `POST /sessions/{site}/retry` |
| Plan Creation | `/plans/new` | `/plans/new` | `/plans/new` | `POST .../courses`, `POST .../videos`, `PUT /plans/new` |
| Plans CRUD | — | `GET /plans` | `DELETE /plans` (body: planName) | — |
| Investigation | `/investigation` | `/investigation` | `/investigation` | — |
| Execution | `/execution` | `/execution` | `/execution` | — |

### Error Types by Module

**Session**: `UNSUPPORTED_SITE` (400), `SESSION_ALREADY_EXISTS` (409), `SESSION_NOT_FOUND` (404), `SESSION_NOT_READY` (409)

**Plan**: `UNSUPPORTED_SITE` (400), `INDEX_OUT_OF_BOUNDS` (400), `MANAGER_ALREADY_EXISTS` (409), `MANAGER_NOT_FOUND` (404), `SESSIONS_NOT_READY` (409), `INVALID_STATE` (409), `PLAN_SAVE_FAILED` (500), `COURSE_FETCH_FAILED` (500), `VIDEO_FETCH_FAILED` (500)

**Investigation**: `ALL_VIDEOS_COLLECTED` (409), `MANAGER_ALREADY_EXISTS` (409), `MANAGER_NOT_FOUND` (404), `PLAN_SAVE_FAILED` (500), `INVALID_STATE` (409), `SESSIONS_NOT_READY` (409), `PLAN_NOT_FOUND` (404)

**Execution**: `ALL_VIDEOS_FINISHED` (409), `MANAGER_ALREADY_EXISTS` (409), `MANAGER_NOT_FOUND` (404), `PLAN_SAVE_FAILED` (500), `SITE_EXECUTION_FAILED` (500), `INVALID_STATE` (409), `SESSIONS_NOT_READY` (409), `PLAN_NOT_FOUND` (404)

## Frontend Features (Total Task)

The frontend has **3 user-triggered features** and **2 automatic sub-flows**.

### User-Triggered Features

#### 1. 创建计划 (Create Plan)

Multi-step wizard:
1. User selects sites to include (`GET /sites`)
2. `POST /plans/new` → create plan manager
3. `POST /plans/new/courses` → start course collection
4. Poll `GET /plans/new` until `AwaitingCourseSelection` (data contains course lists per site)
5. User selects courses from the list
6. `POST /plans/new/videos` → fetch videos for selected courses
7. Poll `GET /plans/new` until `AwaitingPlanSave`
8. User inputs plan name → `PUT /plans/new` to save

#### 2. 执行播放 (Execute Plan)

User selects a saved plan, then the frontend automatically orchestrates:
1. `POST /investigation` to start video metadata collection
   - If `ALL_VIDEOS_COLLECTED` (409), skip investigation
2. `POST /execution` to start playback
3. **Poll both** `GET /investigation` and `GET /execution` concurrently
   - Show investigation progress until it completes, then stop its polling
   - Show execution progress (plan/site/video level) until completion

#### 3. 管理已保存计划 (Manage Saved Plans)

- List all saved plans (`GET /plans`)
- Delete a plan (`DELETE /plans` with body `{ "planName": "..." }`)

### Automatic Sub-Flows (not user-triggered)

#### Session Creation

When any feature receives `SESSIONS_NOT_READY` error:
1. Extract the list of unready sites from the error's `data` field
2. `POST /sessions/{site}` for each unready site
3. Poll `GET /sessions/{site}` until all sessions reach `Ready`
4. Retry the original operation

#### Investigation (Video Metadata Collection)

Automatically triggered by "Execute Plan" (feature 2). Not a standalone user action. If investigation is still in progress when execution starts, both are polled concurrently.

### Execution Progress Data Structure

```json
{
  "planProgress": { "watched": 1200, "duration": 5000 },
  "sites": {
    "liru": {
      "siteProgress": { "watched": 1200, "duration": 5000 },
      "currentVideo": { "name": "视频1", "watched": 300, "duration": 600 }
    }
  }
}
```

## Backend Lifecycle Management

The frontend must control the backend's lifecycle synchronously.

### Startup Sequence

1. Show a splash/loading screen while waiting for backend to become ready
2. Poll `http://localhost:8080/` or `GET /sites` until backend responds successfully
3. Only proceed to the main UI after backend is ready

### Shutdown Sequence

1. When frontend receives close request (user clicks close button or window closes)
2. **Immediately close the GUI/window** (hide the compose window)
3. In the background, send `POST http://localhost:8080/break` to gracefully shutdown backend
4. Wait for the response from `/break` endpoint
5. After receiving response (or timeout), fully exit the frontend process

The `/break` endpoint:
- Returns HTTP 200 with JSON `{"type":"Success","message":"服务已关闭"}`
- Closes all Playwright browser resources and managers
- Then terminates the JVM process