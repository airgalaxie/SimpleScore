package com.r4g3baby.simplescore.fabric.scoreboard

import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket
import net.minecraft.world.scores.DisplaySlot
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Packet callbacks can run off-thread; only immutable objective names cross this boundary. */
object SidebarOwnership {
    private val displays = ConcurrentHashMap<UUID, String>()
    @JvmStatic fun observe(player: UUID, packet: Packet<*>) {
        if (packet is ClientboundSetDisplayObjectivePacket && packet.slot == DisplaySlot.SIDEBAR) {
            displays[player] = packet.objectiveName ?: ""
        }
    }
    fun isExternal(player: UUID, ownName: String): Boolean = displays[player]?.let { it.isNotEmpty() && it != ownName } ?: false
    fun current(player: UUID) = displays[player]
    fun forget(player: UUID) { displays.remove(player) }
}
