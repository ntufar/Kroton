# Kroton — Technical Specification v0.1

**Offline-first strength training and body composition tracker for Android.**

- **Package:** `io.github.ntufar.kroton`
- **Licence:** GPL-3.0-or-later
- **Platforms:** Android first (minSdk 26, target 36), iOS later via shared Kotlin core
- **Network:** none. The app declares no `INTERNET` permission. Exports leave via the OS share sheet.
- **Identity:** Aegean blue `#14548C` ground, chalk `#F2EFE6` mark, bull's-head silhouette (see §1.4)

---

## 1. Product definition

### 1.1 Problem

Existing loggers (Hevy, Strong, Jefit) are excellent at set logging but either gate body-composition tracking behind a subscription, require an account, or keep the data in a cloud silo where it can't be analysed properly. Kroton logs strength work *and* body metrics, keeps everything on the device, and exports a spreadsheet you can actually pivot.

### 1.2 Design principles

1. **No network, ever.** Absence of the `INTERNET` permission is a verifiable guarantee, not a promise. It's the headline feature.
2. **The user owns the bytes.** Full JSON round-trip backup, plus XLSX/CSV for analysis. Getting a file to a laptop, an inbox, or a Google Sheet must take three taps — the app hands the file to the OS share sheet and lets other apps do the networking. No proprietary lock-in.
3. **Logging must be faster than the rest between sets.** Every tap on the active-workout screen is on the critical path.
4. **Metric canonical, display converted.** Everything stored in kg, cm, metres, seconds. Unit preference is a view concern only.
5. **Additive schema.** Never destroy user history on migration.

### 1.3 Explicit non-goals (v1)

Social feed, following, sharing, workout marketplace, AI coaching, wearable sync, Health Connect, nutrition/macros. **Bundled exercise photographs, illustrations, or video are excluded permanently, not deferred** — see §7.2.

### 1.4 Identity

Kroton (modern Crotone, Calabria) was the Achaean colony founded around 710 BC on instruction from Apollo's oracle at Delphi. It became famous in antiquity for its physicians, for Pythagoras' school, and above all for its athletes — the wrestler Milo of Kroton was the most crowned competitor of the ancient games, and the legend of him carrying a calf daily until it grew into a bull is the oldest surviving account of progressive overload.

The city's silver staters, struck from roughly 530 BC, carry the Delphic tripod. One numismatic reading holds the tripod was an agonistic type: the prize carried off by a Krotoniate athlete, commemorating the city's run of Olympic victories. An ancient trophy from the city that produced the progressive-overload myth is an unusually good fit for a training log.

**The mark is a bull's head**, for Milo's calf — the animal he is said to have carried daily until it outgrew him. A solid silhouette, horns as a single stroke, face features knocked out as holes.

**The palette is Aegean**: a chalk-white mark on deep sea blue. High contrast at thumbnail size, and the light-on-dark arrangement means the monochrome themed-icon layer is simply the foreground shape — no inversion step, one less thing to get wrong.

| Token | Value | Use |
|---|---|---|
| `ground` | `#14548C` | icon background layer, brand surfaces |
| `mark` | `#F2EFE6` | icon foreground, text on ground |
| `slate` | `#1B1B1F` | app dark theme background |
| `ink` | `#2A2721` | text on light surfaces |

Blue is the most crowded colour in the store, so the silhouette carries the recognition load rather than the colour. Keep the bull bold and uncluttered; resist adding detail that dissolves below 64 dp.

Approved alternates if it doesn't survive contact with a real launcher: terracotta (`#C25E33` ground, `#2A1710` mark — black-figure pottery), stater silver (`#CBC7BA` / `#2E2B24`), verdigris (`#5F7355` / `#F4F1EA`).

Kroton's coins were also **incuse**: struck in relief on the obverse and punched as the same design in negative on the reverse. Use that as the system's visual device — splash state, empty-state illustrations, and the "no data yet" chart placeholders are the negative of the primary mark.

Do not reproduce a photograph or rubbing of an actual coin, or trace a museum's vase photograph. Draw the bull from scratch; the conventions are public, a specific photograph is not.

**Adaptive icon build notes.**

- Foreground and background are separate layers, so the colourway is a one-line change later. Try the alternates on a real device before locking it in.
- The canvas is 108×108 dp but only the centre 66 dp is guaranteed visible — launchers mask the rest to circles, squircles, or teardrops. Scale the bull to roughly 62% of the canvas or Samsung's circular mask clips the ears.
- **The monochrome themed layer is the trap.** In the full-colour icon the eyes and nostrils are circles painted in the background colour. The themed layer has no background, so painting them that way yields a featureless blob. They must be genuine holes — one compound path with `fill-rule="evenodd"`, or a mask. Check it against a themed-icon launcher before release, not after.

### 1.5 A note on cloning

Reimplement the *feature set* and the *interaction model* — those aren't protectable. Do not copy Hevy's name, logo, icon, colour palette, marketing copy, exercise-description text, or exercise illustrations. Write your own exercise seed data and draw your own assets. Also do not decompile their APK for assets or strings. Functional equivalence is fine; asset reuse is not.

