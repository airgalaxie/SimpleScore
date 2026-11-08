[![SimpleScore Logo]][GitHub]

> [!IMPORTANT]
> **Unofficial fork**
>
> This repository is an independent fork of SimpleScore. It is not affiliated with or maintained by the original author. Changes in this fork are maintained independently of the original project. Please report issues related to these changes in this repository.

[![Upstream Latest Release](https://ruipereira.dev/api/badge/release/SimpleScore)](https://github.com/RuiPereiraDev/SimpleScore/releases/latest)
[![Upstream Downloads](https://ruipereira.dev/api/badge/downloads/SimpleScore)][SpigotMC]
[![Discord](https://ruipereira.dev/api/badge/discord)][Discord]
[![License](https://img.shields.io/github/license/RuiPereiraDev/SimpleScore)](https://github.com/RuiPereiraDev/SimpleScore/blob/main/LICENSE)

# Overview
[SimpleScore][GitHub] is a powerful yet easy-to-use plugin that lets you bring your Minecraft server to life with fully customized scoreboards. Display key information, announcements, player stats and much more in real-time!

Select scoreboards by permissions, conditions, and world/dimension. The Bukkit adapter additionally supports [WorldGuard][WorldGuard] regions.

The Bukkit adapter optionally integrates with [PlaceholderAPI][PlaceholderAPI]. The Fabric adapter optionally integrates with [Patbox Text Placeholder API](https://github.com/Patbox/TextPlaceholderAPI). Built-in player and server placeholders work without either provider.

## Main Features
- A lag-free, fully animated scoreboard with no flickering.
- Support for newer features such as hiding score numbers.
- Full RGB color support on 1.16 servers or newer.
- Bukkit/Paper/Folia adapter: includes compatibility code for Minecraft 1.8 and newer, built against the Spigot 26.3 API.
- Fabric adapter: targets Minecraft 26.4 Snapshot 7.
- Can display scoreboards based on permissions, conditions, worlds and regions.
- No character limit on 1.13 servers or newer (limited to 32 characters on older versions).
- Designed to be compatible with any other plugins that temporarily modify the scoreboard.
- Any message sent by the plugin can be modified either by the built-in translation system or by providing a custom messages file.

## Requirements
- **Java:** Java 25 or above for running the plugin.
- **Bukkit adapter:** Spigot, Paper, or compatible forks, including Folia.
- **Fabric adapter:** Minecraft 26.4 Snapshot 7, Fabric Loader 0.20.1 or newer, and Fabric API 0.165.4+26.4.
- Each adapter bundles its own Kotlin runtime. Fabric Language Kotlin is not required.

Older Minecraft versions require a server implementation that can run on Java 25.
Compiling against the Spigot 26.3 API does not by itself verify compatibility with every server version.

## Building
Use JDK 25 and the included Gradle wrapper:

```sh
./gradlew test adapterJars
```

Both distributable JARs are written to the repository's `target/` directory:

- `SimpleScore-<version>-bukkit.jar`: install in the server's `plugins/` directory.
- `SimpleScore-<version>-fabric.jar`: install in the server's `mods/` directory alongside Fabric API.

Players do not need a client mod. Install only the adapter matching your server.
`./gradlew shadowJar` and `./gradlew assemble` also build both adapter JARs.

The project is organized as:

```text
api/                 Platform-independent public interfaces
core/                Scoreboards, animations, conditions, effects, and translations
adapter/bukkit/      Bukkit, Paper, Folia, PAPI, and WorldGuard integration
adapter/fabric/      Fabric server integration and optional Patbox support
```

All dependency, toolchain, and plugin versions are defined in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml).
`versions.minecraft` is the launcher version ID; `versions.minecraftRuntime` is the
normalized version checked by Fabric Loader (`26.4-alpha.7` for Snapshot 7).
The authoritative Java version is `versions.java` in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml). Gradle's Java and Kotlin
toolchains, all GitHub Actions workflows, and JitPack read this value.
When changing it, update the Java requirements in this README as well.

## Bukkit PlaceholderAPI
PlaceholderAPI is optional. SimpleScore evaluates placeholders on the main server thread
(or the player's entity scheduler on Folia), even when asynchronous update dispatch is enabled.
Other plugins calling SimpleScore's variable replacement API from a different thread must
schedule that call on the player's server thread; otherwise external placeholders are left unchanged.

If an expansion throws a runtime exception or a linkage error, SimpleScore preserves the
unresolved placeholder and pauses calls to that expansion for 30 seconds before retrying.
Other expansions and built-in placeholders continue to work. Errors logged and handled
inside an expansion are not detectable by this protection; blocking scripts and native
engine crashes cannot be isolated by it.

## Fabric configuration and placeholders
Configuration files are stored in `config/simplescore/`: `config.yml`,
`conditions.yml`, and `scoreboards.yml`. The existing YAML schema, animation frames,
render intervals, conditional lines, score values, and hidden numbers are supported.
World patterns match dimension identifiers such as `minecraft:overworld`,
`minecraft:the_nether`, and `minecraft:the_end`; update Bukkit world-name patterns
when migrating configurations. Fabric processes all updates on the server thread;
`scoreboardTaskAsync` is retained for configuration compatibility and ignored.

The `/simplescore` command and `/sb` alias provide `help`, `reload`, `version`,
`info`, `toggle`, and `force`, including player targets and tab completion.
Permission nodes retain their Bukkit names. With LuckPerms installed, its Fabric
API provides permission checks; otherwise Minecraft gamemaster permissions (operator
level 2 or above) are required. WorldGuard regions and Bukkit plugin expansions
remain features of the Bukkit adapter.

Built-in `%player_name%`, `%player_health%`, `%player_world%`, `%server_online%`, and
other existing player/server placeholders are available without another mod.
`%simplescore_scoreboard%` and `%simplescore_isHidden%` expose the current selection.
For optional external placeholders, install Patbox Text Placeholder API separately.
Native tokens use Patbox's namespace syntax, for example `%player:name%`.
SimpleScore registers `%simplescore:scoreboard%` and `%simplescore:is_hidden%`.
Unknown tokens remain unchanged, and providers throwing runtime or linkage errors
are paused for 30 seconds. Patbox text colors and decorations are retained.
Bukkit PAPI expansion JARs cannot be loaded by the Fabric adapter.

The Fabric JAR contains a private, relocated Kotlin runtime and does not depend on
or use an installed Fabric Language Kotlin mod. It also bundles its own YAML and
text formatting libraries; Patbox, LuckPerms, Fabric API, and Minecraft are not bundled.

### Built-in Fabric compass

The Fabric adapter includes the single-line renderer from Airgalaxie's
CompassExpansion. No external compass mod, Bukkit PAPI, or Patbox is required.
The compass is enabled by default and is the lowest row of the bundled Fabric
`default` scoreboard. Other bundled layouts and the Bukkit adapter are unchanged.
Existing server configuration files are preserved when updating the JAR.

The default switch in `config/simplescore/compass.yml` is:

```yaml
enabled: true
```

The file also contains `content`, `layout`, `styles`, `behavior`, and `font` settings
matching the original compass configuration. These control direction labels,
scale marks, pixel width, colors, highlighting, frame resolution, and custom font
advances. Missing settings use the bundled defaults. Existing compass settings can
be copied into this file; keep the additional `enabled` switch.

The bundled `default` scoreboard already includes `%compass_bar%` at score `-1`
with `renderEvery: 1`. For an existing saved layout, add the bottom row manually:

```yaml
scores:
  -1:
    lines:
      - text: '%compass_bar%'
        renderEvery: 1
```

Use `taskUpdateTime: 1` in `config.yml` for updates every server tick, then run
`/sb reload`. The compass only changes when the selected cached frame changes.
Patbox, when installed, additionally exposes `%compass:bar%` to compatible mods.
The Bukkit adapter continues to use the separate PAPI CompassExpansion.

Setting `enabled: false` and reloading releases the frame cache and makes both
compass placeholders empty. An invalid enabled compass configuration rejects the
reload and retains the previous configuration. The default renderer precomputes
720 frames; queries only read the player's yaw and select a frame on the server
thread. There is no additional repeating task or file polling.

Pixel spacing includes the original bold U+200C padding and is tested through
Fabric's native text conversion. The calculations assume the vanilla default font;
resource packs and alternate fonts can change the visual result. Compass-only
settings do not control the surrounding scoreboard layout.

### Production validation
`./gradlew test adapterJars` includes tests of the final Fabric JAR in an isolated
classloader with no external Kotlin runtime, plus configuration and text tests.

The production launcher can load the exact distributable without starting a world:

```sh
./gradlew :adapter:fabric:runProductionServer -PserverHelp
./gradlew :adapter:fabric:runProductionServer -PserverHelp -PwithPatbox
```

For full server smoke tests, first accept the Minecraft EULA by creating `eula.txt`
containing `eula=true` in `adapter/fabric/build/smoke-standalone/` and, for the Patbox
run, `adapter/fabric/build/smoke-patbox/`. Alternatively, after agreeing to the EULA,
pass `-PacceptMinecraftEula=true` to explicitly create the file during task execution
(this also works when Gradle recreates the test output directory). Then run:

```sh
./gradlew :adapter:fabric:runProductionSmoke
./gradlew :adapter:fabric:runProductionSmoke -PwithPatbox
```

These run the final JAR with a separate test mod and simulated players on a local
server. They check per-player output, commands, cleanup, foreign sidebar handling,
and optional placeholders, then stop the server. The test mod is never shipped.

Production smoke tests passed on Minecraft 26.4 Snapshot 7 with Java 25 and Fabric
Loader 0.20.1, both without Patbox and with Patbox 3.2.0+26.3. Neither run installed
Fabric Language Kotlin; both verified that unrelocated Kotlin classes were absent.
The Patbox run also evaluated `%player:name%` and `%simplescore:scoreboard%`.
These tests use simulated players. Separately, the fork maintainer confirmed the
Fabric default scoreboard and bottom compass row visually in a connected client
on 2026-11-08 and reported smooth compass movement. This is a manual observation,
not a performance benchmark or validation of every third-party mod.

### Update-check scope
The current update checker queries the original project's GitHub releases.
Its “latest version” message means that it found no upstream release with a higher
version number; it does not establish that this independently maintained fork is
up to date. Fork-specific release checking is not implemented.

## More Information
### Documentation
[Configuration](https://github.com/RuiPereiraDev/SimpleScore/wiki/Configuration) - [Placeholders](https://github.com/RuiPereiraDev/SimpleScore/wiki/Placeholders) - [WorldGuard](https://github.com/RuiPereiraDev/SimpleScore/wiki/WorldGuard) - [Developer API](https://github.com/RuiPereiraDev/SimpleScore/wiki/Developer-API)

### Upstream Download Links
[GitHub](https://github.com/RuiPereiraDev/SimpleScore/releases/latest) - [SpigotMC][SpigotMC]

### Quick Links
[Discord][Discord] - [Fork Issues](https://github.com/airgalaxie/SimpleScore/issues) - [Wiki](https://github.com/RuiPereiraDev/SimpleScore/wiki)

## Show Your Support
Like [SimpleScore][GitHub]? Your support helps keep this project updated and bug-free for everyone. The easiest ways to help are by:
* ⭐ Star the project on [GitHub][GitHub] and/or [follow me](https://github.com/RuiPereiraDev).
* 👍 Follow, rate, or review on [SpigotMC][SpigotMC].

For those who want to show their appreciation with a financial contribution, you can do so via [GitHub Sponsors][Sponsors] or a one-time donation via [PayPal][PayPal].

[SimpleScore Logo]: https://raw.githubusercontent.com/RuiPereiraDev/SimpleScore/main/.github/SimpleScore.png

[GitHub]: https://github.com/RuiPereiraDev/SimpleScore
[SpigotMC]: https://www.spigotmc.org/resources/23243/

[Discord]: https://discord.gg/cJnzTDGphE

[Sponsors]: https://github.com/sponsors/RuiPereiraDev
[PayPal]: https://paypal.me/RageBaby

[WorldGuard]: https://github.com/EngineHub/WorldGuard
[PlaceholderAPI]: https://github.com/PlaceholderAPI/PlaceholderAPI
