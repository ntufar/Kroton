# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

This repository currently contains only the technical specification (`doc/kroton-spec.md`). No Gradle project, source modules, or CI config exist yet — there are no build/lint/test commands to run. When the project is scaffolded, update this file with the actual commands (Gradle tasks, ktlint/detekt invocations, instrumented test commands, single-test invocation).

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
