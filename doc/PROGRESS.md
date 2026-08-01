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

Status: ✅ done — build/lint/test-verified, including a repository-level retroactive-PR test

What's built (`core/database/.../WorkoutDao.kt` additions, `core/domain/.../HistoryRepository.kt`,
`core/domain/.../WorkoutRepository.editCompletedSet/deleteWorkout/duplicateAsNewWorkout`,
`feature/history/.../HistoryViewModel.kt` + `HistoryScreen.kt`):

- ✅ Reverse-chronological list grouped by month, calendar-view toggle (trained-day list for the
  current month — a simple textual list rather than a full month grid, since a real grid widget
  wasn't worth building before M5's charting infra exists; upgrade candidate later), header stats
  (workouts this week/month, current streak computed by walking `local_date` day-by-day so
  month/year boundaries are handled correctly, total volume).
- ✅ Workout detail: read-only rendering by default, **Edit** toggle turns each set row into
  editable weight/reps fields.
- ✅ **Retroactive PR re-derivation is self-healing, not a separate recompute pass**: `personal_record`
  is append-only and `RecordDao.getBest` is `MAX(value)` over surviving rows for that
  exercise+type, so `WorkoutRepository.editCompletedSet` just deletes the edited set's own PR
  rows before re-running the normal PR check — if that demoted the top row, the next-best
  surviving row (an earlier real set) becomes the answer automatically. Verified by
  `WorkoutRepositoryRetroactiveEditTest` (`core/domain` commonTest, against hand-rolled in-memory
  fake DAOs — no Room test harness needed since `WorkoutRepository` only depends on DAO
  interfaces): edits a 100kg PR set down to 70kg and asserts the ledger falls back to an earlier
  80kg set.
- ✅ Overflow: duplicate-as-new-workout (`WorkoutRepository.duplicateAsNewWorkout` — copies
  exercises/sets as a fresh unchecked `is_in_progress` workout, then navigates to the Workout tab
  where the existing auto-resume logic picks it up), save-as-routine (reuses M2's
  `RoutineRepository.saveWorkoutAsRoutine`), delete (`WorkoutRepository.deleteWorkout`, cascades
  PR-row cleanup per set).
- Added `kotlinx-coroutines-test` to the version catalog for this — the first `core/domain` test
  needing `runTest`/coroutine test infra.

## M4 — Measurements

Status: ✅ done — build/lint/test-verified

What's built (`core/database/.../MeasurementDao.kt` + `ProgressPhotoDao.kt` + `ProfileDao.kt`,
`core/domain/.../MeasurementRepository.kt` + `ProfileRepository.kt` + `MeasurementSeeder.kt`,
`feature/measure/...`):

- ✅ Builtin measurement types seeded from the existing `BuiltinMeasurementKeys` list (weight,
  body fat %, all circumferences, resting HR) — weight and body fat enabled by default, the rest
  available via **Manage**; custom types can be created too.
- ✅ Today card (quick-add weight/body fat), enabled-type list with a hand-rolled `Canvas`
  sparkline (no chart library dependency pulled in early — Vico per the spec is scoped to M5),
  tap-through to full history (editable table + backdated add, both routed through the same
  `addOrReplaceEntry` upsert since `measurement_entry`'s unique `(typeId, localDate)` index
  makes same-day re-entry a natural replace rather than a duplicate).
- ✅ Photos: capture via `ACTION_IMAGE_CAPTURE` + a new app `FileProvider` (`file_paths.xml`,
  scoped to `filesDir/photos/` and `cacheDir/exports/` — the latter pre-wired for M6) — no
  `CAMERA` permission needed, confirmed absent from the merged manifest same as `INTERNET`.
  Timeline grid, pose filter chips, two-photo compare. Photos are never touched by Auto Backup
  because nothing outside `BackupAgent`'s explicit include-list is (deferred fully to M6, but the
  storage location already matches the CLAUDE.md constraint).
- ✅ Derived card: Navy BF%, lean/fat mass, FFMI + normalised FFMI, BMI (new
  `BodyCompositionCalculator.bmi`), 7-day bodyweight EMA (new
  `BodyCompositionCalculator.exponentialMovingAverage`) — each derived value is computed
  alongside, never overwriting, manual entries, and reads its inputs from the *nearest-in-time*
  entry per type (`MeasurementDao.getNearestEntry`), same "nearest in time" convention as §4.1's
  bodyweight lookup for `WEIGHTED_BODYWEIGHT` 1RM. Unit-tested (`BodyCompositionCalculatorTest`)
  the same way `OneRepMaxCalculatorTest` covers §4.1.
