package com.r4g3baby.simplescore.fabric.scoreboard

import com.r4g3baby.simplescore.api.scoreboard.VarReplacer
import com.r4g3baby.simplescore.core.util.translateColorCodes
import com.r4g3baby.simplescore.fabric.FabricPlatform
import net.minecraft.server.level.ServerPlayer
import net.minecraft.stats.Stats
import kotlin.math.roundToInt

class VarReplacer(private val platform: FabricPlatform) : VarReplacer<ServerPlayer> {
    var external: ((String, ServerPlayer) -> String)? = null
    override fun replace(text: String, viewer: ServerPlayer): String {
        // External users of the API must also respect Minecraft's server thread.
        if (!platform.server.isSameThread) return text
        var result = text
        fun replace(key: String, value: () -> String) {
            if (key in result) result = result.replace(key, value())
        }
        replace("%compass_bar%") { platform.manager.compass.atYaw(viewer.yRot) }
        replace("%player_name%") { viewer.gameProfile.name }
        replace("%player_displayname%") { viewer.displayName.string }
        replace("%player_uuid%") { viewer.uuid.toString() }
        replace("%player_level%") { viewer.experienceLevel.toString() }
        replace("%player_gamemode%") { viewer.gameMode().name.lowercase().replaceFirstChar { it.titlecase() } }
        replace("%player_health%") { viewer.health.roundToInt().toString() }
        replace("%player_maxhealth%") { viewer.maxHealth.roundToInt().toString() }
        replace("%player_hearts%") {
            val hearts = ((viewer.health / viewer.maxHealth) * 10).roundToInt().coerceIn(0, 10)
            "&c${"❤".repeat(hearts)}&0${"❤".repeat(10 - hearts)}"
        }
        replace("%player_ping%") { viewer.connection.latency().toString() }
        replace("%player_kills%") { viewer.stats.getValue(Stats.CUSTOM.get(Stats.PLAYER_KILLS)).toString() }
        replace("%player_deaths%") { viewer.stats.getValue(Stats.CUSTOM.get(Stats.DEATHS)).toString() }
        replace("%player_world%") { viewer.level().dimension().identifier().toString() }
        replace("%player_world_online%") { viewer.level().players().size.toString() }
        replace("%server_online%") { platform.server.playerCount.toString() }
        replace("%server_maxplayers%") { platform.server.playerList.maxPlayers.toString() }
        replace("%simplescore_scoreboard%") { platform.manager.getViewer(viewer.uuid)?.scoreboard?.name ?: "none" }
        replace("%simplescore_isHidden%") { (platform.manager.getViewer(viewer.uuid)?.isScoreboardHidden ?: false).toString() }
        return translateColorCodes(external?.invoke(result, viewer) ?: result)
    }
}
