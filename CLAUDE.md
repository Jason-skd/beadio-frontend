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