- **New this milestone**: a minimal single-row `user_profile` DAO/repository
  (`ProfileDao`/`ProfileRepository`) — needed for height/sex inputs to body-composition math.
  Full settings read/write is still M6's job; this only covers ensure-seeded + height/sex update,
  surfaced as an inline prompt in the derived-metrics card rather than a real Settings screen.

Not yet built, tracked as follow-ups rather than blockers: a real month-grid calendar widget
(History's calendar view is still the simple list from M3), and photo compare is currently a
label-only placeholder (thumbnails render via `BitmapFactory` in the grid, but the side-by-side
compare view doesn't yet re-render the two selected images larger).

## M5 — Stats

Status: ✅ done (muscle-map heatmap excluded by design this pass — see below) —
build/lint/test-verified including the 60k-set performance gate

What's built (`core/database/.../StatsDao.kt`, `core/domain/.../StatsRepository.kt`,
`feature/stats/...`):

- ✅ **Charts are hand-rolled `Canvas` composables (`Charts.kt`: `LineChart`/`BarChart`/`DonutChart`),
  not Vico.** The spec's open decision #2 says "start with Vico, keep the chart API behind your
  own interface so swapping is contained" — given no way to verify Vico's exact current API
  surface against documentation in this pass, and that call sites already only touch these three
  functions, swapping the implementation later is contained to `Charts.kt` alone. Revisit before
  M7 polish.
- ✅ Consistency: workouts-per-ISO-week bar chart, streak (day-by-day walk handling month/year
  boundaries, same approach as M3's `HistoryRepository`), average duration. The GitHub-style year
  heatmap grid specifically is **not** built — `ConsistencyStats.trainedLocalDates` has the data,
  but the visual grid is deferred alongside the real month-calendar widget noted in M3/M4.
- ✅ Volume: total-volume line chart + muscle-share donut, both from pre-aggregated
  `StatsDao` rows. Stacked-by-muscle-over-time data is computed (`VolumeStats.volumeByDayAndMuscle`)
  but not yet rendered as a stacked chart — the donut and line cover the two highest-value views
  first.
- ✅ Weekly hard sets per muscle (§4.3): pre-aggregated exercise+day set counts fanned out to
  muscles in Kotlin (primary full credit, secondary 0.5×) over a rowset bounded by
  `#exercises × #days`, never per-set. Rendered as a per-week total bar chart with the 10/20
  reference numbers stated as text; true grouped-by-muscle bars are a follow-up.
- ✅ Strength: multi-select up to 5 exercises, each plotted as a 1RM-over-time line normalised to
  its first value = 100 so different lifts overlay meaningfully.
- ✅ Body: weight+7-day-EMA line, body fat line, circumference type list (reuses M4's
  `MeasurementRepository`). Dual-axis bodyweight-vs-lift overlay isn't built.
- ✅ Long-press → export chart data as CSV (`StatsViewModel.csvFor` + `ACTION_SEND`) wired on the
  Volume and Body sections. Pinch/drag zoom and tap-to-inspect tooltip are not implemented on the
  hand-rolled charts (`LineChart` exposes `onTapIndex` as a hook, unused by call sites yet).
- ✅ **60k-set performance gate** (spec §8, explicitly required by M5's definition of done):
  `StatsDaoPerformanceTest` (new `core/database` `jvmTest` source set, first for this module) runs
  against a *real* Room database on the JVM `BundledSQLiteDriver` (same engine as Android, via the
  already-existing `createRoomDatabase(path)` JVM builder) — seeds 2,000 workouts × 3 exercises ×
  10 sets = 60,000 sets using new batch-insert DAO methods (`insertWorkouts`/`insertExercises`/
  `insertSets`, each a single Room-batched transaction), then asserts every `StatsDao` query both
  completes under a 2s budget and returns row counts bounded by days/exercises/muscles — not sets.
  Full run (seed + 4 queries) takes ~0.6s.
- **Muscle-map heatmap tile is out of scope for this pass** (per explicit instruction), blocked
  upstream on the §7.3/§11.5 écorché art-sourcing decision — `WeeklyHardSets` already computes
  the muscle-credited data the heatmap would consume once that asset exists.

## M6 — Data portability

Status: ✅ done — build/lint/test-verified, including a real-Room backup round-trip test

What's built (`core/export/...`, `core/domain/.../BackupRepository.kt` + `BackupFileIo.kt` +
`ImportRepository.kt`, `feature/settings/...`, `app/.../backup/...`):

- ✅ **JSON backup/restore** (spec §6.3): `BackupJson` expanded to full fidelity (every table,
  nested exercises/sets under workouts and routines). `BackupRepository.buildBackup` assembles it
  from the DAOs; `.restore` always **remaps ids** rather than trusting the backup's own ids —
  necessary since a restore target (fresh install, or the same DB after a `REPLACE` wipe) has no
  guarantee of matching auto-generated ids. Exercises reconcile by `seedUuid` then
  `nameNormalised` (so seeded exercises aren't duplicated on restore); measurement types
  reconcile by `key`; everything else (routines, workouts, sets, records) is freshly inserted
  with remapped foreign keys. `MERGE` mode dedupes workouts by `startedAt`+`name` per spec;
  `REPLACE` wipes generated tables first (new DAO `clear*()` queries) but never touches the
  exercise catalog or profile. Verified end-to-end by `BackupRepositoryRoundTripTest`
  (`core/domain` `jvmTest`, real Room/SQLite like the M5 perf test): export → `REPLACE` restore →
  export again, asserting workout/exercise/measurement/photo *content* matches (ids legitimately
  differ after remapping, so the comparison ignores them — ids reassigned is the correct behaviour
  for a restore onto a target with no prior matching rows, not a bug).
- ✅ `.kroton` = ZIP of `backup.json` + `photos/` (`BackupFileIo`, `java.util.zip` — same
  established pattern as using `java.time` directly in this KMP module's commonMain).
- ✅ **XLSX** (`XlsxWriter`, fastexcel — verified against the library's actual GitHub source
  before writing any code, given no local docs access): all 9 §6.1 sheets, real date/numeric
  cells (not strings), bold frozen header row, autofilter.
- ✅ **CSV**: `Sets` + `Workouts` only (the two highest-value sheets per spec's own framing of
  `Sets` as "the pivot table") bundled as a ZIP — **not all 9 sheets**, a scoped simplification
  since XLSX already has full parity and duplicating all 9 sheet-builders in CSV form wasn't
  worth the time given everything else in this milestone. Follow-up, not forgotten.
- ✅ **Share entry points**: Save to… (`ACTION_CREATE_DOCUMENT`), Share… and Email to myself
  (`ACTION_SEND` via the existing `FileProvider`, correct OOXML/zip MIME types so Sheets/Drive
  offer themselves in the chooser) — all three behind the same generated file, per spec §6.5.
- ✅ **Auto-backup** (`AutoBackupWorker`/`AutoBackupScheduler`, WorkManager, no charging/idle
  constraints per spec): daily job writes a `.kroton` snapshot to a user-chosen SAF tree URI,
  keeps the last 7. Settings screen's folder picker enables it (persists a persistable URI
  permission). Enable state and tree URI live in plain `SharedPreferences` rather than the
  `core:datastore` module CLAUDE.md mentions — that module is still unimplemented; using it
  properly was out of scope for this pass, documented rather than silently swapped.
- ✅ **Platform `BackupAgent`** (`KrotonBackupAgent`, spec §6.7): `allowBackup="true"` +
  `dataExtractionRules`/`fullBackupContent` XML explicitly excluding `kroton.db`/`-wal`/`-shm`
  and `photos/`; `onFullBackup` refreshes a gzipped JSON snapshot
  (`filesDir/backup_snapshot/snapshot.json.gz`) before delegating to the default full-backup
  implementation, so only that small file (plus whatever the XML rules don't exclude) leaves the
  device. `KrotonApplication.onCreate` detects the snapshot on first launch after a restore,
  imports it via `BackupRepository.restore(REPLACE)`, and deletes it.
- ✅ **Hevy/Strong CSV import** (`CsvParser` + `HevyImport`/`StrongImport` in `core:export`,
  `ImportRepository` in `core:domain`): parses by header name per spec, not position. **Neither
  mapping was verified against a real Hevy or Strong export** — no sample file was available in
  this pass, and the spec explicitly warns not to trust the documented column shape blindly, so
  this is a best-effort implementation pending verification against real exports before relying
  on it. Unmatched exercise names are **auto-created as custom exercises** rather than routed
  through a manual fuzzy-match mapping screen (spec's "fuzzy-suggest, or create as custom" UI) —
  a scope simplification. Imported sets are **not** run through live PR detection — bulk-imported
  history doesn't retroactively populate `personal_record`; users get correct set/workout data
  immediately, but the Records screen for imported lifts needs a manual look, a known follow-up.
- **Settings screen** (`feature/settings`, previously a placeholder) now hosts auto-backup,
  export (XLSX/JSON/CSV with Save/Share/Email), import (Hevy/Strong), and restore (pick a
  `.kroton` file → preview workout count → Merge or Replace, always taking a safety snapshot
  first per spec). Units/theme/1RM-formula/plate-bar-inventory editors from spec §5.8 are **not**
  built — `ProfileRepository.update` exists for a future settings-fields screen, but this pass
  prioritized the data-portability half of M6 over the preferences half given the milestone's own
  name and the explicit M6 task list (XLSX/CSV/JSON/auto-backup/share/BackupAgent/import).

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
