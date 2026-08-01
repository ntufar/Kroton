# Kroton

Offline-first strength training and body-composition tracker for Android, built with Kotlin
Multiplatform in anticipation of an iOS port.

**No `INTERNET` permission, ever.** That's not a feature flag — it's a CI-enforced guarantee
(see `.github/workflows/ci.yml`). Exports leave the device only through the OS share sheet or
the Storage Access Framework; whatever app receives them does the networking, not Kroton.

Full product and technical detail lives in [`doc/kroton-spec.md`](doc/kroton-spec.md). Current
build status and what's implemented vs. still to do is tracked in
[`doc/PROGRESS.md`](doc/PROGRESS.md).

## Status

Early scaffolding stage (milestone M0 of the roadmap in spec §9). The module structure, data
model, and navigation shell build and run; none of the actual logging/history/stats features
are implemented yet.

## Stack

Kotlin 2.x, Jetpack Compose + Material 3, MVVM (`ViewModel` + `StateFlow`), Room over SQLite,
DataStore, Koin for DI, Navigation Compose with type-safe routes, Coroutines/Flow. Domain and
data layers are Kotlin Multiplatform from day one; `:app` and feature modules are Android-only
for now.

## Project layout

```
core/model         KMP  — entities, enums, value classes
core/database       KMP  — Room entities, DAOs, migrations
core/datastore      KMP  — settings
core/domain         KMP  — use cases, 1RM/volume/body-composition calculators
core/export         KMP  — backup/export data shapes
core/designsystem   Android — theme, tokens, shared composables
feature/*           Android — one module per bottom-nav destination
app                 Android — application module, DI wiring, navigation host
```

## Building

Requires JDK 17+ and Android SDK (compileSdk/target 36, minSdk 26).

```
./gradlew build          # full build: compile, lint, tests
./gradlew test           # unit tests only
./gradlew ktlintCheck    # style check
./gradlew detekt         # static analysis
./gradlew :app:assembleDebug
```

## Licence

GPL-3.0-or-later. `LICENSE` file to be added.

Exercise metadata sourced from `yuhonas/free-exercise-db` (Unlicense) and `wger` (CC-BY-SA);
attribution for both, and for the OpenStax *Anatomy & Physiology* muscle-map source art
(CC BY 4.0), is recorded in `NOTICE` once those assets are integrated (see spec §7).