---

## 2. Architecture

### 2.1 Stack

| Concern | Choice | Note |
|---|---|---|
| Language | Kotlin 2.x | |
| UI | Jetpack Compose + Material 3 | dynamic colour on Android 12+ |
| Architecture | MVVM, unidirectional data flow | `ViewModel` + `StateFlow`, immutable UI state |
| Persistence | Room (KSP) over SQLite | |
| Preferences | DataStore (Proto) | |
| DI | Koin | lighter than Hilt, no kapt, KMP-friendly |
| Navigation | Navigation Compose, type-safe routes | |
| Async | Coroutines + Flow | |
| Charts | Vico | Compose-native; fallback is custom `Canvas` |
| XLSX writing | `com.github.dhatim:fastexcel` | ~200 KB. **Do not use Apache POI** — 10 MB+, method-count and XML-parser problems on Android |
| ZIP/JSON | `kotlinx.serialization` + `java.util.zip` | |
| Background | WorkManager (auto-backup), foreground service (active workout) | |
| Build | Gradle version catalogs, R8 full mode | |

### 2.2 Module layout — plan for iOS from day one

Because iOS is on the roadmap, put the domain and data layers in Kotlin Multiplatform **now**. Room 2.7+ supports KMP; the alternative is SQLDelight. Retrofitting KMP after a year of Android-only development is the single most expensive mistake available here.

```
:core:model          KMP  — pure Kotlin entities, enums, value classes
:core:database       KMP  — Room entities, DAOs, migrations
:core:datastore      KMP  — settings
:core:domain         KMP  — use cases, 1RM/volume/BF calculators
:core:export         KMP  — JSON serialisation; XLSX writer is actual/expect
:core:designsystem   AND  — theme, tokens, shared composables
:feature:workout     AND  — active session logger
:feature:routines    AND
:feature:history     AND
:feature:exercises   AND
:feature:measure     AND
:feature:stats       AND
:feature:settings    AND
:app                 AND
```

iOS later consumes `:core:*` as a framework with a SwiftUI layer. Target roughly 60% shared code.

### 2.3 Permissions

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
<uses-permission android:name="android.permission.VIBRATE"/>
<uses-permission android:name="android.permission.WAKE_LOCK"/>
```

That is the entire list. File access uses the Storage Access Framework (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`), which needs no permission. Progress photos are copied into app-private storage; the camera is invoked via `ACTION_IMAGE_CAPTURE` so no `CAMERA` permission is required either.

Add a CI check that fails the build if `INTERNET` appears in the merged manifest. It is a load-bearing claim.

---

## 3. Data model

All identifiers are `Long` autoincrement except where noted. All timestamps are epoch milliseconds UTC, with a companion local-date column (`INTEGER` as `yyyymmdd`) where day-bucketing matters, so that timezone changes don't silently re-bucket history.

### 3.1 Exercise catalogue

```
exercise
  id, name, name_normalised (for search)
  exercise_type       enum: WEIGHT_REPS, BODYWEIGHT_REPS, WEIGHTED_BODYWEIGHT,
                            ASSISTED_BODYWEIGHT, DURATION, DISTANCE_DURATION,
                            REPS_ONLY, WEIGHT_DISTANCE
  equipment           enum: BARBELL, DUMBBELL, MACHINE, CABLE, KETTLEBELL,
                            BODYWEIGHT, BAND, PLATE, SMITH, EZ_BAR, TRAP_BAR,
                            SLED, SUSPENSION, OTHER
  primary_muscle      enum MuscleGroup
  force               enum: PUSH, PULL, STATIC        -- from seed data, useful for stats
  mechanic            enum: COMPOUND, ISOLATION
  is_custom           boolean
  is_archived         boolean
  default_rest_sec    int?
  instructions        text?
  created_at, updated_at

exercise_secondary_muscle
  exercise_id, muscle   -- many-to-many, drives fractional volume credit

MuscleGroup enum:
  CHEST, FRONT_DELTS, SIDE_DELTS, REAR_DELTS, LATS, UPPER_BACK, TRAPS,
  LOWER_BACK, BICEPS, TRICEPS, FOREARMS, ABS, OBLIQUES, QUADS, HAMSTRINGS,
  GLUTES, CALVES, ADDUCTORS, ABDUCTORS, NECK, FULL_BODY, CARDIO
```

Ship ~250 seeded exercises as a bundled JSON asset, versioned. Seed rows are `is_custom = false` and are reconciled on upgrade by a stable `seed_uuid`, so a user's edits to a seeded exercise survive an app update.

### 3.2 Routines (templates)

```
routine_folder      id, name, sort_order

routine             id, folder_id?, name, notes, sort_order,
                    created_at, updated_at, last_performed_at?

routine_exercise    id, routine_id, exercise_id, sort_order,
                    superset_group_id?,   -- null = not in a superset
                    rest_sec?, notes?

routine_set         id, routine_exercise_id, sort_order,
                    set_type, target_reps_min?, target_reps_max?,
                    target_weight_kg?, target_rpe?
```

