package com.r4g3baby.simplescore.bukkit.hooks

import com.r4g3baby.simplescore.BukkitPlugin
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.entity.Player
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/** Only instantiated while PlaceholderAPI is enabled. */
internal class PapiHook(private val plugin: BukkitPlugin) {
    private var expansion: PapiExpansion? = null
    @Volatile private var active = true
    private val lastThreadWarning = AtomicLong(Long.MIN_VALUE)
    private val resolver = SafePlaceholderResolver<Player>(
        resolve = { text, player -> PlaceholderAPI.setPlaceholders(player, text) },
        report = { identifier, error ->
            plugin.logger.log(Level.WARNING,
                "PlaceholderAPI expansion '$identifier' failed; skipping it for 30 seconds. " +
                    "Unresolved placeholders are preserved.", error)
        }
    )

    fun register() {
        try {
            val candidate = PapiExpansion(plugin)
            if (candidate.register()) expansion = candidate
            else plugin.logger.warning("Could not register SimpleScore's PlaceholderAPI expansion. External placeholders remain available.")
        } catch (exception: RuntimeException) {
            plugin.logger.log(Level.WARNING, "Could not register SimpleScore's PlaceholderAPI expansion.", exception)
        } catch (error: LinkageError) {
            plugin.logger.log(Level.WARNING, "Incompatible PlaceholderAPI API; expansion registration failed.", error)
        }
    }

    fun replace(text: String, player: Player): String {
        if (!active || !plugin.server.pluginManager.isPluginEnabled("PlaceholderAPI")) return text
        // Other plugins may call the SimpleScore API outside our scoreboard task, too.
        if (!plugin.scheduler.isPlayerThread(player)) {
            val now = System.nanoTime()
            val previous = lastThreadWarning.get()
            if ((previous == Long.MIN_VALUE || now - previous >= TimeUnit.MINUTES.toNanos(1)) &&
                lastThreadWarning.compareAndSet(previous, now)) {
                plugin.logger.warning("Skipped PlaceholderAPI evaluation outside the player's server thread.")
            }
            return text
        }
        return resolver.replace(text, player)
    }

    fun close() {
        active = false
        val registered = expansion ?: return
        expansion = null
        try {
            if (registered.isRegistered) registered.unregister()
        } catch (exception: RuntimeException) {
            plugin.logger.log(Level.WARNING, "Could not unregister SimpleScore's PlaceholderAPI expansion.", exception)
        } catch (error: LinkageError) {
            plugin.logger.log(Level.WARNING, "PlaceholderAPI API unavailable during expansion cleanup.", error)
        }
    }
}
