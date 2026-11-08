package com.r4g3baby.simplescore.fabric.scoreboard

import com.r4g3baby.simplescore.api.scoreboard.Scoreboard
import com.r4g3baby.simplescore.api.scoreboard.data.Priority
import com.r4g3baby.simplescore.api.scoreboard.data.Provider
import com.r4g3baby.simplescore.api.scoreboard.data.Viewer as ApiViewer
import net.minecraft.server.level.ServerPlayer
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class Viewer(
    override val reference: WeakReference<ServerPlayer>,
) : ApiViewer<ServerPlayer> {
    constructor(player: ServerPlayer) : this(WeakReference(player))

    private data class ScoreboardEntry(
        val scoreboard: Scoreboard<ServerPlayer>?, val priority: Priority
    )

    private val entries = ConcurrentHashMap<Provider, ScoreboardEntry>()
    private val hiding = CopyOnWriteArrayList<Provider>()

    private var cachedScoreboard: Scoreboard<ServerPlayer>? = null
    private var isCacheDirty: Boolean = true

    override val scoreboard: Scoreboard<ServerPlayer>?
        get() {
            if (isCacheDirty) {
                cachedScoreboard = entries.entries.maxByOrNull { it.value.priority }?.value?.scoreboard
                isCacheDirty = false
            }
            return cachedScoreboard
        }
    override val isScoreboardHidden: Boolean
        get() = hiding.isNotEmpty()

    override fun setScoreboard(scoreboard: Scoreboard<ServerPlayer>?, provider: Provider, priority: Priority) {
        entries[provider] = ScoreboardEntry(scoreboard, priority)
        invalidateCache()
    }

    override fun getScoreboard(provider: Provider): Scoreboard<ServerPlayer>? {
        return entries[provider]?.scoreboard
    }

    override fun removeScoreboard(provider: Provider): Scoreboard<ServerPlayer>? {
        val removed = entries.remove(provider)
        if (removed != null) invalidateCache()
        return removed?.scoreboard
    }

    override fun setPriority(priority: Priority?, provider: Provider) {
        entries.computeIfPresent(provider) { _, entry ->
            entry.copy(priority = priority ?: Priority.Normal)
        }
        invalidateCache()
    }

    override fun getPriority(provider: Provider): Priority? {
        return entries[provider]?.priority
    }

    override fun hideScoreboard(provider: Provider): Boolean {
        return hiding.addIfAbsent(provider)
    }

    override fun showScoreboard(provider: Provider): Boolean {
        return hiding.remove(provider)
    }

    override fun isHidingScoreboard(provider: Provider): Boolean {
        return hiding.contains(provider)
    }

    internal fun copyTo(target: Viewer) {
        entries.forEach { (provider, entry) -> target.setScoreboard(entry.scoreboard, provider, entry.priority) }
        hiding.forEach(target::hideScoreboard)
    }

    private fun invalidateCache() {
        isCacheDirty = true
    }
}
