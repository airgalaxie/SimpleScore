package com.r4g3baby.simplescore.bukkit.scheduler

import org.bukkit.entity.Player

interface Scheduler {
    fun runPlayerTask(player: Player, task: Runnable, retired: Runnable)
    fun isPlayerThread(player: Player): Boolean
    fun cancelTasks()
    fun runTask(task: Runnable)
    fun runTaskAsync(task: Runnable)
    fun runTaskTimer(delay: Long, period: Long, task: Runnable)
    fun runTaskTimerAsync(delay: Long, period: Long, task: Runnable)
}
