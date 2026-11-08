package com.r4g3baby.simplescore.fabric

import com.r4g3baby.simplescore.api.scoreboard.Scoreboard
import com.r4g3baby.simplescore.api.scoreboard.ScoreboardLine
import com.r4g3baby.simplescore.api.scoreboard.data.Priority
import com.r4g3baby.simplescore.core.BaseManager
import com.r4g3baby.simplescore.fabric.compass.Compass
import com.r4g3baby.simplescore.fabric.config.MainConfig
import com.r4g3baby.simplescore.fabric.config.YamlConfiguration
import com.r4g3baby.simplescore.fabric.hooks.PatboxHook
import com.r4g3baby.simplescore.fabric.scoreboard.FabricRenderer
import com.r4g3baby.simplescore.fabric.scoreboard.VarReplacer
import com.r4g3baby.simplescore.fabric.scoreboard.Viewer
import com.r4g3baby.simplescore.fabric.scoreboard.SidebarOwnership
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

class FabricManager(private val host: FabricPlatform) : BaseManager<ServerPlayer, YamlConfiguration>(host) {
    override lateinit var config: MainConfig<ServerPlayer>
        private set
    override val varReplacer = VarReplacer(host)
    var compass = Compass.disabled()
        private set
    private val customScoreboards = linkedMapOf<String, Scoreboard<ServerPlayer>>()
    private val playerViewers = linkedMapOf<UUID, Viewer>()
    private val renderer = FabricRenderer()
    private val worldProvider = host.provider.withContext("world")
    private var clock = 0L
    private val revisions = mutableMapOf<String, Long>()
    private data class Rendered(val uid: String, val revision: Long, val text: String)
    private val rendered = mutableMapOf<UUID, MutableMap<String, Rendered>>()

    override val scoreboards get() = config.scoreboards.values + customScoreboards.values
    override fun getScoreboard(name: String) = config.scoreboards[name] ?: customScoreboards[name]
    override fun addScoreboard(scoreboard: Scoreboard<ServerPlayer>) = customScoreboards.put(scoreboard.name, scoreboard)
    override fun removeScoreboard(scoreboard: Scoreboard<ServerPlayer>) = customScoreboards.remove(scoreboard.name)
    override val viewers get() = playerViewers.values.toList()
    override fun getViewer(uniqueID: UUID) = playerViewers[uniqueID]
    fun getOrCreateViewer(player: ServerPlayer): Viewer {
        val existing = playerViewers[player.uuid]
        // Minecraft replaces ServerPlayer on respawn; never retain the old reference.
        if (existing != null && existing.reference.get() === player) return existing
        return Viewer(player).also { replacement ->
            if (existing != null) {
                existing.copyTo(replacement)
                renderer.remove(player)
                rendered.remove(player.uuid)
            }
            playerViewers[player.uuid] = replacement
        }
    }
    fun removeViewer(id: UUID) {
        playerViewers.remove(id)?.reference?.get()?.let { renderer.remove(it) }
        rendered.remove(id)
        SidebarOwnership.forget(id)
    }

    override fun loadConfiguration() {
        check(host.server.isSameThread) { "Configuration reload must run on the server thread" }
        val oldConfig = if (this::config.isInitialized) config else null
        val next = MainConfig(host) { player, permission -> FabricPermissions.check(player.createCommandSourceStack(), permission) }
        val nextCompass: Compass
        try {
            next.loadConfig()
            nextCompass = Compass.load(host.dataFolder)
        } catch (error: RuntimeException) {
            host.logger.severe("Configuration reload failed; keeping the previous configuration: ${error.message}")
            if (oldConfig == null) throw error
            throw IllegalArgumentException("Invalid SimpleScore configuration", error)
        }
        config = next
        compass = nextCompass
        i18n.loadTranslations(config.language)
        config.scoreboards.keys.forEach(customScoreboards::remove)
        revisions.clear()
        rendered.clear()
        viewers.forEach { viewer ->
            viewer.getScoreboard(host.provider)?.let { viewer.setScoreboard(getScoreboard(it.name), host.provider, Priority.Highest) }
        }
        if (config.scoreboardTaskAsync) host.logger.info("Fabric processes all scoreboard updates on the server thread; scoreboardTaskAsync is ignored.")
    }