### 3.3 Logged workouts

```
workout             id, routine_id?, name, notes?,
                    started_at, ended_at?, duration_sec,
                    local_date,
                    total_volume_kg,      -- denormalised, recomputed on write
                    total_sets, pr_count,
                    is_in_progress        -- exactly one row may be true

workout_exercise    id, workout_id, exercise_id, sort_order,
                    superset_group_id?, notes?, rest_sec?

workout_set         id, workout_exercise_id, sort_order,
                    set_type   enum: NORMAL, WARMUP, DROP, FAILURE, MYOREP
                    weight_kg REAL?, reps INT?,
                    distance_m REAL?, duration_sec INT?,
                    rpe REAL?, rir INT?,
                    is_completed BOOLEAN,
                    completed_at?,
                    estimated_1rm_kg REAL?   -- computed on write, indexed
```

Warmup sets are excluded from volume and PR calculations by default (configurable).

### 3.4 Body metrics

```
measurement_type    id, key, display_name, unit_kind (MASS|LENGTH|PERCENT|COUNT),
                    is_builtin, is_enabled, sort_order, decimals

  Builtin keys: body_weight, body_fat_pct, neck, shoulders, chest, waist,
                abdomen, hips, bicep_left, bicep_right, forearm_left,
                forearm_right, thigh_left, thigh_right, calf_left, calf_right,
                resting_hr

measurement_entry   id, type_id, value (canonical: kg / cm / percent),
                    recorded_at, local_date, note?
                    UNIQUE(type_id, local_date)   -- one value per type per day,
                                                  -- upsert on conflict

progress_photo      id, recorded_at, local_date, file_name,
                    pose enum: FRONT, SIDE, BACK, OTHER, note?
                    -- stored in filesDir/photos/, never in MediaStore
```

Custom measurement types are just rows with `is_builtin = false` — same table, same charts, no special casing.

### 3.5 Records and support tables

```
personal_record     id, exercise_id,
                    record_type enum: MAX_WEIGHT, MAX_REPS, BEST_1RM,
                                      BEST_SET_VOLUME, BEST_SESSION_VOLUME,
                                      MAX_DURATION, MAX_DISTANCE
                    value, workout_set_id?, workout_id, achieved_at
                    -- full history retained, not just current best

rep_max             exercise_id, reps (1..12), weight_kg, workout_set_id,
                    achieved_at
                    -- the "rep max table" on the exercise detail screen

plate_inventory     id, plate_kg, count, is_enabled
bar_inventory       id, name, weight_kg, is_default

user_profile        (single row) height_cm, birth_date, sex,
                    weight_unit, length_unit, distance_unit,
                    default_rest_sec, first_day_of_week,
                    one_rm_formula, theme, dynamic_colour,
                    count_warmups_in_volume, secondary_muscle_credit
```

### 3.6 Indices

Non-negotiable for chart performance at 5+ years of data:

```
workout(started_at DESC), workout(local_date)
workout_exercise(workout_id), workout_exercise(exercise_id)
workout_set(workout_exercise_id)
workout_set(estimated_1rm_kg) WHERE is_completed = 1
measurement_entry(type_id, local_date)
personal_record(exercise_id, record_type, achieved_at DESC)
exercise(name_normalised)
```

---

## 4. Calculations

### 4.1 Estimated 1RM

User-selectable formula, default Epley. Only computed for `reps` in 1..12; above that the estimate is noise and should be suppressed rather than shown.

```
Epley     : w × (1 + r/30)
Brzycki   : w × 36 / (37 − r)
Lombardi  : w × r^0.10
O'Conner  : w × (1 + r/40)
```

For `WEIGHTED_BODYWEIGHT`, effective load = `added_weight + (bodyweight × leverage_factor)`, where `leverage_factor` defaults to 1.0 for pull-ups/dips and is a per-exercise override. Use the bodyweight entry nearest in time to the set.

### 4.2 Volume

- Set volume = `weight_kg × reps`
- Session volume = Σ over completed working sets
- Muscle-group volume = full credit to `primary_muscle`, and `secondary_muscle_credit` (default 0.5) to each secondary

### 4.3 Weekly hard sets per muscle

The single most useful programming metric. Count completed non-warmup sets per muscle per ISO week, with fractional credit for secondaries. Chart with reference bands at 10 (maintenance) and 20 (upper productive range) sets/week — presented as reference lines, not prescriptions.

### 4.4 Body composition

- **Navy method body fat** (derived, optional, shown alongside any manually entered value, never overwriting it):
  - Men: `495 / (1.0324 − 0.19077·log10(waist − neck) + 0.15456·log10(height)) − 450`
  - Women: `495 / (1.29579 − 0.35004·log10(waist + hips − neck) + 0.22100·log10(height)) − 450`
  - All measurements in cm.
- Lean mass = `weight × (1 − bf/100)`; fat mass = remainder
- FFMI = `lean_kg / height_m²`; normalised FFMI = `FFMI + 6.1 × (1.8 − height_m)`
- Bodyweight trend: 7-day exponential moving average plotted over raw points. Day-to-day scale noise is 1–2 kg of water; the raw series alone is actively misleading and the smoothed line should be the visual default.

