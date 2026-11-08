# Changes relative to main

> [!IMPORTANT]
> **Unofficial fork**
>
> This repository is an independent fork of SimpleScore. It is not affiliated with or maintained by the original author. Changes in this fork are maintained independently of the original project. Please report issues related to these changes in this repository.

This document describes the cumulative changes in the current development state
relative to local `main` at commit `10995df`, including earlier changes on `dev`.
The project version is `4.3.2-build.1`; this is a development change record, not an
announcement of a published release. Validation results below were recorded on
2026-11-08.

## Project structure and distribution

The project now builds separate Bukkit and Fabric server adapters around the
existing platform-independent API and core:

```text
api/
core/
adapter/
  bukkit/
    worldguard/
      common/
      v6/
      v7/
  fabric/
```

- Move the former `bukkit/` module and its WorldGuard submodules to
  `adapter/bukkit/`, updating Gradle project references.
- Preserve existing Bukkit package names, the plugin entrypoint, commands, packet
  implementations, WorldGuard integrations, and configuration format. Of the 61
  existing files under `bukkit/` on the comparison baseline, 45 move unchanged and
  16 also contain content changes; none is missing.
- Keep the source files in `api/` and `core/src/` unchanged. Adjust the core build
  and generated project information as described below.
- Add the `adapterJars` task. The root `shadowJar` and `assemble` tasks also build
  both adapter distributions.
- Write distributable artifacts to the repository-root `target/` directory:
  - `SimpleScore-<version>-bukkit.jar`
  - `SimpleScore-<version>-fabric.jar`
- Ignore `target/` in Git and remove it when running the root `clean` task.

## Java and version management

`gradle/libs.versions.toml` is the authoritative source for project, toolchain,
plugin, and direct dependency versions. Gradle Java and Kotlin toolchains,
GitHub Actions, and JitPack read the catalog's Java version.

**Java 25 is now required.** The previous Java 8 bytecode target is no longer
supported. Old Minecraft server versions require an implementation capable of
running on Java 25; the presence of compatibility code does not establish that
every historical server version works.

| Component | Main baseline | Current development state |
| --- | --- | --- |
| Project | `4.3.2-dev` | `4.3.2-build.1` |
| Gradle wrapper | `9.7.0` | `9.8.2` |
| Kotlin | `2.4.10` | `2.4.21` |
| Bukkit compile dependency | `org.bukkit:bukkit:1.8.8-R0.1-SNAPSHOT` | `org.spigotmc:spigot-api:26.3-R0.1-SNAPSHOT` |
| Netty transport | `4.1.97.Final` | `4.2.20.Final` |
| PlaceholderAPI | `2.11.6` | `2.12.3` |
| LuckPerms API | `5.4` | `5.5` |
| Adventure | `4.25.0` | `5.2.0` |
| WorldGuard 7 | `7.0.0` | `7.0.21` |
| Objenesis | `3.5` | `3.6` |

Dokka `2.2.0`, Shadow `9.6.1`, Foojay `1.0.0`, WorldGuard 6 `6.2`, and SemVer
`2.1.0` remain at their baseline versions. Foojay's version is now resolved from
the catalog as well. The newer compile-only dependencies do not upgrade libraries
or plugins installed on a server.

## Bukkit, Paper, and Folia

### Player updates and scheduling

- Add player-specific scheduling and thread-ownership checks to the scheduler
  abstraction.
- Process player data, placeholder evaluation, and scoreboard packets on the
  Bukkit main thread or the player's Folia entity scheduler.
- Retain the asynchronous animation-clock and dispatch option, while tracking
  pending updates to avoid overlapping queued work for the same player.
- Track line revisions per viewer so delayed updates retain earlier render
  requests, and synchronize animation advancement with rendering.
- Add null checks for movement and teleport destinations and configuration
  values required by the newer Spigot API.

### PlaceholderAPI integration

- Introduce an optional `PapiHook` with registration and cleanup on PAPI
  enable/disable events.
- Use the fixed expansion identifier `simplescore`.
- Separate external placeholder evaluation from built-in replacements.
- Preserve unresolved tokens when an expansion throws a runtime exception or
  linkage error. Pause calls to the failing expansion for 30 seconds and limit
  repeated error logging.
