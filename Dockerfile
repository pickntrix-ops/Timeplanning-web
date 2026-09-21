# Builds the wasmJs (Kotlin/Wasm + Compose Multiplatform) web target and
# serves the resulting static bundle. Android SDK isn't needed here — the
# multiplatform module's Android target only gets configured when an
# Android-specific task is actually requested (verified locally: a clean
# build with no SDK installed and no local.properties still produces the
# wasmJs distribution).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# The Kotlin/JS-Wasm Gradle plugin downloads its own Node.js binary, which
# needs libatomic at runtime — not present in this base image otherwise.
RUN apt-get update && apt-get install -y --no-install-recommends libatomic1 \
    && rm -rf /var/lib/apt/lists/*

COPY gradlew gradlew.bat ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY settings.gradle.kts build.gradle.kts ./
COPY shared/build.gradle.kts shared/build.gradle.kts
COPY shared/src shared/src

RUN ./gradlew :shared:wasmJsBrowserDistribution --console=plain --no-daemon

FROM nginx:alpine
ENV PORT=8080
COPY --from=build /app/shared/build/dist/wasmJs/productionExecutable /usr/share/nginx/html
COPY nginx.conf /etc/nginx/templates/default.conf.template
