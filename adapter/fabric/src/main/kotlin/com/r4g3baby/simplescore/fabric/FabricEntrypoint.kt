package com.r4g3baby.simplescore.fabric

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import com.r4g3baby.simplescore.fabric.command.FabricCommands

/** Plain JVM class entrypoint: no Kotlin language adapter is involved. */
class FabricEntrypoint : ModInitializer {
    override fun onInitialize() {
        var platform: FabricPlatform? = null
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            FabricCommands.register(dispatcher) { checkNotNull(platform) { "Server is not started" } }
        }
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            platform = FabricPlatform(server).also { it.start() }
        }
        ServerTickEvents.END_SERVER_TICK.register { platform?.manager?.tick() }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            platform?.manager?.removeViewer(handler.player.uuid)
        }
        ServerLifecycleEvents.SERVER_STOPPING.register {
            platform?.stop()
            platform = null
        }
    }
}
