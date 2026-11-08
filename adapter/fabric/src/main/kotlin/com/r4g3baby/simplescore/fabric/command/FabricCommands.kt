package com.r4g3baby.simplescore.fabric.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.r4g3baby.simplescore.ProjectInfo
import com.r4g3baby.simplescore.api.scoreboard.data.Priority
import com.r4g3baby.simplescore.core.util.checkForUpdates
import com.r4g3baby.simplescore.fabric.FabricPermissions
import com.r4g3baby.simplescore.fabric.FabricPlatform
import com.r4g3baby.simplescore.fabric.scoreboard.Text
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import java.util.concurrent.CompletableFuture

object FabricCommands {
    private val names = listOf("help", "reload", "version", "info", "toggle", "force")
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>, platform: () -> FabricPlatform) {
        val root = Commands.literal("simplescore")
            .executes { execute(platform(), it.source, "help", emptyList()) }
        names.forEach { command ->
            root.then(Commands.literal(command).requires { FabricPermissions.check(it, "simplescore.cmd.$command") }
                .executes { execute(platform(), it.source, command, emptyList()) }
                .then(Commands.argument("arguments", StringArgumentType.greedyString())
                    .suggests { context, builder ->
                        val host = platform()
                        val tokens = builder.remaining.split(' ')
                        val last = tokens.last()
                        val offset = builder.createOffset(builder.start + builder.remaining.length - last.length)
                        val other = FabricPermissions.check(context.source, "simplescore.cmd.$command.other")
                        val players = if (other) host.server.playerList.players.map { it.gameProfile.name } else emptyList()
                        val choices = when (command) {
                            "info" -> if (tokens.size == 1) players else emptyList()
                            "toggle" -> if (tokens.size == 1) listOf("on", "off") + players else if (tokens.size == 2 && other) listOf("on", "off") else emptyList()
                            "force" -> if (tokens.size == 1) host.manager.scoreboards.map { it.name } + "none" + players else if (tokens.size == 2 && other) host.manager.scoreboards.map { it.name } + "none" else emptyList()
                            else -> emptyList()
                        }
                        choices.filter { it.startsWith(last, true) }.distinct().forEach(offset::suggest)
                        offset.buildFuture()
                    }
                    .executes { execute(platform(), it.source, command, StringArgumentType.getString(it, "arguments").trim().split(Regex("\\s+"))) }))
        }
        val node = dispatcher.register(root)
        dispatcher.register(Commands.literal("sb").redirect(node).executes { execute(platform(), it.source, "help", emptyList()) })
        dispatcher.register(Commands.literal("SimpleScore").redirect(node).executes { execute(platform(), it.source, "help", emptyList()) })
    }

    private fun execute(host: FabricPlatform, source: CommandSourceStack, command: String, args: List<String>): Int {
        fun message(key: String, vararg values: Any) = source.sendSystemMessage(Text.component(host.i18n.t(key, *values)))
        if (!FabricPermissions.check(source, "simplescore.cmd.$command")) { message("cmd.noPermission"); return 0 }
        val manager = host.manager
        val self = source.player
        val otherPermission = FabricPermissions.check(source, "simplescore.cmd.$command.other")
        when (command) {
            "help" -> {
                message("cmd.help.header")
                names.filter { FabricPermissions.check(source, "simplescore.cmd.$it") }.forEach {
                    source.sendSystemMessage(Text.component(host.i18n.t("cmd.help.cmd", it, host.i18n.t("cmd.$it.description", prefixed = false), prefixed = false)))
                }
            }
            "reload" -> {
                message("cmd.reload.start")
                try { manager.loadConfiguration(); message("cmd.reload.finished") }
                catch (error: IllegalArgumentException) { source.sendFailure(net.minecraft.network.chat.Component.literal(error.message ?: "Reload failed")) }
            }
            "version" -> {
                if (!manager.config.checkForUpdates) message("cmd.version.current", ProjectInfo.VERSION)
                else {
                    message("cmd.version.checking", ProjectInfo.VERSION)
                    CompletableFuture.runAsync {
                        checkForUpdates({ version -> host.server.execute { message("cmd.version.newVersion", version); source.sendSystemMessage(Text.component(ProjectInfo.DOWNLOAD_URL)) } },
                            { error -> host.server.execute { message(if (error == null) "cmd.version.latest" else "cmd.version.failed") } })
                    }
                }
            }
            "info" -> {
                val other = args.isNotEmpty() && (self == null || otherPermission)
                val target = if (other) host.server.playerList.players.find { it.gameProfile.name.equals(args[0], true) } else self
                if (target == null) { message(if (other) "cmd.notOnline" else "cmd.info.usage.console"); return 0 }
                val viewer = manager.getOrCreateViewer(target)
                if (other) message("cmd.info.other.header", target.gameProfile.name) else message("cmd.info.header")
                message("cmd.info.scoreboard", viewer.scoreboard?.name ?: "none")
                message("cmd.info.isHidden", viewer.isScoreboardHidden)
            }
            "toggle" -> {
                val other = args.isNotEmpty() && !args[0].equals("on", true) && !args[0].equals("off", true)
                if (other && !otherPermission) { message("cmd.noPermission"); return 0 }
                val target = if (other) host.server.playerList.players.find { it.gameProfile.name.equals(args[0], true) } else self
                if (target == null) { message(if (other) "cmd.notOnline" else "cmd.toggle.usage.console"); return 0 }
                val viewer = manager.getOrCreateViewer(target)
                val mode = args.getOrNull(if (other) 1 else 0)?.lowercase()
                val shown = when (mode) {
                    "on" -> { viewer.showScoreboard(host.provider); true }
                    "off" -> { viewer.hideScoreboard(host.provider); false }
                    null -> if (viewer.hideScoreboard(host.provider)) false else { viewer.showScoreboard(host.provider); true }
                    else -> { message("cmd.toggle.usage.admin"); return 0 }
                }
                val key = if (shown) "shown" else "hidden"
                if (other) message("cmd.toggle.other.$key", target.gameProfile.name) else message("cmd.toggle.$key")
            }
            "force" -> {
                if (args.isEmpty()) { message(if (self == null) "cmd.force.usage.console" else if (otherPermission) "cmd.force.usage.admin" else "cmd.force.usage.player"); return 0 }
                val other = self == null || (manager.getScoreboard(args[0]) == null && !args[0].equals("none", true) && args.size > 1)
                if (other && !otherPermission) { message("cmd.noPermission"); return 0 }
                val target = if (other) host.server.playerList.players.find { it.gameProfile.name.equals(args[0], true) } else self
                if (target == null) { message("cmd.notOnline"); return 0 }
                val name = args.getOrNull(if (other) 1 else 0) ?: run { message("cmd.force.usage.console"); return 0 }
                val viewer = manager.getOrCreateViewer(target)
                if (name.equals("none", true)) {
                    viewer.removeScoreboard(host.provider)
                    if (other) message("cmd.force.other.removed", target.gameProfile.name) else message("cmd.force.removed")
                } else {
                    val scoreboard = manager.getScoreboard(name) ?: run { message("cmd.force.notFound", name); return 0 }
                    viewer.setScoreboard(scoreboard, host.provider, Priority.Highest)
                    if (other) message("cmd.force.other.changed", target.gameProfile.name, scoreboard.name) else message("cmd.force.changed", scoreboard.name)
                }
            }
        }
        return 1
    }
}