### 4.5 Plate calculator

Given target weight, bar weight, and `plate_inventory`, solve greedily for plates per side, respecting available counts. Show the exact achievable weight and the delta when the target isn't reachable.

---

## 5. Screens

### 5.1 Navigation

Bottom bar, five destinations: **Workout** · **History** · **Measure** · **Stats** · **Exercises**. Settings lives behind an icon in the top app bar.

### 5.2 Workout (home)

- Large "Start empty workout" button
- "Resume workout" banner if `is_in_progress`
- Routine folders, collapsible, drag to reorder
- Routine cards: name, exercise summary line, last-performed relative date, overflow → start / edit / duplicate / share as text / delete

### 5.3 Active workout — the critical screen

Layout: sticky header with elapsed timer, volume, set count, and **Finish**; then a vertical list of exercises, each with a set table.

Set row columns: `#` · `Previous` · `kg` · `Reps` · `RPE` (optional) · ✓

Behaviour requirements:

- The **Previous** column shows the corresponding set from the most recent session containing that exercise, and tapping it fills the row. This is the highest-value single interaction in the whole app.
- Checking a set starts the rest timer automatically and commits to the DB immediately (no "save" step anywhere).
- Long-press a set row → change type (warmup / drop / failure / myorep), or delete.
- Swipe left on a set → delete with undo snackbar.
- Numeric keyboards with `+`/`−` steppers; weight step follows unit (2.5 kg / 5 lb, configurable).
- Supersets: multi-select exercises → group; grouped exercises get a coloured left edge and share a rest timer.
- Rest timer: foreground-service notification with countdown, `+15s` / `−15s` / skip actions, vibration and optional sound at zero, and it must survive the screen being locked.
- Per-exercise overflow: reorder, replace exercise, add note, set rest time, open plate calculator, view history inline.
- **PR detection is live.** When a completed set beats a stored record, the row gets a badge immediately.
- Crash safety: the in-progress workout is a normal DB row from the first tap, so recovery is automatic rather than a special path.
- Optional keep-screen-on during a session.
- Finish → summary sheet: duration, volume, sets, PRs earned, muscle-group breakdown, notes field.

### 5.4 History

- Reverse-chronological list grouped by month, plus a calendar view toggle with dots on trained days
- Header stats: workouts this week/month, current streak, total volume
- Workout detail: full read-only rendering, with edit mode enabling the same set table as the active screen
- Overflow: duplicate as new workout, save as routine, delete
- Retroactive edits must recompute denormalised totals and re-derive affected PRs

### 5.5 Exercises

- Search with normalised matching (case- and accent-insensitive)
- Filter chips: muscle group, equipment, custom-only, recently used
- Create/edit custom exercise: name, type, equipment, primary + secondary muscles, default rest, notes
- **Exercise detail**, tabbed:
  - *About* — muscles, equipment, notes
  - *History* — every session containing it, sets rendered compactly
  - *Charts* — estimated 1RM, heaviest weight, best set volume, session volume, total reps
  - *Records* — current PRs plus the 1–12 rep-max table

### 5.6 Measure

- Today card: quick-add weight and body fat
- List of enabled measurement types, each showing latest value, delta since last, and a sparkline
- Tap a type → full history: chart, editable table, add entry with backdating
- Manage types: enable/disable builtins, create custom, reorder
- Photos: timeline grid by date, side-by-side comparison of any two, pose filter
- Derived card: Navy body fat, lean/fat mass, FFMI, BMI — each labelled as derived with its inputs listed

### 5.7 Stats

Global range selector (1M / 3M / 6M / 1Y / All / custom) applied to every chart on the screen.

- **Consistency** — workouts per week bar chart, GitHub-style year heatmap, streak, average duration
- **Volume** — total volume over time; stacked by muscle group; muscle-group share donut
- **Weekly hard sets per muscle** — grouped bars with reference bands (§4.3)
- **Strength** — multi-select up to 5 exercises, overlay normalised estimated 1RM
- **Body** — weight with EMA overlay; body fat; circumferences multi-series; optional dual-axis overlay of bodyweight against a chosen lift's 1RM
- Every chart: pinch/drag zoom, tap-to-inspect value tooltip, long-press → "export this chart's data as CSV"

### 5.8 Settings

Units · theme and dynamic colour · default rest · 1RM formula · warmups in volume · secondary-muscle credit · first day of week · plate and bar inventory · export · share and email destinations · import · auto-backup · app lock (biometric) · optional SQLCipher encryption · about and licences.

---

## 6. Export and import

### 6.1 XLSX report — the reason this app exists

Written with fastexcel to a user-chosen location via `ACTION_CREATE_DOCUMENT`. Sheets:

