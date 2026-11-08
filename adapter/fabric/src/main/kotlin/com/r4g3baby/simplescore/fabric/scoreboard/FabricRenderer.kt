package com.r4g3baby.simplescore.fabric.scoreboard

import net.minecraft.network.chat.numbers.BlankFormat
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket
import net.minecraft.network.protocol.game.ClientboundSetScorePacket
import net.minecraft.network.protocol.game.ClientboundResetScorePacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.Scoreboard
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import java.util.Optional
import java.util.UUID

/** Client-only objectives: never adds objectives/scores to the world's server scoreboard. */
class FabricRenderer {
    data class Line(val text: String, val score: Int, val hideNumber: Boolean)
    private data class State(val objective: Objective, var title: String, var lines: Map<String, Line>)
    private val states = mutableMapOf<UUID, State>()

    internal interface Target {
        val uuid: UUID
        fun send(packet: Packet<*>)
        fun isConnected(): Boolean
        fun fallbackObjective(): Objective?
    }
    private class PlayerTarget(private val player: ServerPlayer) : Target {
        override val uuid get() = player.uuid
        override fun send(packet: Packet<*>) = player.connection.send(packet)
        override fun isConnected() = !player.hasDisconnected()
        override fun fallbackObjective() = player.level().scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR)
    }
    fun render(player: ServerPlayer, title: String, lines: Map<String, Line>) = render(PlayerTarget(player), title, lines)
    internal fun render(player: Target, title: String, lines: Map<String, Line>) {
        val state = states.getOrPut(player.uuid) {
            val objective = Objective(Scoreboard(), "ss${player.uuid.toString().replace("-", "").take(14)}",
                ObjectiveCriteria.DUMMY, Text.component(title), ObjectiveCriteria.RenderType.INTEGER, false, null)
            player.send(ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_ADD))
            if (!SidebarOwnership.isExternal(player.uuid, objective.name))
                player.send(ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective))
            State(objective, title, emptyMap())
        }
        if (SidebarOwnership.current(player.uuid) == "")
            player.send(ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, state.objective))
        if (state.title != title) {
            state.objective.displayName = Text.component(title)
            player.send(ClientboundSetObjectivePacket(state.objective, ClientboundSetObjectivePacket.METHOD_CHANGE))
            state.title = title
        }
        lines.forEach { (id, line) ->
            if (state.lines[id] != line) player.send(ClientboundSetScorePacket(id, state.objective.name,
                line.score, Optional.of(Text.component(line.text)), Optional.ofNullable(if (line.hideNumber) BlankFormat.INSTANCE else null)))
        }
        (state.lines.keys - lines.keys).forEach { player.send(ClientboundResetScorePacket(it, state.objective.name)) }
        state.lines = lines.toMap()
    }
    fun remove(player: ServerPlayer, restore: Boolean = true) = remove(PlayerTarget(player), restore)
    internal fun remove(player: Target, restore: Boolean = true) {
        val state = states.remove(player.uuid) ?: return
        if (player.isConnected()) {
            player.send(ClientboundSetObjectivePacket(state.objective, ClientboundSetObjectivePacket.METHOD_REMOVE))
            if (restore && !SidebarOwnership.isExternal(player.uuid, state.objective.name)) player.send(ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR,
                player.fallbackObjective()))
        }
    }
}
