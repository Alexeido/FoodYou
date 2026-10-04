# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Food You is a Kotlin Multiplatform (Android + iOS) food diary and nutrition tracker built with
Compose Multiplatform. This repository (`Alexeido/FoodYou`) is a personal fork of the upstream
[maksimowiczm/FoodYou](https://github.com/maksimowiczm/FoodYou) project — package names and some
infrastructure (e.g. changelog metadata paths) still reference `com.maksimowiczm.foodyou`.

## Common commands

Build tooling is Gradle (via the wrapper) plus a `justfile` for a few workflows.

- Assemble debug build: `./gradlew assembleDebug`
- Run all tests (commonTest, runs on JVM): `./gradlew test` (or `./gradlew jvmTest` /
  `./gradlew testDebugUnitTest` for the Android variant)
- Run a single test class: `./gradlew test --tests "com.maksimowiczm.foodyou.<package>.<ClassName>"`
- Run Android instrumented tests: `./gradlew connectedAndroidTest`
- Format all Kotlin files with ktfmt (kotlinlang style): `just format` (requires `$KTFMT_JAR` env
  var pointing at the ktfmt jar)
- Build + zipalign + sign a release APK: `just release` (requires `foodyou.keystore` and
  `zipalign`/`apksigner` on PATH)
- Build + sign a preview APK: `just preview`

There is no separate lint/format-check Gradle task wired up in this repo beyond `just format`;
match the existing code style (see Code style below) when editing by hand.

## Module structure

Gradle modules are intentionally kept minimal (see
[docs/decision-log/0002-minimize-gradle-modules.md](docs/decision-log/0002-minimize-gradle-modules.md)):

- `app` — the entire application (UI, domain, infrastructure). This is where almost all work happens.
- `shared/resources` — static resources (images, fonts, strings) shared across targets.
- `shared/barcodescanner` — barcode scanning, isolated because it depends on the older Android XML
  view system rather than Compose.

Targets: `androidTarget`, `iosArm64`, `iosSimulatorArm64`. Android is the primary/only actively
tested target in CI.

Outside the KMP app:

- `wear` — Wear OS companion (plain Android + Wear Compose). Pairs with a sync account through a
  6-digit code and talks to the sync server directly. Build: `./gradlew :wear:assembleDebug`.
- `sync-server/` — self-hosted sync server (FastAPI + SQLite, Python), with a web admin panel and
  OAuth for the MCP. Tests: `python -m pytest` inside it.
- `mcp-server/` — MCP server giving AI assistants read/write access to the diary through the sync
  server. Tests run the sync server in-process.
- `docs/sync/protocol.md` — the sync protocol (document kinds, last-writer-wins per field) shared by
  the app's `sync` package, the watch and the MCP. Keep it in step with
  `sync/infrastructure/SyncLocalStore.kt`.

## Architecture

### Feature-based, layered packages

Code under `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/` is organized by **feature**, not
by technical layer, e.g. `fooddiary`, `food`, `goals`, `settings`, `sponsorship`, `poll`,
`importexport`, `changelog`. The top-level `app` package holds app-wide wiring (DI, navigation,
Room database setup, root UI shell), and `common` holds cross-feature utilities.

Within each feature package, code is further split into layers:

- `domain` — entities, repository interfaces, use cases, domain events. No framework/infrastructure
  dependencies.
- `infrastructure` — implementations of domain interfaces: Room DAOs/entities, network clients
  (Ktor), Koin DI modules for the feature (usually a `<Feature>DomainModule.kt` /
  infrastructure module function).
- `ui` (under `app/ui/<feature>`) — Compose screens, view models, UI-only state/components for that
  feature.

Some features (e.g. `food`) have deeper sub-features (`food/search`) that repeat the same
`domain`/`infrastructure` split.

### Dependency injection

Koin is used throughout. Each feature exposes a `<feature>Module()` / `<Feature>DomainModule.kt`
function that registers its bindings; these are aggregated in
[app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/di/AppModule.kt](app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/di/AppModule.kt)
and initialized via `InitKoin.kt`. When adding a new repository/use case, follow the existing
pattern: bind the interface from `domain` to its `infrastructure` implementation inside that
feature's Koin module function, not directly in `AppModule.kt`.

### Cross-feature communication: integration events

Features avoid depending directly on each other's internals by publishing/subscribing to events via
an `EventBus` (see `common/domain/event/EventBus.kt`, `IntegrationEvent.kt`,
`IntegrationEventHandler.kt`). Feature-level domain events (e.g.
`fooddiary/domain/event/FoodDiaryEntryCreatedEvent.kt`) are the mechanism for side effects that
cross feature boundaries (e.g. updating goals when a diary entry is created).

### Data layer

- Room is the persistence layer (`androidx.room`), with KSP-generated code; schemas are exported to
  `app/schemas`. Migrations live under
  `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/app/infrastructure/room/migration`.
- Food data is sourced from multiple external databases — Open Food Facts, USDA FoodData Central,
  and the Swiss Food Composition Database — each with its own `infrastructure` package under
  `food/infrastructure/` (e.g. `openfoodfacts`, `usda`). Per
  [docs/decision-log/0003](docs/decision-log/0003-create-local-mirror-for-external-food-databases.md),
  the app maintains a local mirror of these external databases and replicates their search/filter
  behavior locally rather than treating them as a single flattened food table.
- Networking uses Ktor (`ktor-client-core` + `content-negotiation` + kotlinx.serialization), with
  OkHttp as the Android engine and Darwin on iOS.

### UI

- Compose Multiplatform with Material 3 (including expressive/experimental Material3 APIs, opted
  into at the module level). Theming uses `material-kolor` for Material You color generation.
  Navigation uses `androidx.navigation` (Compose Navigation) with typed nav destinations under
  `app/navigation`.
- Compose resources (strings, images) are generated with a public `R`-style class
  (`compose.resources { publicResClass = true }`), package
  `com.maksimowiczm.foodyou.app.generated.resources`.

### Kotlin language features in use

The project opts into `ExpectActualClasses` and `ContextParameters` language features, and enables
the Kotlin return-value checker (`-Xreturn-value-checker=check`) — respect `@CheckReturnValue`-style
contracts rather than discarding results silently.

## Decision log

Significant architectural decisions are recorded under `docs/decision-log/` in a lightweight
Problem/Decision/Rationale/Consequences format. Check there before proposing a large structural
change (e.g. splitting modules further, changing the data-mirroring approach) — it may already have
been discussed.

## Release process

See [docs/development/release.md](docs/development/release.md) for the full release checklist
(version bump in `libs.versions.toml`, changelog entries in `metadata/en-US/changelog/` and
`StaticChangelog.kt`, tagging, signing).
