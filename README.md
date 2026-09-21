# timeplanning-web

Deploy target for TimePlanning's web client (the `wasmJs` Compose Multiplatform target). This is a snapshot copy of the [`app/`](https://github.com/pickntrix-ops/TImeplanning) directory from the main `TImeplanning` repo, pushed here so Railway has a repo to build from independently of local/mobile development.

**This is not where the app's source is maintained.** Edits to `shared/`, `androidApp/`, or `iosApp/` happen in the main `TImeplanning` repo; when the web target changes, re-sync this repo from there before deploying (`rsync -a --exclude build/ --exclude .gradle/ --exclude .kotlin/ --exclude local.properties <TImeplanning>/app/ .`, commit, push).

## Deploying

Railway builds `Dockerfile` on push: a multi-stage build that runs `./gradlew :shared:wasmJsBrowserDistribution` (JDK 21, no Android SDK needed — the Android target isn't configured unless an Android-specific task runs) and serves the static output via nginx, listening on Railway's `$PORT`.

## Local dev

```
./gradlew :shared:wasmJsBrowserDevelopmentRun
```
Opens a webpack dev server on `localhost:8080`. The backend (`api.pickntrix-themeparks.com`) must allow that origin in its CORS config (`WEB_CORS_ALLOWED_ORIGINS` on the server) for sign-in and API calls to work.
