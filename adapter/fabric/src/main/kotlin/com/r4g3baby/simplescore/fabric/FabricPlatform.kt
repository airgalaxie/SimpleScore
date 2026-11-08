package com.r4g3baby.simplescore.fabric

import com.r4g3baby.simplescore.ProjectInfo
import com.r4g3baby.simplescore.api.Manager
import com.r4g3baby.simplescore.api.Platform
import com.r4g3baby.simplescore.api.scoreboard.data.Provider
import com.r4g3baby.simplescore.core.util.checkForUpdates
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger

class FabricPlatform(val server: MinecraftServer) : Platform<ServerPlayer> {
    @get:JvmName("platformLogger")
    val logger: Logger = Logger.getLogger("SimpleScore")
    @get:JvmName("platformDataFolder")
    val dataFolder: File = FabricLoader.getInstance().configDir.resolve("simplescore").toFile()
    override fun getLogger() = logger
    override fun getDataFolder() = dataFolder
    override val provider = Provider("SimpleScore")
    override val manager = FabricManager(this)

    fun start() {
        Manager.setInstance(manager)
        manager.onLoad()
        manager.onEnable()
        if (manager.config.checkForUpdates) CompletableFuture.runAsync {
            checkForUpdates({ version -> logger.info("SimpleScore $version available: ${ProjectInfo.DOWNLOAD_URL}") })
        }
        logger.info("SimpleScore ${ProjectInfo.VERSION} started with its bundled Kotlin runtime.")
    }
    fun stop() = manager.onDisable()
}