    override fun onEnable() {
        if (FabricLoader.getInstance().isModLoaded("placeholder-api")) {
            try {
                val hook = PatboxHook(host)
                hook.register()
                varReplacer.external = hook::replace
                host.logger.info("Optional Patbox Placeholder API integration enabled.")
            } catch (error: RuntimeException) {
                host.logger.log(java.util.logging.Level.WARNING, "Could not initialize optional Patbox integration.", error)
            } catch (error: LinkageError) {
                host.logger.log(java.util.logging.Level.WARNING, "Installed Patbox API is incompatible; built-in placeholders remain available.", error)
            }
        }
    }
    override fun onDisable() {
        viewers.mapNotNull { it.reference.get() }.forEach { renderer.remove(it) }
        playerViewers.keys.forEach(SidebarOwnership::forget)
        playerViewers.clear()
        rendered.clear()
        varReplacer.external = null
        compass = Compass.disabled()
    }
    fun tick() {
        if (clock++ % config.taskUpdateTime.coerceAtLeast(1L) != 0L) return
        val active = mutableSetOf<String>()
        scoreboards.forEach { scoreboard ->
            scoreboard.tick()
            (scoreboard.titles + scoreboard.scores.flatMap { it.lines }).forEach { line ->
                active.add(line.uid)
                revisions[line.uid] = (revisions[line.uid] ?: 0L) + if (line.shouldRender()) 1L else 0L
            }
        }
        revisions.keys.retainAll(active)
        val players = host.server.playerList.players
        (playerViewers.keys - players.map { it.uuid }.toSet()).forEach(::removeViewer)
        players.forEach { player ->
            try {
                val viewer = getOrCreateViewer(player)
                val world = player.level().dimension().identifier().toString()
                val choices = config.worlds.entries.firstOrNull { it.key.test(world) }?.value.orEmpty()
                viewer.setScoreboard(choices.mapNotNull(::getScoreboard).firstOrNull { it.canSee(player, varReplacer) }, worldProvider, Priority.Low)
                val scoreboard = viewer.scoreboard
                if (viewer.isScoreboardHidden || scoreboard == null || !scoreboard.canSee(player, varReplacer)) {
                    renderer.remove(player)
                    rendered.remove(player.uuid)
                    return@forEach
                }
                val cache = rendered.getOrPut(player.uuid) { mutableMapOf() }
                fun text(key: String, line: ScoreboardLine<ServerPlayer>): String {
                    val previous = cache[key]
                    val revision = revisions[line.uid] ?: 0L
                    if (previous != null && previous.uid == line.uid && previous.revision == revision) return previous.text
                    return line.currentText(player, varReplacer).also { cache[key] = Rendered(line.uid, revision, it) }
                }
                val title = scoreboard.getTitle(player, varReplacer)?.let { text("title", it) } ?: ""
                val lines = linkedMapOf<String, FabricRenderer.Line>()
                scoreboard.getScores(player, varReplacer).forEach scoreLoop@{ score ->
                    val value = score.getValueAsInteger(player, varReplacer) ?: return@scoreLoop
                    val line = score.getLine(player, varReplacer) ?: return@scoreLoop
                    lines[score.uid] = FabricRenderer.Line(text(score.uid, line), value, score.hideNumber)
                }
                cache.keys.retainAll(lines.keys + "title")
                renderer.render(player, title, lines)
            } catch (error: RuntimeException) {
                host.logger.log(java.util.logging.Level.WARNING, "Could not update scoreboard for ${player.uuid}", error)
            }
        }
    }
}