- Skip external PAPI evaluation when another plugin invokes replacement outside
  the player's server thread.

This protection cannot detect failures swallowed by an expansion, stop blocking
scripts, or isolate native engine crashes.

## New Fabric server adapter

The Fabric adapter targets Minecraft `26.4-snapshot-7` with Java 25, Fabric Loader
`0.20.1`, Fabric API `0.165.4+26.4`, Loom `1.19.4`, and Fabric Installer `1.1.2`.
Fabric Loader identifies this snapshot as `26.4-alpha.7`; the catalog therefore
stores separate launcher and normalized runtime version identifiers.

- Implement the platform lifecycle, viewer management, and per-player scoreboard
  renderer using native Minecraft packets without adding objectives to the
  shared server scoreboard.
- Reuse core animations, effects, score calculations, and comparison conditions.
- Support the existing YAML schema through a bundled SnakeYAML `2.6` parser,
  including conditional lines, frame timing, render intervals, and hidden numbers.
- Preserve the previous configuration if a reload fails to parse.
- Provide `/simplescore` and `/sb` with `help`, `reload`, `version`, `info`,
  `toggle`, and `force`, including target-player operations and completion.
- Resolve permissions through LuckPerms when installed, falling back to Minecraft
  gamemaster permissions when no explicit LuckPerms result is available.
- Observe outgoing sidebar display packets so a foreign sidebar retains ownership;
  resume the SimpleScore display when the foreign sidebar is cleared.
- Process all updates on the server thread. The `scoreboardTaskAsync` setting is
  retained for configuration compatibility but ignored by Fabric.

### Bundled Kotlin runtime

The Fabric JAR contains its own relocated Kotlin runtime, YAML parser, SemVer
dependency, and Adventure formatting libraries. It uses a plain JVM entrypoint
and has no dependency on Fabric Language Kotlin or another mod's Kotlin runtime.
Minecraft, Fabric Loader, Fabric API, Patbox, and LuckPerms are not bundled.

### Optional Patbox placeholders

- Integrate optionally with Patbox Text Placeholder API `3.2.0+26.3`.
- Keep existing built-in `%player_*%` and `%server_*%` tokens available without
  Patbox, along with `%simplescore_scoreboard%` and `%simplescore_isHidden%`.
- Evaluate native Patbox tokens such as `%player:name%` when the provider is
  installed, and register `%simplescore:scoreboard%` and
  `%simplescore:is_hidden%`.
- Preserve text colors and decorations, leave unknown tokens unchanged, and pause
  providers throwing runtime or linkage errors for 30 seconds.

Patbox and Bukkit PlaceholderAPI are different APIs. Bukkit expansion JARs and
their plugin dependencies cannot be loaded by this Fabric adapter. WorldGuard
integration remains specific to Bukkit.

### Built-in optional compass

- Port the single-line renderer, styles, and font metrics from Airgalaxie's
  CompassExpansion into the Fabric adapter. The original project is not modified.
- Add `config/simplescore/compass.yml`, enabled by default, preserving the original
  content, layout, style, behavior, and font settings with bundled fallback values.
- Provide `%compass_bar%` without additional mods and optionally register
  `%compass:bar%` with Patbox. Disabled compasses return empty output.
- Precompute the immutable frame cache only when enabled; release it when disabled
  or stopped. No additional scheduler, per-query layout work, or file polling is
  introduced.
- Apply changes through `/sb reload`; reject invalid enabled configurations while
  retaining the previous configuration and renderer.
- Preserve exact pixel advances, including bold U+200C padding, through the native
  Fabric text conversion. Test every default-resolution frame at 90 and 160 pixels.
- Include the compass as the lowest row (score `-1`, `renderEvery: 1`) only in the
  bundled Fabric `default` scoreboard. Preserve the enable/disable switch,
  existing saved configurations, other bundled layouts, and Bukkit/PAPI integration.
  Existing saved layouts can add the token manually; `taskUpdateTime: 1` enables
  per-tick rotation updates.

