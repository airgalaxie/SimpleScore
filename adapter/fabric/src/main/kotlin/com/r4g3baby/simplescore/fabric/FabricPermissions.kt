package com.r4g3baby.simplescore.fabric

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.permissions.Permissions
import net.luckperms.api.LuckPermsProvider

object FabricPermissions {
    fun check(source: CommandSourceStack, permission: String): Boolean {
        val player = source.player
        if (player != null && FabricLoader.getInstance().isModLoaded("luckperms")) {
            val user = LuckPermsProvider.get().userManager.getUser(player.uuid)
            val result = user?.cachedData?.permissionData?.checkPermission(permission)
            if (result != null && result != net.luckperms.api.util.Tristate.UNDEFINED) return result.asBoolean()
        }
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)
    }
}