| Sheet | Grain | Notes |
|---|---|---|
| `Sets` | one row per logged set | **the pivot table** — fully denormalised: date, ISO week, workout name, exercise, primary muscle, equipment, set index, set type, weight kg, weight lb, reps, RPE, volume, est 1RM, is PR |
| `Workouts` | one row per session | date, name, duration, volume, sets, PR count, notes |
| `Exercises` | catalogue | with lifetime totals per exercise |
| `Measurements` | long format | date, type, value, unit — long format pivots better than wide |
| `Measurements_Wide` | one row per date | convenience for direct charting |
| `Records` | PR history | |
| `Routines` | template definitions | |
| `Summary` | weekly rollup | week, workouts, volume, sets per muscle group, avg bodyweight |
| `Meta` | app version, schema version, export timestamp, unit settings | |

Formatting: real date cells (not strings), numeric cells (not strings), frozen header row, autofilter on every sheet, sensible column widths. Getting types right here is what makes the file pivot-ready.

### 6.2 CSV

Per-entity UTF-8 with BOM (so Excel on Windows reads Greek and Cyrillic correctly), RFC 4180 quoting. Same schemas as the XLSX sheets. Offered as a ZIP bundle or individually.

### 6.3 JSON full backup

Versioned, complete, lossless round trip — this is the actual backup format; XLSX is for analysis only.

```json
{
  "format": "kroton-backup",
  "formatVersion": 1,
  "schemaVersion": 7,
  "appVersion": "1.0.0",
  "exportedAt": "2026-07-31T09:14:00Z",
  "profile": { },
  "exercises": [ ],
  "routineFolders": [ ], "routines": [ ],
  "workouts": [ ],
  "measurementTypes": [ ], "measurements": [ ],
  "records": [ ],
  "photos": [ ]
}
```

Delivered as `.kroton` (a ZIP containing `backup.json` + `photos/`). Restore offers **merge** (dedupe by `started_at` + name) or **replace**. Always take a safety snapshot of the current DB before a restore, and offer one-tap rollback.

### 6.4 Auto-backup

WorkManager daily job at a user-set hour, writing to a persisted SAF tree URI, keeping the last *N* files (default 7) and pruning older. Requires charging: no. Requires idle: no.

### 6.5 Delivering exports off the phone — without breaking the no-network rule

This is the part that makes the zero-permission design work rather than get in the way. **Kroton never uploads anything. It hands the file to another app, and that app does the networking.** Gmail, Drive, Sheets, Telegram, Nextcloud, a USB-C drive — all of them work, and none of them require Kroton to hold `INTERNET`.

The mechanism is `ACTION_SEND` with a `FileProvider` content URI:

```kotlin
val uri = FileProvider.getUriForFile(
    context, "$packageName.fileprovider", exportFile
)
val intent = Intent(Intent.ACTION_SEND).apply {
    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    putExtra(Intent.EXTRA_STREAM, uri)
    putExtra(Intent.EXTRA_SUBJECT, "Kroton export — 2026-07-31")
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
startActivity(Intent.createChooser(intent, "Send export"))
```

Two details that matter:

- **The MIME type determines which apps appear in the chooser.** Send the correct OOXML type above and Sheets, Excel, and Drive all offer themselves. Send `application/octet-stream` and you get a much worse chooser.
- **Prefill `EXTRA_EMAIL` with the user's own address** (a settings field) so "mail it to myself" is genuinely one tap. Store it locally; it is never transmitted by Kroton.

Concrete destinations, all via the same share sheet:

| Destination | How it works | Notes |
|---|---|---|
| **Gmail / any mail app** | share sheet → Gmail → attachment prefilled | subject and recipient prefilled from settings |
| **Google Drive** | share sheet → Drive → uploads the `.xlsx` | |
| **Google Sheets** | share to Drive, then open with Sheets — Drive converts the `.xlsx` to a native Sheet on open | works today with no API integration |
| **Nextcloud / Syncthing / Dropbox** | same share sheet | the auto-backup SAF folder (§6.4) can also point at a synced directory, which makes backup fully automatic |
| **USB / SD card / laptop** | `ACTION_CREATE_DOCUMENT` writes wherever the user picks | |

Provide three entry points in the UI: **Save to…** (SAF), **Share…** (chooser), and **Email to myself** (chooser pre-filtered to mail apps). Same generated file behind all three.

**On a direct Google Sheets API integration — don't.** Writing rows straight into a Sheet would require `INTERNET`, an OAuth client, Google's sensitive-scope verification process, a hosted privacy policy, and an annual security review. It would cost weeks, it would put a Google sign-in in a no-account app, and it would delete the one claim that distinguishes Kroton from thirty other trackers. The Drive-converts-XLSX route gives the user a real Google Sheet with none of that. If it's ever revisited, it must be an optional separate flavour of the app, never the default build.

Write exports to `cacheDir/exports/` and prune files older than 24 hours on launch, so shared files don't accumulate.

### 6.6 Import from other apps

- **Hevy CSV** — their export is one row per set with roughly: `title, start_time, end_time, description, exercise_title, superset_id, exercise_notes, set_index, set_type, weight_kg, reps, distance_km, duration_seconds, rpe`. Verify against a real export before coding; treat the header row as authoritative and map by column name, not position.
- **Strong CSV** — similar shape, different headers.
- Import flow: pick file → parse → show a mapping screen for unmatched exercise names (fuzzy-suggest, or create as custom) → preview counts → confirm.

