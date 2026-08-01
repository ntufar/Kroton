# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

M0 "Foundations" is scaffolded: the full module graph builds, Room schema v1 compiles and exports its schema JSON, the 1RM calculator is real and unit-tested, and the app assembles and launches to a five-tab shell with placeholder feature screens. Feature logic (active workout, exercise seeding, exports, etc.) is not implemented yet — that's M1 onward.

**Build/test/lint commands:**

- `./gradlew build` — full build (all modules, all variants, unit tests, lint)
- `./gradlew :app:assembleDebug` — just the app APK
- `./gradlew test` — unit tests across all KMP/Android modules (e.g. `core/domain`'s `OneRepMaxCalculatorTest`)
- `./gradlew :core:domain:test --tests "*.OneRepMaxCalculatorTest"` — single-test invocation example
- `./gradlew ktlintCheck` / `./gradlew ktlintFormat` — lint / autoformat (config: root `.editorconfig`)
- `./gradlew detekt` — static analysis (config: `config/detekt/detekt.yml`)
- `./gradlew :app:processDebugManifest` — merges the manifest to `app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml`; CI greps this for `android.permission.INTERNET` and fails the build if found (`.github/workflows/ci.yml`)

**Deviations from the spec worth knowing about:**

- **iOS targets are declared but commented out.** `:core:model`, `:core:database`, `:core:domain`, `:core:export` target `androidTarget()` + `jvm()` only for now; the `iosArm64()/iosSimulatorArm64()/iosX64()` lines are present but commented with a `TODO`, since a Mac/Xcode CI runner and Room's native SQLite driver wiring weren't validated in this pass. Re-enabling them is expected to be mechanical (Room 2.7+ already ships iOS artifacts) but budget time to verify the KSP-generated `RoomDatabaseConstructor` actuals and `BundledSQLiteDriver` cinterop on-device.
- **Room KMP**: the `KrotonDatabase` `expect object KrotonDatabaseConstructor` in `:core:database` commonMain has **no hand-written `actual`** — the Room KSP compiler generates the platform actuals per source set (`kspAndroid`, `kspJvm`) automatically when the class is annotated `@ConstructedBy`. Don't add a manual `actual object` for it; that would conflict with the generated one.
- **`:core:database` is excluded from ktlint/detekt.** KSP writes Room's generated DAO/database impls into the same Kotlin source sets it processes, and ktlint-gradle's KMP support doesn't reliably exclude generated-only directories from linting, so the module's checked-in code is excluded wholesale (hand-written code there was still verified clean before excluding). Every other module is linted.
- **`workout_set(estimated_1rm_kg) WHERE is_completed = 1`** (spec §3.6) is implemented as a plain (non-partial) index on `estimated1RmKg` — Room's `@Index` doesn't support a `WHERE` predicate. Revisit with a raw migration `CREATE INDEX ... WHERE ...` statement when the migration-testing apparatus lands.
- Gradle wrapper is pinned to 8.13; AGP 8.9.2, Kotlin 2.1.20, Room 2.7.2, Koin 4.0.4, Compose BOM 2025.04.00 — chosen as a mutually-compatible, well-established combination rather than the latest available versions.

Treat `doc/kroton-spec.md` as the source of truth for all product, architecture, and data-model decisions below. Re-read the relevant section before implementing a feature — this file only summarizes what's needed to orient quickly.

## What Kroton is

Offline-first strength training and body-composition tracker for Android (Kotlin, package `io.github.ntufar.kroton`, GPL-3.0-or-later). The defining constraint: **no `INTERNET` permission, ever** — this is a verifiable, CI-enforced guarantee, not a feature toggle. Exports leave only via the OS share sheet / Storage Access Framework; other apps do any networking.

## Architecture

- **Stack**: Kotlin 2.x, Jetpack Compose + Material 3, MVVM (`ViewModel` + `StateFlow`, immutable UI state), Room (KSP) over SQLite, DataStore (Proto) for prefs, Koin for DI, Navigation Compose with type-safe routes, Coroutines/Flow, Vico for charts, `fastexcel` for XLSX (never Apache POI — 10MB+ and method-count/XML-parser issues on Android).
- **KMP from day one**: domain and data layers are Kotlin Multiplatform now, in anticipation of an iOS port later, because retrofitting KMP after a year of Android-only work is explicitly called out as the most expensive reversal available. Module layout:
  ```
  :core:model        KMP  — pure entities, enums, value classes
  :core:database      KMP  — Room entities, DAOs, migrations
  :core:datastore     KMP  — settings
  :core:domain        KMP  — use cases, 1RM/volume/BF calculators
  :core:export        KMP  — JSON serialisation; XLSX writer is actual/expect
  :core:designsystem  Android — theme, tokens, shared composables
  :feature:workout|routines|history|exercises|measure|stats|settings  Android
  :app                Android
  ```
  Keep business logic (calculators, use cases, serialization) in `:core:*` even before iOS work starts — that's the point of the split.
- **Permissions**: only `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `VIBRATE`, `WAKE_LOCK`. File access is SAF (`ACTION_CREATE_DOCUMENT`/`ACTION_OPEN_DOCUMENT`), camera via `ACTION_IMAGE_CAPTURE` — neither needs a dangerous permission. A CI check must fail the build if `INTERNET` appears anywhere in the merged manifest (spec §2.3).

## Data model essentials (§3 of the spec)

- All monetary/physical values are stored canonical (kg, cm, m, seconds); unit preference is a display-layer concern only — never store user-preferred units.
- Timestamps: epoch ms UTC plus a companion `local_date` (`INTEGER` as `yyyymmdd`) column wherever day-bucketing matters, so timezone changes can't silently re-bucket history.
- Schema changes are additive only — never destroy user history on migration. Room migration tests are mandatory per schema version, including a test that exports schema JSON and diffs it against the checked-in file.
- Seeded exercise rows (`is_custom = false`) are reconciled on app upgrade by a stable `seed_uuid` so user edits to a seeded exercise survive updates.
- Denormalised aggregates (`workout.total_volume_kg`, `total_sets`, `pr_count`, `workout_set.estimated_1rm_kg`) are recomputed on write; retroactive edits to a past workout must recompute these and re-derive affected PRs.
- Indices in §3.6 are non-negotiable for chart performance at 5+ years of data — don't drop them during a migration without re-adding.

## Calculations (§4) — implement in `:core:domain`, unit-test against the reference values in the spec

- 1RM formulas (Epley default, Brzycki, Lombardi, O'Conner) only apply for `reps` in 1..12 — suppress the estimate outside that range rather than showing noise.
- Volume: `weight_kg × reps` per set; secondary muscles get fractional credit (`secondary_muscle_credit`, default 0.5); warmup sets excluded from volume/PR by default (configurable).
- Navy-method body fat, FFMI, and bodyweight EMA (7-day) are all *derived* values shown alongside manual entries, never overwriting them.

## Conventions that shape implementation decisions

- **Crash safety over explicit save**: an in-progress workout is a normal DB row from the first tap (`workout.is_in_progress`), not a separate draft/staging concept — recovery is automatic. Every set check-in commits immediately, no "save" step anywhere in the active workout flow.
- **PR detection is live**: a completed set is checked against `personal_record` at write time, not in a batch job.
- **Muscle map**: SVG path-data strings live in `:core:model` (extracted at build time into generated Kotlin, not parsed from XML at runtime) so iOS shares the same source of truth. Parse `Path` objects once at startup on Android and cache them keyed by muscle — never re-parse per frame. The enum-to-path contract (every `MuscleGroup` except `FULL_BODY`/`CARDIO` must resolve to a path, and vice versa) is enforced by a CI test — keep it passing when touching either the enum or the SVGs.
- **Exports**: XLSX/CSV/JSON are three views of the same denormalised export data, not independently maintained formats — see spec §6 for the exact sheet/column list before changing any of them. JSON backup (`.kroton` = zip of `backup.json` + `photos/`) is the lossless round-trip format; XLSX is analysis-only.
- Progress photos are always app-private (`filesDir/photos/`), never `MediaStore`, and excluded from platform Auto Backup/iCloud Backup (only a gzipped JSON snapshot is backed up — raw SQLite files must never be included in Auto Backup, per §6.7).

## Explicit non-goals (don't add these without a product decision)

Social features, following, AI coaching, wearable sync, Health Connect, nutrition/macros, direct Google Sheets API integration, a downloadable exercise-image pack, and — permanently, not just for v1 — any bundled exercise photographs/illustrations/video (spec §7.2 explains why; the muscle map in §7.3 is the intended substitute for "what does this exercise work").

## Content sourcing constraints

- Exercise metadata comes from `yuhonas/free-exercise-db` (Unlicense) with `wger` as a secondary source (CC-BY-SA, share-alike — attribute in `NOTICE`).
- The anatomical muscle map traces OpenStax *Anatomy & Physiology* fig. 1105 (CC BY 4.0) — attribution required in `NOTICE` and the in-app licences screen. Do not pull muscle-map source art from Wikimedia Commons without checking each file's individual licence (the category is not uniformly licensed).
- Do not copy Hevy/Strong/Jefit branding, icon, palette, or exercise-description text, and do not decompile competitor APKs for assets or strings (spec §1.5).
