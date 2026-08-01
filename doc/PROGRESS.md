# Kroton — Implementation Progress

Tracks status against the roadmap in `kroton-spec.md` §9. Update this file whenever a
milestone's scope changes state — it is the quick answer to "what's actually built"
without re-reading the whole spec or git log.

Status legend: ✅ done · 🚧 in progress · ⬜ not started

## M0 — Foundations

Status: 🚧 mostly done

- ✅ Gradle multi-module project (Kotlin DSL, version catalog, wrapper) matching spec §2.2
  module layout: `core:model/database/datastore/domain/export` (KMP), `core:designsystem`,
  7 `feature:*` modules, `:app`.
- ✅ `core:model` — enums and plain data classes for the full §3 schema (exercises, routines,
  workouts, measurements, records, user profile).
- ✅ `core:database` — Room entities for every §3 table, §3.6 indices, `ExerciseDao` /
  `WorkoutDao`, schema v1 exported to `core/database/schemas/`.
- ✅ `core:domain` — real, unit-tested `OneRepMaxCalculator` (Epley/Brzycki/Lombardi/O'Conner,
  suppressed outside reps 1..12), `VolumeCalculator`, `BodyCompositionCalculator` (Navy BF%,
  FFMI).
- ✅ `core:export` — `BackupJson` stub matching the §6.3 top-level shape (no writer yet).
- ✅ `core:designsystem` — `KrotonTheme` with the Aegean palette (§1.4), dark-by-default,
  dynamic colour on API 31+.
- ✅ `:app` — Koin DI, type-safe Navigation Compose with the 5 bottom-bar destinations +
  Settings, manifest with exactly the 5 approved permissions.
- ✅ CI (`.github/workflows/ci.yml`) — ktlint, detekt, `./gradlew build`, merged-manifest grep
  that fails the build if `INTERNET` appears.
- 🚧 Exercise catalogue: a hand-curated 50-exercise seed list (`core/domain/.../SeededExercises.kt`)
  covering every major muscle group ships via `ExerciseSeeder`, run once from
  `KrotonApplication.onCreate`. The full free-exercise-db import + curation to ~250-300 rows
  (spec §7.1) is still open — this is a functional placeholder, not the licensed import.
- ⬜ Écorché muscle map (front/back SVGs) + the enum-to-path CI contract test (spec §7.3).
- ⬜ Room migration test harness (schema-JSON diff test) — schema v1 export exists, but no
  migration tests yet since there's only one version.

Known deviations from spec, tracked here so they don't get rediscovered as bugs:

- iOS targets (`iosArm64`/`iosSimulatorArm64`/`iosX64`) are declared but commented out in the
  KMP module build files pending a validated Xcode toolchain. Only `androidTarget()` + `jvm()`
  build today.
- `workout_set(estimated_1rm_kg) WHERE is_completed = 1` (spec §3.6) is implemented as a plain
  (non-partial) index — Room's `@Index` has no `WHERE`-predicate support. Needs a raw-SQL
  migration if the partial index is required for performance later.
- `core:database`'s generated sources are excluded from ktlint/detekt (KSP writes into the same
  source set it processes); hand-written code in the module was verified clean before excluding.
- Room-KMP vs SQLDelight (spec §11.1): decided — Room, KMP support used as shipped.

## M1 — Log a workout

Status: ✅ done — every §5.3 interaction implemented and build/lint/test-verified

What's built (`core/domain/.../WorkoutRepository.kt`, `feature/workout/.../WorkoutViewModel.kt` +
`WorkoutScreen.kt` + `WorkoutExerciseSection.kt` + `WorkoutSheets.kt`):

- ✅ Start empty workout → a normal `is_in_progress` DB row from the first tap; app relaunch
  resumes it automatically (`WorkoutRepository.getInProgressWorkoutId`) — crash safety works as
  specified, no separate draft/staging path.
- ✅ Add exercise (bottom-sheet picker, client-side filtered search over the seeded catalogue),
  add/delete sets, edit weight/reps.
- ✅ **Previous** column: looks up the most recent non-in-progress session containing the
  exercise and fills the row on tap (`WorkoutDao.getMostRecentSets`).
- ✅ Checking a set commits immediately, computes the estimated 1RM (`OneRepMaxCalculator`), and
  checks it live against `personal_record` (`WorkoutRepository.checkAndRecordPrs` — MAX_WEIGHT,
  BEST_1RM, BEST_SET_VOLUME, MAX_REPS for bodyweight sets); a PR badge shows immediately. Warmup
  sets are excluded from both volume and PR checks per the default config.
- ✅ Denormalised `workout.total_volume_kg` / `total_sets` / `pr_count` recompute on every
  mutation (`recomputeTotals`), not just at finish.
