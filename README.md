# CronoSync

*[Leia em português](README.pt-BR.md)*

A stopwatch, timer and Pomodoro that **several devices see and control at the same time**. Start the
timer on your phone, pause it on your computer: every device in the same room shows the same time,
in real time.

- **Stopwatch** with laps · **Timer** with an alarm · **Pomodoro** (focus / short break / long break,
  customizable, the next phase starts by itself)
- **Rooms** shared by an 8-character code; without a room, everything works offline on one device
- **Android**, **Linux** and **Windows** apps, plus a self-hosted server

## How it works

```
 Android ─┐                         ┌─ PostgreSQL (rooms survive restarts)
 Desktop ─┼── WebSocket (wss://) ── Ktor server
 Desktop ─┘                         └─ server clock = the single source of time
```

- A room's state is stored as *"started at X + time accumulated"* on the **server clock**, never as a
  ticking number. Each device estimates its offset to the server clock, so all screens show the same
  time without the devices ever sending "ticks".
- Every change is a command with an **expected version**: if two devices tap at the same moment, the
  server applies one and rejects the stale one, and the losing device rolls back its prediction.
- Taps take effect **immediately** on the device that made them (optimistic prediction) and are then
  confirmed by the server.

## Project structure

| Module | What lives there |
|---|---|
| `shared` | Kotlin Multiplatform: domain (stopwatch, timer, Pomodoro), sync protocol and client, ViewModels |
| `sharedUi` | Compose Multiplatform screens shared by Android and desktop |
| `androidApp` | Android app: full-screen alarm, notifications, permissions |
| `desktopApp` | Desktop app (Linux/Windows): system tray, notifications, installers |
| `server` | Ktor server: rooms over WebSocket, PostgreSQL persistence (Flyway migrations) |
| `deploy/homologacao` | Staging deployment: Docker Compose, nginx and a deploy script |

Architecture: MVVM with unidirectional data flow, Jetpack/Compose Multiplatform UI, Koin for
dependency injection. Business rules live in `shared/commonMain` with no platform dependencies.

**Stack:** Kotlin 2.4 · Compose Multiplatform 1.12 · Ktor 3.6 · Koin 4.2 · PostgreSQL 16 · Flyway ·
kotlin-test, kotlinx-coroutines-test, Turbine, Testcontainers.

## Running locally

Requirements: JDK 17+, Android SDK (Android Studio), Docker.

```bash
scripts/local-test.sh        # database + server + desktop app (+ installs on a USB-connected phone)
scripts/server.sh            # only database + server, in Docker, in the background
./gradlew :desktopApp:run    # desktop app pointing to the local server
```

The debug Android build connects to the server on your computer over Wi-Fi (the address is detected
at build time). A browser test page is available at `http://localhost:8080/dev/` in development mode.

## Tests

```bash
./gradlew :shared:jvmTest :server:test :desktopApp:test
```

The PostgreSQL tests use Testcontainers and need Docker running.

## Building

| Target | Command |
|---|---|
| Android (staging) | `./gradlew :androidApp:assembleHomologacao` |
| Linux `.deb` | `./gradlew :desktopApp:packageDeb` |
| Windows `.msi` (on Windows) | `gradlew.bat :desktopApp:packageMsi` |

Add `-Pcronosync.environment=homologacao` to desktop builds to point them to the staging server.
Server deployment is described in [`deploy/homologacao/README.md`](deploy/homologacao/README.md).

## Security

- Rooms are not created on demand: a device can only join a code the server generated.
- Per-IP limits on room creation, connection attempts and simultaneous connections, plus a
  per-connection command limit and a maximum message size.
- Behind a reverse proxy, the real client IP is read only from the address the proxy itself appends
  (`CRONOSYNC_BEHIND_PROXY`), so a forged `X-Forwarded-For` header can't bypass the limits.
- Outside development mode, the server refuses to start without database credentials, and the
  container runs as an unprivileged user.

## License

[MIT](LICENSE)