## Telemetry, publishing, and build automation

These changes include removals already present in earlier development commits:

- Remove bStats telemetry, its dependency, and the generated metrics identifier.
- Remove Hangar and Modrinth publishing plugins, configuration, and CI steps.
- Retain GitHub Releases and use its latest-release page as the generated download
  URL.

Additional build changes:

- Read Java versions from the catalog in release, test, and documentation workflows.
- Build both adapters in CI and publish `target/*.jar` in GitHub Releases.
- Include Fabric in the aggregated Dokka documentation.
- Declare the project version as an input to project-information generation and
  Bukkit resource processing to prevent stale generated versions.
- Read JitPack's Java version from the catalog and select that JDK through SDKMAN.
- Update the README with installation, migration, build, and validation instructions.

## Validation

The following checks passed for the development state documented here:

- Both adapter JAR builds and aggregated Dokka generation.
- 29 automated tests: seven Bukkit placeholder-resolver tests and 22 Fabric tests
  covering configuration, text formatting, renderer behavior, and the final JAR.
- Isolated loading of the Fabric distributable with only the Java platform
  classloader as its parent, exercising bundled Kotlin, SemVer, and YAML without
  an external Kotlin runtime.
- Full production-server smoke tests on Snapshot 7, using the final Fabric JAR
  and a separate test mod, both without Patbox and with Patbox `3.2.0+26.3`.
- Simulated-player checks for private output, hidden numbers, commands, reload,
  disconnect cleanup, and foreign-sidebar ownership and resumption.
- Patbox evaluation of `%player:name%` and `%simplescore:scoreboard%`.
- Built-in compass enable/disable and reload, cardinal-direction rotation in
  scoreboard packets, rejection of invalid configuration without losing the
  previous renderer, and styled equivalence of the optional Patbox compass token.
- Both server runs verify that Fabric Language Kotlin is absent and unrelocated
  Kotlin classes are unavailable, then stop the test server.

Reproduce the build and automated tests with:

```sh
./gradlew test adapterJars
./gradlew :dokkaGenerate
```

After accepting the [Minecraft EULA](https://aka.ms/MinecraftEULA), run the local
production smoke tests with:

```sh
./gradlew :adapter:fabric:runProductionSmoke -PacceptMinecraftEula=true
./gradlew :adapter:fabric:runProductionSmoke -PwithPatbox -PacceptMinecraftEula=true
```

The explicit EULA flag creates `eula.txt` in the isolated test directory. Test
servers bind to the loopback interface with an automatically selected port. The
smoke test mod and generated server data are not included in the distributions.

The fork maintainer additionally confirmed the Fabric default scoreboard and its
bottom compass row visually in a connected Minecraft client on 2026-11-08 and
reported smooth compass movement. This manual observation is not a performance
benchmark or a guarantee for other fonts, resource packs, or clients.

The checks do not establish compatibility with every third-party expansion or all
Bukkit, Paper, Folia, and historical Minecraft server versions. Production smoke
runs cover Fabric; Bukkit has build and placeholder-resolver test coverage.

The existing update checker still queries upstream releases and groups equal and
higher local version numbers under the same “latest version” message. It does not
check the freshness of this independent fork; this limitation remains unchanged.

## Migration notes

- Run builds and servers on Java 25 or newer; the configured build toolchain is 25.
- Install only the JAR matching the server platform: Bukkit in `plugins/`, Fabric
  in `mods/` alongside Fabric API. No SimpleScore client mod is required.
- Update scripts referencing `:bukkit` Gradle paths to `:adapter:bukkit`.
- Update artifact paths from `build/libs/` to `target/` and account for the
  `-bukkit` or `-fabric` filename suffix.
- Fabric stores configuration in `config/simplescore/`. Adapt world-selection
  patterns to dimension identifiers such as `minecraft:overworld` and
  `minecraft:the_end` when migrating Bukkit configurations.
- Keep WorldGuard and Bukkit PAPI expansion configurations on the Bukkit adapter;
  use native Patbox tokens for Fabric integrations where available.
- Hangar and Modrinth releases require a separate publishing process; the project
  no longer publishes to those services automatically.
