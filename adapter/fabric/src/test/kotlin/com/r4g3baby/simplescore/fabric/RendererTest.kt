package com.r4g3baby.simplescore.fabric

import com.r4g3baby.simplescore.fabric.scoreboard.FabricRenderer
import com.r4g3baby.simplescore.fabric.scoreboard.SidebarOwnership
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.*
import net.minecraft.network.chat.Component
import net.minecraft.world.scores.Objective
import net.minecraft.world.scores.Scoreboard
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.criteria.ObjectiveCriteria
import java.util.UUID
import kotlin.test.*
import org.junit.BeforeClass

class RendererTest {
    companion object {
        @BeforeClass @JvmStatic fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }
    private class Target : FabricRenderer.Target {
        override val uuid = UUID.randomUUID()
        val packets = mutableListOf<Packet<*>>()
        override fun send(packet: Packet<*>) { SidebarOwnership.observe(uuid, packet); packets += packet }
        override fun isConnected() = true
        override fun fallbackObjective(): Objective? = null
    }
    private val line = FabricRenderer.Line("<green>hello</green>", 7, true)
    @Test fun `objectives and rows are private to each viewer and duplicate text uses unique entries`() {
        val renderer = FabricRenderer()
        val a = Target(); val b = Target()
        renderer.render(a, "Alpha", mapOf("a" to line, "b" to line))
        renderer.render(b, "Beta", mapOf("a" to line.copy(text = "private")))
        val aObjective = a.packets.filterIsInstance<ClientboundSetObjectivePacket>().single()
        val bObjective = b.packets.filterIsInstance<ClientboundSetObjectivePacket>().single()
        assertNotEquals(aObjective.objectiveName, bObjective.objectiveName)
        val scores = a.packets.filterIsInstance<ClientboundSetScorePacket>()
        assertEquals(setOf("a", "b"), scores.map { it.owner() }.toSet())
        assertTrue(scores.all { it.display().get().string == "hello" && it.numberFormat().isPresent })
        assertTrue(b.packets.filterIsInstance<ClientboundSetScorePacket>().all { it.display().get().string == "private" })
    }
    @Test fun `unchanged renders send nothing and updates only send changed and removed rows`() {
        val renderer = FabricRenderer(); val target = Target()
        renderer.render(target, "Title", mapOf("a" to line, "b" to line))
        target.packets.clear()
        renderer.render(target, "Title", mapOf("a" to line, "b" to line))
        assertTrue(target.packets.isEmpty())
        renderer.render(target, "New", mapOf("a" to line.copy(score = 8)))
        assertEquals(1, target.packets.filterIsInstance<ClientboundSetObjectivePacket>().size)
        assertEquals(1, target.packets.filterIsInstance<ClientboundSetScorePacket>().size)
        assertEquals("b", target.packets.filterIsInstance<ClientboundResetScorePacket>().single().owner())
        assertTrue(target.packets.filterIsInstance<ClientboundSetObjectivePacket>().all { it.method == ClientboundSetObjectivePacket.METHOD_CHANGE })
    }
    @Test fun `foreign sidebar retains ownership and SimpleScore resumes when it clears`() {
        val renderer = FabricRenderer(); val target = Target()
        renderer.render(target, "Title", mapOf("a" to line))
        val foreign = Objective(Scoreboard(), "foreign", ObjectiveCriteria.DUMMY, Component.literal("Foreign"), ObjectiveCriteria.RenderType.INTEGER, false, null)
        target.send(ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, foreign))
        target.packets.clear()
        renderer.render(target, "Title", mapOf("a" to line.copy(text = "updated")))
        assertTrue(target.packets.none { it is ClientboundSetDisplayObjectivePacket })
        target.send(ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, null))
        target.packets.clear()
        renderer.render(target, "Title", mapOf("a" to line))
        assertNotNull(target.packets.filterIsInstance<ClientboundSetDisplayObjectivePacket>().single().objectiveName)
    }
    @Test fun `removal cleans only the private objective without overriding a foreign display`() {
        val renderer = FabricRenderer(); val target = Target()
        renderer.render(target, "Title", mapOf("a" to line))
        val foreign = Objective(Scoreboard(), "foreign", ObjectiveCriteria.DUMMY, Component.literal("Foreign"), ObjectiveCriteria.RenderType.INTEGER, false, null)
        target.send(ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, foreign))
        target.packets.clear()
        renderer.remove(target)
        assertEquals(ClientboundSetObjectivePacket.METHOD_REMOVE, target.packets.filterIsInstance<ClientboundSetObjectivePacket>().single().method)
        assertTrue(target.packets.none { it is ClientboundSetDisplayObjectivePacket })
        target.packets.clear()
        renderer.remove(target)
        assertTrue(target.packets.isEmpty())
    }
    @Test fun `hiding then showing creates the complete private scoreboard again`() {
        val renderer = FabricRenderer(); val target = Target()
        renderer.render(target, "Title", mapOf("a" to line))
        renderer.remove(target)
        target.packets.clear()
        renderer.render(target, "Title", mapOf("a" to line))
        assertEquals(ClientboundSetObjectivePacket.METHOD_ADD, target.packets.filterIsInstance<ClientboundSetObjectivePacket>().single().method)
        assertEquals(1, target.packets.filterIsInstance<ClientboundSetScorePacket>().size)
    }
}
