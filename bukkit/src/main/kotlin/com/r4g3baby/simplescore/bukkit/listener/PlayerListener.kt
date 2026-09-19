package com.r4g3baby.simplescore.bukkit.listener

import com.r4g3baby.simplescore.bukkit.BukkitManager
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.*

class PlayerListener(private val manager: BukkitManager) : Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(e: PlayerJoinEvent) {
        val viewer = manager.getOrCreateViewer(e.player)
        manager.updateViewerWorldScoreboard(viewer, e.player.world)
        manager.updateViewerRegionScoreboard(viewer, e.player.location)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(e: PlayerQuitEvent) {
        manager.removeViewer(e.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerKick(e: PlayerKickEvent) {
        manager.removeViewer(e.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerChangedWorld(e: PlayerChangedWorldEvent) {
        manager.getViewer(e.player.uniqueId)?.let { viewer ->
            manager.updateViewerWorldScoreboard(viewer, e.player.world)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onPlayerMove(e: PlayerMoveEvent) {
        val from = e.from
        val to = e.to ?: return
        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) return

        manager.getViewer(e.player.uniqueId)?.let { viewer ->
            to.world?.let { world -> manager.updateViewerWorldScoreboard(viewer, world) }
            manager.updateViewerRegionScoreboard(viewer, to)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerTeleport(e: PlayerTeleportEvent) {
        val from = e.from
        val to = e.to ?: return

        manager.getViewer(e.player.uniqueId)?.let { viewer ->
            val toWorld = to.world
            if (from.world != toWorld && toWorld != null) {
                manager.updateViewerWorldScoreboard(viewer, toWorld)
            }
            manager.updateViewerRegionScoreboard(viewer, to)
        }
    }
}