Getting Hevy import right on day one means you can switch over immediately instead of running both apps in parallel for months.

### 6.7 Platform backup and device migration

**Yes — and it costs no permissions.** Android Auto Backup and iOS iCloud Backup are performed *by the operating system*, not by the app. The system backup service reads the app's files and transfers them. Kroton never opens a socket, `INTERNET` stays out of the manifest, and the user still gets seamless migration to a new phone: sign in during setup, install Kroton, history is already there.

**Android.** Declare `android:allowBackup="true"` with `android:dataExtractionRules` (API 31+) or `android:fullBackupContent` (below that). Quota is 25 MB per app. The transfer runs roughly daily when the device is idle, charging, and on an unmetered network, and restores automatically during new-device setup or on reinstall. Since Android 9 the payload is encrypted with a key derived from the device lockscreen secret, so Google cannot read it — worth stating plainly in the README, because it is consistent with the rest of the privacy position rather than a hole in it.

Three things that will bite:

1. **Never let Auto Backup copy the raw SQLite files.** Backing up `kroton.db`, `-wal` and `-shm` while the database is open restores an inconsistent or outright corrupt database. Exclude all three explicitly.
2. **Back up a snapshot instead.** Implement a `BackupAgent` whose `onFullBackup` writes a gzipped JSON export (§6.3) to a dedicated directory and backs up only that file. A heavy user at 60,000 sets is a few hundred KB gzipped — comfortably inside the quota, which raw data is not. On restore, the app finds the snapshot on first launch, imports it, and deletes it.
3. **Exclude progress photos.** They will blow the 25 MB quota on their own and silently kill the entire backup, including the training history. Say so in the UI: photos are the user's to export manually.

**iOS.** iCloud Backup automatically includes the app's `Documents/` directory. Put the same gzipped snapshot there and mark caches and progress photos `isExcludedFromBackup` so they don't consume the user's iCloud quota. No entitlement, no CloudKit container, no code beyond directory hygiene.

**Migration is not sync.** Platform backup gets history onto a *new* phone. It does not keep two phones in step, and it fires roughly daily, so it is not a guarantee that this morning's session survives a phone dropped in the Saronic Gulf. Real multi-device sync means CloudKit or a server, network permission, and accounts — a different product, and one that would cost the app its defining property. Keep the scheduled local export (§6.4) as the actual safety net and present platform backup as the convenience it is.

---

## 7. Exercise content and imagery

Two separate problems with very different risk profiles: the **metadata** (names, muscles, equipment) and the **imagery**. Solve them separately.

### 7.1 Metadata — use free-exercise-db

`yuhonas/free-exercise-db` — over 800 exercises as schema-validated JSON, released under the **Unlicense** (public domain, no attribution burden). The fields map onto §3.1 almost directly:

| free-exercise-db | Kroton |
|---|---|
| `name` | `exercise.name` |
| `primaryMuscles[]` | `primary_muscle` (take the first, review the rest by hand) |
| `secondaryMuscles[]` | `exercise_secondary_muscle` |
| `equipment` | `equipment` enum — flat string, needs a mapping table |
| `force` | `force` |
| `mechanic` | `mechanic` |
| `instructions[]` | `exercise.instructions` |
| `level`, `category` | filters; partially informs `exercise_type` |

Budget about a day for the curation pass. Their muscle taxonomy is coarser than §3.1 — no separate deltoid heads, for instance — so a hand-written mapping table is needed. And 800 entries is too many for a picker: cull to roughly 300 visible, marking the long tail `is_archived = true` so it stays searchable without cluttering the list.

**wger** (`wger-project/wger`, AGPL app, exercise data CC-BY-SA) is the secondary source, mainly for gaps and for non-English names — it carries community translations including Greek and Russian, which directly serves the localisation target in §8. Attribution required, derivative datasets stay share-alike; record it in `NOTICE`.

### 7.2 No bundled exercise imagery — settled

Kroton ships **no exercise photographs, illustrations, or video**. Not in v1, not later. This is a decision, not a deferral, and it should be stated in the README so nobody opens a PR to add 200 MB of JPEGs.

The reasons, in order of weight:

1. **Provenance.** The obvious source — free-exercise-db — carries images whose ownership is undocumented; at least two separate issues asking who holds the rights were closed without an answer. A maintainer can only grant rights they actually hold. For a GPL project on F-Droid maintained over years, unverifiable image provenance is a liability that surfaces as a takedown long after everyone has forgotten where the files came from.
2. **Size.** 800 exercises at two photos each is 100–200 MB against a target app size under 15 MB. It would be the largest thing in the repository by two orders of magnitude, for the least valuable feature.
3. **Value.** For someone who already trains, stock form photos are decoration. The picker exists to *find* "incline dumbbell press", not to teach it. The question a logged exercise actually raises is "what does this work?" — and that is answered far better by §7.3 than by a photograph of a stranger.

wger's images are legally clean (contributors' own work or CC-BY-SA 4.0) and are the only source that would be defensible. They are still not being used, because reasons 2 and 3 stand regardless of licensing.

