package com.r4g3baby.simplescore.bukkit.scoreboard

import com.r4g3baby.simplescore.bukkit.BukkitManager
import com.r4g3baby.simplescore.bukkit.protocol.ProtocolHandler
import com.r4g3baby.simplescore.bukkit.protocol.model.ObjectiveScore
import com.r4g3baby.simplescore.bukkit.protocol.model.ObjectiveTitle
import com.r4g3baby.simplescore.bukkit.scoreboard.data.Viewer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.entity.Player

class ScoreboardTask(
    private val manager: BukkitManager,
    private val protocolHandler: ProtocolHandler
) : Runnable {
    private val pending = ConcurrentHashMap.newKeySet<UUID>()
    // A queued player update must still see render requests from earlier animation ticks.
    private val lineRevisions = ConcurrentHashMap<String, Long>()
    private val renderedRevisions = ConcurrentHashMap<UUID, MutableMap<String, Long>>()
    private val emptyTitle = ObjectiveTitle("-1", "")

    override fun run() {
        val activeLines = mutableSetOf<String>()
        manager.scoreboards.forEach { scoreboard ->
            synchronized(scoreboard) {
                scoreboard.tick()
                val lines = scoreboard.titles + scoreboard.scores.flatMap { it.lines }
                lines.forEach { line ->
                    activeLines.add(line.uid)
                    lineRevisions.compute(line.uid) { _, revision ->
                        (revision ?: 0L) + if (line.shouldRender()) 1L else 0L
                    }
                }
            }
        }
        lineRevisions.keys.retainAll(activeLines)
        val viewers = manager.viewers
        renderedRevisions.keys.retainAll(viewers.mapNotNull { it.reference.get()?.uniqueId }.toSet())

        viewers.forEach { viewer ->
            val player = viewer.reference.get() ?: return@forEach
            val id = player.uniqueId
            if (!pending.add(id)) return@forEach
            try {
                manager.updatePlayer(player, Runnable {
                    try {
                        if (player.isOnline && manager.getViewer(id) === viewer) {
                            val scoreboard = viewer.scoreboard
                            if (scoreboard == null) updateViewer(viewer, player)
                            else synchronized(scoreboard) { updateViewer(viewer, player) }
                        }
                    } finally {
                        pending.remove(id)
                    }
                }, Runnable { pending.remove(id) })
            } catch (exception: RuntimeException) {
                pending.remove(id)
                throw exception
            }
        }
    }

    private fun updateViewer(viewer: Viewer, player: Player) {
        val scoreboard = if (!viewer.isScoreboardHidden) viewer.scoreboard else null
        var playerObjective = protocolHandler.getObjective(player)
        if (scoreboard == null || !scoreboard.canSee(player, manager.varReplacer)) {
            if (playerObjective != null) protocolHandler.removeObjective(player)
            return
        }

        val rendered = renderedRevisions.computeIfAbsent(player.uniqueId) { mutableMapOf() }
        rendered.keys.retainAll(scoreboard.scores.map { it.uid }.toSet() + "title")
        val title = scoreboard.getTitle(player, manager.varReplacer).let { line ->
            if (line == null) return@let emptyTitle

            val currentTitle = playerObjective?.title
            val revision = lineRevisions[line.uid] ?: 0L
            if (currentTitle == null || currentTitle.lineUID != line.uid || rendered["title"] != revision) {
                ObjectiveTitle(line.uid, line.currentText(player, manager.varReplacer)).also {
                    rendered["title"] = revision
                }
            } else currentTitle
        }

        if (playerObjective == null) playerObjective = protocolHandler.createObjective(player, title)

        val scores = mutableMapOf<String, ObjectiveScore>()
        scoreboard.getScores(player, manager.varReplacer).forEach scoresForEach@{ scoreboardScore ->
            val value = scoreboardScore.getValueAsInteger(player, manager.varReplacer) ?: return@scoresForEach
            val line = scoreboardScore.getLine(player, manager.varReplacer) ?: return@scoresForEach

            val currentScore = playerObjective.scores[scoreboardScore.uid]
            val revision = lineRevisions[line.uid] ?: 0L
            val text = if (currentScore == null || currentScore.lineUID != line.uid || rendered[scoreboardScore.uid] != revision) {
                line.currentText(player, manager.varReplacer).also { rendered[scoreboardScore.uid] = revision }
            } else currentScore.text

            scores[scoreboardScore.uid] = ObjectiveScore(line.uid, text, value, scoreboardScore.hideNumber)
        }

        protocolHandler.updateScoreboard(player, title, scores)
    }
}
