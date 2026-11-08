package com.r4g3baby.simplescore.bukkit.scheduler

import com.r4g3baby.simplescore.BukkitPlugin
import org.bukkit.Bukkit
import org.bukkit.entity.Player

class BukkitScheduler(private val plugin: BukkitPlugin) : Scheduler {
    private val bukkitScheduler = plugin.server.scheduler

    override fun isPlayerThread(player: Player): Boolean = Bukkit.isPrimaryThread()

    override fun runPlayerTask(player: Player, task: Runnable, retired: Runnable) {
        val update = Runnable { if (player.isOnline) task.run() else retired.run() }
        if (Bukkit.isPrimaryThread()) update.run() else bukkitScheduler.runTask(plugin, update)
    }

    override fun cancelTasks() {
        bukkitScheduler.cancelTasks(plugin)
    }

    override fun runTask(task: Runnable) {
        bukkitScheduler.runTask(plugin, task)
    }

    override fun runTaskAsync(task: Runnable) {
        bukkitScheduler.runTaskAsynchronously(plugin, task)
    }

    override fun runTaskTimer(delay: Long, period: Long, task: Runnable) {
        bukkitScheduler.runTaskTimer(plugin, task, delay, period)
    }

    override fun runTaskTimerAsync(delay: Long, period: Long, task: Runnable) {
        bukkitScheduler.runTaskTimerAsynchronously(plugin, task, delay, period)
    }
}