### 7.3 The muscle map — anatomical écorché

With no photography, this asset carries every "what does this work" question in the app. It must be a **realistic anatomical rendering** — a recognisable écorché figure with true muscle contours — not a schematic of blocks. A wireframe of rounded rectangles reads as a placeholder and undermines confidence in everything around it.

**Definition of realistic, for this project:**

- Muscle bellies follow real form: pectoralis major with distinguishable clavicular and sternal heads, rectus abdominis with tendinous intersections, deltoid with three separable heads, quadriceps with rectus femoris, vastus lateralis and medialis visible as distinct masses.
- Fibre direction is implied by the outline. The lat's fan, the oblique's diagonal and the pec's convergence toward the humerus should be legible without labels.
- Proportion is anatomical, not stylised. Roughly 7.5 heads tall.
- Depth is carried by contour, not shading. Flat fills only — the whole figure is recoloured at runtime, so gradients and baked shadows would fight the tint.

It should look like a plate from an anatomy atlas that happens to be interactive, not like an app illustration.

**Sourcing — this is the part to get right.**

Preferred base: OpenStax *Anatomy & Physiology*, figure 1105, "Anterior and Posterior Views of Muscles". It is a full-body labelled écorché under **CC BY 4.0** — attribution only, no share-alike. That matters: the CC BY-SA derivatives on Wikimedia Commons would drag a copyleft obligation onto the artwork, and while that is survivable in a GPL project, plain CC BY is materially cleaner and lets the asset be reused without conditions propagating. Attribution goes in `NOTICE` and in the in-app licences screen.

Commons is **not uniformly licensed** — CC BY 4.0, CC BY-SA 3.0, CC BY-SA 4.0 and public domain files sit side by side in the same category. Check and record the licence of every individual file used. Never assume from the category.

The base is raster, so the paths must be traced. That is real work — budget two to three days for both figures, tracing in Inkscape or Illustrator, then hand-correcting the ~20 target regions so each is a single clean closed path. Alternatively commission it: a competent illustrator produces both views for a few hundred euros, and the project then owns the artwork outright, which removes the attribution chain entirely. For an asset this central, that is defensible spending.

**Assets.** `muscle_map_front.svg` and `muscle_map_back.svg`, sharing one normalised viewBox so the figures align. Beneath the muscle layer sits a static body layer — head, hands, feet, joints — in a neutral tone, so untinted areas read as a body rather than as holes.

Each targetable path carries an `id` equal to a `MuscleGroup` enum value in lower snake case (`front_delts`, `lower_back`, `hamstrings`). Anatomical detail that is *not* targetable (tendons, sartorius, minor stabilisers) stays on the base layer as visual texture with no id. Realism is served by drawing more muscles than the app tints.

**The enum-to-path contract is enforced in CI.** A test asserts every `MuscleGroup` value except `FULL_BODY` and `CARDIO` resolves to a path in at least one file, and that no targetable id is absent from the enum. Asset-to-schema drift is otherwise silent: someone adds `ADDUCTORS`, nothing renders, nobody notices for six months.

**Four features from one asset:**

| Surface | Tinting |
|---|---|
| Exercise detail | primary muscle at full tint, secondaries at ~40% |
| Workout summary | every muscle touched in the session |
| Stats heatmap | graded by weekly hard sets per muscle (§4.3) |
| Library filter | tap a region to filter the picker |

The heatmap is the one worth getting right. Tinting the body by weekly set volume is the visual that actually changes what gets programmed next week, and it is largely absent from the commercial trackers.

**Tint ramp.** Five steps on a single hue from the neutral untrained state to `ground`. Bind the steps to the volume landmarks in §4.3 rather than to a linear scale, so the colour carries meaning. Colour alone is never sufficient — pair every map with the numeric legend, and honour the "view as table" affordance required in §8.

**Edge cases.**
- `FULL_BODY` and `CARDIO` have no region. Render a badge beside the figure rather than tinting everything, which reads as noise.
- The map is **not lateralised**: one `biceps` region, not left and right. Measurements are per-side (§3.4); muscle volume is not. Don't let the two taxonomies bleed together.
- Muscles a front/back view cannot show honestly — deep hip rotators, and to a degree `ADDUCTORS` — get an approximate region rather than being silently absent.
- The figure is anatomical, so it is unclothed by definition. Keep it clearly a medical écorché: no face, no gendered secondary characteristics, muscle-only rendering. This is both the tasteful choice and the one that avoids store-review ambiguity.

**Implementation.**
- Store raw SVG path-data strings in `:core:model` so iOS consumes the same source of truth. Extract them from the SVGs at build time into a generated Kotlin file rather than parsing XML at runtime on two platforms.
- On Android, parse once at startup with `PathParser`, cache the `Path` objects keyed by muscle, draw with Compose `Canvas`. Never re-parse per frame — the stats screen will visibly stutter.
- Realistic contours cost more than blocks: expect **150–400 KB** for both files after running them through SVGO and reducing path precision to 1–2 decimal places. Still negligible against 150 MB of photographs, and worth an explicit size budget in CI.
- Ship both figures at a single resolution; SVG scales, and there is no raster fallback.

