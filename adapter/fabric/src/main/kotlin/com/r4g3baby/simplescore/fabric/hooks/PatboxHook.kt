package com.r4g3baby.simplescore.fabric.hooks

import com.r4g3baby.simplescore.fabric.FabricPlatform
import com.r4g3baby.simplescore.fabric.scoreboard.Text
import eu.pb4.placeholders.api.PlaceholderResult
import eu.pb4.placeholders.api.Placeholders
import eu.pb4.placeholders.api.ServerPlaceholderContext
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import java.util.logging.Level

/** Loaded only when the optional placeholder-api mod is present. */
class PatboxHook(private val platform: FabricPlatform) {
    private val placeholders = Regex("%([a-z0-9_.-]+:[a-z0-9_/.-]+)(?: ([^%]*))?%")
    private val failures = mutableMapOf<String, Long>()
    fun register() {
        Placeholders.registerServer<String>(Identifier.fromNamespaceAndPath("compass", "bar")) { context, _ ->
            val player = context.serverPlayer()
            val text = if (player != null && platform.server.isSameThread) platform.manager.compass.atYaw(player.yRot) else ""
            PlaceholderResult.value(Text.component(text))
        }
        Placeholders.registerServer<String>(Identifier.fromNamespaceAndPath("simplescore", "scoreboard")) { context, _ ->
            PlaceholderResult.value(context.serverPlayer()?.let { platform.manager.getViewer(it.uuid)?.scoreboard?.name } ?: "none")
        }
        Placeholders.registerServer<String>(Identifier.fromNamespaceAndPath("simplescore", "is_hidden")) { context, _ ->
            PlaceholderResult.value((context.serverPlayer()?.let { platform.manager.getViewer(it.uuid)?.isScoreboardHidden } ?: false).toString())
        }
    }
    fun replace(text: String, player: ServerPlayer): String = placeholders.replace(text) { match ->
        val id = Identifier.parse(match.groupValues[1])
        val now = System.nanoTime()
        if (failures[id.namespace]?.let { now - it < 30_000_000_000L } == true) return@replace match.value
        try {
            // Parse individual tokens to preserve unknown placeholders and isolate failing providers.
            val result = Placeholders.parseServerPlaceholder(id, match.groups[2]?.value, ServerPlaceholderContext.of(player))
            if (result.isValid) Text.legacy(result.component()) else match.value
        } catch (error: RuntimeException) {
            fail(id.namespace, now, error)
            match.value
        } catch (error: LinkageError) {
            fail(id.namespace, now, error)
            match.value
        }
    }
    private fun fail(namespace: String, now: Long, error: Throwable) {
        failures[namespace] = now
        platform.logger.log(Level.WARNING, "Patbox provider '$namespace' failed; skipping for 30 seconds.", error)
    }
}