- ✅ Finish → summary sheet (duration, volume, sets, PR count).
- ✅ Rest timer (`feature/workout/.../resttimer/`): completing a set starts a
  `RestTimerService` foreground service (type `specialUse`) so it survives the screen locking;
  countdown notification with `−15s`/`+15s`/skip actions, vibration + a short tone at zero.
  Duration is `WorkoutExercise.restSec` if set, else `DEFAULT_REST_SEC` (90s) — there's still no
  UI to edit `restSec` per exercise (tracked below under per-exercise overflow). State is shared
  with the UI via `RestTimerController` (interface in `core:domain`, Android impl wraps the
  service's companion `StateFlow`); `WorkoutScreen` shows a bar with the same actions and
  requests `POST_NOTIFICATIONS` on API 33+ the first time a workout screen with an active set is
  shown, so the countdown notification actually renders.
- ✅ Supersets: multi-select mode (toolbar toggle) groups exercises via
  `WorkoutRepository.groupAsSuperset`/`ungroupSuperset`; grouped cards get a coloured left border
  keyed by group id.
- ✅ Long-press a set row opens a type menu (normal/warmup/drop/failure/myorep, `SetType.entries`)
  plus delete.
- ✅ Swipe-to-delete with undo: `SwipeToDismissBox` on each set row, undo snackbar re-adds the row
  with the same weight/reps/type (uncompleted — an honest simplification documented in
  `WorkoutViewModel.undoDeleteSet`, since reconstructing completion timestamps/PR state on undo
  would need the same machinery as M3's retroactive-edit engine).
- ✅ Numeric stepper `+`/`−` buttons beside weight (2.5 kg step) and reps (1 step) fields.
- ✅ Per-exercise overflow sheet: replace exercise (reopens the picker in replace mode), edit
  note/rest time (`WorkoutRepository.updateExerciseNotes`/`updateExerciseRestSec`), plate
  calculator (`PlateCalculator.solve` against a seeded `plate_inventory`/`bar_inventory`, greedy
  per §4.5), inline history (last session's sets via `WorkoutRepository.getInlineHistory`).
  Reorder is exposed on `WorkoutRepository.reorderExercises`/`WorkoutViewModel.moveExercise`; no
  dedicated drag handle in the UI yet — tracked as a follow-up, not blocking M1's DoD.
- ✅ Keep-screen-on toggle in the workout screen's overflow menu (`View.keepScreenOn` via
  `DisposableEffect`) — screen-local for now since Settings (M6) doesn't exist yet.
- ✅ Notes field on the finish sheet, wired to `finishWorkout(notes)`.
- ✅ Finish-sheet muscle-group breakdown (`WorkoutRepository.muscleBreakdown`): primary muscle at
  full volume credit, secondaries at 0.5×, warmups excluded, shown sorted by volume.
- Reactivity model: repository exposes suspend snapshot reads, not `Flow`-based observation — the
  ViewModel reloads the whole active-workout snapshot after each mutation. Simple and correct at
  the set/exercise counts a single session has; revisit if it's ever a bottleneck.

Per spec §9, budget roughly a third of total project effort here, and use it daily before
starting M2 — the remaining items above (rest timer especially) matter for that daily-use bar.

## M2 — Routines

Status: ✅ done — build/lint/test-verified

What's built (`core/database/.../RoutineDao.kt`, `core/domain/.../RoutineRepository.kt`,
`feature/routines/.../RoutinesViewModel.kt` + `RoutinesScreen.kt`):

- ✅ Folder CRUD (create/rename/delete/reorder) and routine CRUD (create/duplicate/delete),
  editor for exercises + target sets (weight/reps), all against the schema-v1 routine tables
  that already existed but had no DAO until now.
- ✅ Start-from-routine (`RoutineRepository.startWorkoutFromRoutine`): creates a normal
  `is_in_progress` workout row pre-filled with the routine's exercises/target sets (unchecked),
  same crash-safety model as an empty workout; updates `routine.lastPerformedAt`.
- ✅ Save-workout-as-routine (`RoutineRepository.saveWorkoutAsRoutine`): reads a finished
  workout's logged exercises/sets and writes them as a new routine template using the logged
  values as targets.
- ✅ Routine card overflow: start / edit / duplicate / share-as-text (`ACTION_SEND`) / delete,
  per §5.2.
- **Integration point**: per spec §5.1/§5.2, Routines is *not* a bottom-bar tab — it's embedded
  in the Workout home screen (`feature/workout` now depends on `feature/routines` and composes
  `RoutinesScreen` under the "Start empty workout" button; starting a routine hands the new
  workout id to `WorkoutViewModel.loadWorkout` so the active-workout view picks it up
  immediately, no separate navigation hop).
- Folder/exercise drag-to-reorder has repository support (`reorderFolders`/`reorderExercises`)
  but no drag-handle UI yet — buttons/long-press reorder is a follow-up, not blocking M2's DoD.
  The routine → session round trip is verified (start-from-routine → finish → save-as-routine);
  the "→ history" leg of §9's phrasing is the data being durably recorded and readable via
  `WorkoutDao`, since the History *screen* itself is M3, not yet built.

## M3 — History

Status: ⬜ not started

List + calendar view, workout detail with edit mode, retroactive PR/total recomputation.

## M4 — Measurements

Status: ⬜ not started

Measurement types/entries, progress photos, derived metrics (Navy BF%, FFMI, BMI, 7-day EMA).

## M5 — Stats

Status: ⬜ not started

Consistency/volume/weekly-hard-sets/strength/body charts (§5.7), muscle-map heatmap, 60k-set
performance dataset as a gate.

## M6 — Data portability

Status: ⬜ not started

XLSX (fastexcel) and CSV export, JSON backup/restore round trip, auto-backup (WorkManager),
share sheet + email entry points, platform `BackupAgent`, Hevy/Strong CSV import.

## M7 — Release

Status: ⬜ not started

Accessibility pass, `el`/`en` localisation, F-Droid metadata, README/CONTRIBUTING polish,
v1.0.0.

## M8 — iOS

Status: ⬜ not started

SwiftUI layer over `core:*`, feature parity for log + charts + export.

## Open spec decisions still outstanding (§11)

1. ~~Room-KMP vs SQLDelight~~ — resolved: Room.
2. Vico vs hand-rolled Canvas charts — not yet needed (M5).
3. RPE vs RIR — not yet needed (M1 set row).
4. Multi-profile support — deferred; remember to keep nullable `profile_id` columns from the
   first schema that needs them.
5. Trace vs commission the écorché muscle map — **blocks M0 completion**, needs a decision
   before that asset work starts.
6. SQLCipher — deferred, opt-in toggle when reached (M7 settings).