### 7.4 Deliberately excluded

- Bundled photos, illustrations, animations, video (§7.2).
- A downloadable image pack. Rejected: it reintroduces either a network dependency or a licensing question the project would then own.
- **Possible later, and categorically different:** letting users attach *their own* form-check photos or video to an exercise. That is user content, not shipped content — zero licensing exposure, and a clip of your own squat beats any stock image. Stored app-private, excluded from platform backup (§6.7). Backlog, not v1.

---

## 8. Non-functional requirements

**Performance** — cold start to interactive under 1.0 s on a mid-range device. Set-entry keystroke to rendered value under 16 ms. History and chart screens must stay smooth at 2,000 workouts / 60,000 sets; generate that dataset in an instrumented test and treat it as a gate. Chart queries return pre-aggregated rows from SQL, never 60,000 objects into memory.

**Reliability** — no user action may lose data. Every write is committed on the spot. Room migration tests are mandatory for every schema version, including a test that exports schema JSON and asserts it matches the checked-in file.

**Accessibility** — TalkBack labels on every interactive element, especially set rows and the ✓ control. Support 200% font scale without clipping. Minimum 48 dp touch targets. Charts have a "view as table" affordance.

**Localisation** — `en` at launch; `el` and `ru` shortly after. No concatenated strings; plurals via `plurals.xml`; RTL-safe layouts.

**Theming** — dark by default, Material You dynamic colour on Android 12+, monochrome themed icon for Android 13+.

**Privacy** — no analytics, no crash reporting SDK, no ads, no third-party telemetry of any kind. Crash logs written to a local file the user can export manually.

**Testing** — unit tests for all of §4 with reference values; DAO tests against in-memory Room; Compose UI tests for the active-workout flow; export/import round-trip property test; screenshot tests on the main screens.

**CI (GitHub Actions)** — ktlint + detekt, unit tests, instrumented tests on an emulator matrix, assemble release, manifest permission check (§2.3), reproducible-build verification for F-Droid.

---

## 9. Roadmap

| Milestone | Scope | Definition of done |
|---|---|---|
| **M0** Foundations | Modules, Room schema, free-exercise-db import + curation, écorché muscle map + CI contract test, design system | App builds, DB migrates, ~300 curated exercises browsable, every muscle group tints correctly on both figures |
| **M1** Log a workout | Empty workout, set table, previous-column, rest timer, finish + summary | A full session can be logged end to end |
| **M2** Routines | Folders, CRUD, start-from-routine, save-workout-as-routine | Routine → session → history round trip |
| **M3** History | List, calendar, detail, edit past workouts, PR engine | Retroactive edit recomputes PRs correctly |
| **M4** Measurements | Types, entries, photos, derived metrics | Weight, body fat and all circumferences tracked |
| **M5** Stats | All charts in §5.7, muscle-map heatmap | 60k-set dataset renders smoothly |
| **M6** Data portability | XLSX, CSV, JSON backup/restore, auto-backup, share sheet + email, platform BackupAgent, Hevy import | Backup → wipe → restore is byte-identical; export lands in Google Sheets in three taps |
| **M7** Release | Polish, a11y pass, `el`/`ru`, F-Droid metadata, README | v1.0.0 on F-Droid + GitHub Releases |
| **M8** iOS | SwiftUI over `:core:*` | Feature parity for log + charts + export |

Realistic solo effort: M1 is roughly a third of the total work. Build M1 first and use it daily before writing M2 — nothing exposes bad set-entry ergonomics like actually being mid-set with chalky hands.

---

## 10. Repository layout

```
kroton/
  app/  core/  feature/
  fastlane/metadata/android/en-US/   # F-Droid + Play listings
  docs/
    SPEC.md  DATA_MODEL.md  EXPORT_FORMAT.md  CONTRIBUTING.md
  .github/workflows/{ci.yml,release.yml}
  LICENSE            # GPL-3.0
  README.md
```

`EXPORT_FORMAT.md` should document the JSON schema as a public contract — it's what lets other people write importers and analysis scripts against your data, which is the whole point of open-sourcing this.

---

## 11. Open decisions

1. **Room-KMP vs SQLDelight** — Room is more familiar and its KMP support is now stable; SQLDelight is more mature cross-platform. Decide before M0 ends; it is expensive to reverse.
2. **Vico vs hand-rolled Canvas charts** — start with Vico, keep the chart API behind your own interface so swapping is contained.
3. **RPE vs RIR** — support both, one visible at a time, user setting.
4. **Multi-profile support** — deferred; but keep a nullable `profile_id` on `workout` and `measurement_entry` from v1 so adding it later isn't a painful migration.
5. **Trace vs commission the écorché.** Tracing OpenStax figure 1105 (CC BY 4.0) costs two to three days and an attribution line; commissioning costs a few hundred euros and leaves the project owning the artwork. Decide during M0 — it is the asset everything visual depends on.
6. **SQLCipher** — adds ~4 MB and some write latency. Ship as an opt-in toggle rather than default.
