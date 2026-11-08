package com.r4g3baby.simplescore.fabric.smoke;

import com.mojang.authlib.GameProfile;
import com.r4g3baby.simplescore.api.Manager;
import com.r4g3baby.simplescore.api.scoreboard.data.Priority;
import com.r4g3baby.simplescore.api.scoreboard.data.Provider;
import com.r4g3baby.simplescore.fabric.FabricManager;
import com.r4g3baby.simplescore.fabric.scoreboard.SidebarOwnership;
import io.netty.channel.ChannelFutureListener;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Separate test mod, never included in the distributable or in target/. */
public final class ProductionSmoke implements ModInitializer {
    private boolean ran;
    @Override public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ran) return;
            ran = true;
            try {
                verify(server);
                Files.writeString(FabricLoader.getInstance().getGameDir().resolve("smoke-success.txt"), "Production JAR smoke passed\n");
                System.out.println("SIMPLESCORE_PRODUCTION_SMOKE_PASSED");
            } catch (Throwable failure) {
                failure.printStackTrace();
                try { Files.writeString(FabricLoader.getInstance().getGameDir().resolve("smoke-failure.txt"), failure.toString()); }
                catch (Exception ignored) { }
                System.out.println("SIMPLESCORE_PRODUCTION_SMOKE_FAILED");
            } finally { server.halt(false); }
        });
    }
    private static void verify(MinecraftServer server) throws Exception {
        require(!FabricLoader.getInstance().isModLoaded("fabric-language-kotlin"), "Must run without Fabric Language Kotlin");
        try { Class.forName("kotlin.jvm.internal.Intrinsics"); throw new AssertionError("Unrelocated Kotlin leaked onto production classpath"); }
        catch (ClassNotFoundException expected) { }
        FabricManager manager = (FabricManager) Manager.<ServerPlayer>getInstance();
        ServerPlayer alpha = player(server, "SmokeAlpha");
        ServerPlayer beta = player(server, "SmokeBeta");
        server.getPlayerList().getPlayers().add(alpha);
        server.getPlayerList().getPlayers().add(beta);
        server.getPlayerList().getPlayersByUUID().put(alpha.getUUID(), alpha);
        server.getPlayerList().getPlayersByUUID().put(beta.getUUID(), beta);
        RecordingConnection a = (RecordingConnection) connections.get(0);
        RecordingConnection b = (RecordingConnection) connections.get(1);
        try {
            manager.tick();
            require(manager.getViewer(alpha.getUUID()) != null, "Join/viewer creation");
            require(a.packets.stream().anyMatch(p -> p instanceof ClientboundSetObjectivePacket), "Objective creation");
            require(a.packets.stream().anyMatch(p -> p instanceof ClientboundSetScorePacket s && s.display().orElse(Component.empty()).getString().contains("SmokeAlpha")), "Player-specific Alpha placeholders");
            require(b.packets.stream().anyMatch(p -> p instanceof ClientboundSetScorePacket s && s.display().orElse(Component.empty()).getString().contains("SmokeBeta")), "Player-specific Beta placeholders");
            require(a.packets.stream().anyMatch(p -> p instanceof ClientboundSetScorePacket s && s.numberFormat().isPresent()), "Hidden score numbers");
            require(server.getScoreboard().getObjectives().isEmpty(), "No shared scoreboard mutations");
            var source = alpha.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
            a.packets.clear();
            command(server, source, "sb toggle off");
            manager.tick();
            require(manager.getViewer(alpha.getUUID()).isScoreboardHidden(), "Toggle off");
            require(a.packets.stream().anyMatch(p -> p instanceof ClientboundSetObjectivePacket s && s.getMethod() == ClientboundSetObjectivePacket.METHOD_REMOVE), "Objective cleanup");
            command(server, source, "sb toggle on");
            command(server, source, "sb force pvp");
            manager.tick();
            require(manager.getViewer(alpha.getUUID()).getScoreboard(new Provider("SimpleScore", null)).getName().equals("pvp"), "Force scoreboard");
            command(server, source, "sb force none");
            command(server, source, "sb info");
            command(server, source, "sb version");
            command(server, source, "sb help");
            command(server, server.createCommandSourceStack(), "sb toggle SmokeBeta off");
            require(manager.getViewer(beta.getUUID()).isScoreboardHidden(), "Console target command");
            command(server, server.createCommandSourceStack(), "sb toggle SmokeBeta on");
            command(server, source, "sb reload");
            manager.tick();
            require(manager.getScoreboard("default") != null, "Reload retains scoreboards");
            require(manager.getVarReplacer().replace("%unknown_value%", alpha).equals("%unknown_value%"), "Unknown tokens remain intact");
            if (FabricLoader.getInstance().isModLoaded("placeholder-api")) {
                String result = manager.getVarReplacer().replace("%player:name%", alpha);
                require(com.r4g3baby.simplescore.fabric.scoreboard.Text.INSTANCE.component(result).getString().equals("SmokeAlpha"), "Patbox native player placeholder: " + result);
                result = manager.getVarReplacer().replace("%simplescore:scoreboard%", alpha);
                require(com.r4g3baby.simplescore.fabric.scoreboard.Text.INSTANCE.component(result).getString().equals("default"), "Patbox SimpleScore export: " + result);
            } else require(manager.getVarReplacer().replace("%player:name%", alpha).equals("%player:name%"), "Patbox absent is supported");
            verifyCompass(server, manager, alpha, a);
            // A foreign sidebar must keep ownership while SimpleScore updates its own objective.
            Objective external = new Objective(new Scoreboard(), "foreign", ObjectiveCriteria.DUMMY, Component.literal("Foreign"), ObjectiveCriteria.RenderType.INTEGER, false, null);
            alpha.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, external));
            require(SidebarOwnership.INSTANCE.current(alpha.getUUID()).equals("foreign"), "Production mixin sees foreign sidebar packets");
            a.packets.clear();
            manager.tick();
            require(a.packets.stream().noneMatch(p -> p instanceof ClientboundSetDisplayObjectivePacket), "Foreign sidebar not overridden");
            alpha.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, null));
            a.packets.clear();
            manager.tick();
            require(a.packets.stream().anyMatch(p -> p instanceof ClientboundSetDisplayObjectivePacket s && s.getObjectiveName() != null), "Resume after foreign sidebar clears");
            manager.removeViewer(alpha.getUUID());
            require(manager.getViewer(alpha.getUUID()) == null, "Disconnect cleanup");
        } finally {
            server.getPlayerList().getPlayers().remove(alpha);
            server.getPlayerList().getPlayers().remove(beta);
            server.getPlayerList().getPlayersByUUID().remove(alpha.getUUID());
            server.getPlayerList().getPlayersByUUID().remove(beta.getUUID());
            manager.removeViewer(alpha.getUUID());
            manager.removeViewer(beta.getUUID());
        }
    }
    private static void verifyCompass(MinecraftServer server, FabricManager manager, ServerPlayer player, RecordingConnection connection) throws Exception {
        var file = FabricLoader.getInstance().getConfigDir().resolve("simplescore/compass.yml");
        var source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS);
        require(manager.getCompass().getEnabled(), "Compass enabled by default");
        Files.writeString(file, "enabled: false\n");
        command(server, source, "sb reload");
        require(!manager.getCompass().getEnabled(), "Compass can still be disabled");
        require(manager.getVarReplacer().replace("%compass_bar%", player).isEmpty(), "Disabled compass token is empty");
        Files.writeString(file, "enabled: true\nlayout: {width-pixels: 90}\n");
        command(server, source, "sb reload");
        require(manager.getCompass().getEnabled(), "Reload enables compass");
        player.setYRot(180);
        String north = manager.getVarReplacer().replace("%compass_bar%", player);
        require(north.contains("[N]"), "North-facing compass");
        if (FabricLoader.getInstance().isModLoaded("placeholder-api")) {
            var text = com.r4g3baby.simplescore.fabric.scoreboard.Text.INSTANCE;
            String nativeToken = manager.getVarReplacer().replace("%compass:bar%", player);
            require(text.legacy(text.component(north)).equals(text.legacy(text.component(nativeToken))), "Patbox compass preserves native text and styles");
        }
        var line = new com.r4g3baby.simplescore.core.scoreboard.line.StaticLine<ServerPlayer>("%compass_bar%", 1, List.of(), List.of());
        var score = new com.r4g3baby.simplescore.core.scoreboard.ScoreboardScore<ServerPlayer>("1", List.of(line), true, List.of());
        var board = new com.r4g3baby.simplescore.core.scoreboard.Scoreboard<ServerPlayer>("compass-smoke", List.of(), List.of(score), List.of());
        manager.addScoreboard(board);
        var provider = new Provider("SimpleScore", null);
        manager.getOrCreateViewer(player).setScoreboard(board, provider, Priority.Highest);
        connection.packets.clear();
        manager.tick();
        require(connection.packets.stream().anyMatch(p -> p instanceof ClientboundSetScorePacket row && row.display().orElse(Component.empty()).getString().contains("[N]")), "Compass reaches scoreboard packet");
        player.setYRot(270);
        connection.packets.clear();
        manager.tick();
        require(connection.packets.stream().anyMatch(p -> p instanceof ClientboundSetScorePacket row && row.display().orElse(Component.empty()).getString().contains("[E]")), "Rotation updates compass packet each tick");
        Files.writeString(file, "enabled: true\nlayout: {width-pixels: -1}\n");
        boolean rejected = false;
        try { manager.loadConfiguration(); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected && manager.getCompass().getEnabled(), "Invalid reload retains previous compass");
        require(manager.getVarReplacer().replace("%compass_bar%", player).contains("[E]"), "Previous compass still renders after rejected reload");
        Files.writeString(file, "enabled: false\n");
        command(server, source, "sb reload");
        require(!manager.getCompass().getEnabled(), "Reload disables compass and releases renderer");
        require(manager.getVarReplacer().replace("%compass_bar%", player).isEmpty(), "Disabled compass token becomes empty immediately");
        if (FabricLoader.getInstance().isModLoaded("placeholder-api")) {
            String value = manager.getVarReplacer().replace("%compass:bar%", player);
            require(com.r4g3baby.simplescore.fabric.scoreboard.Text.INSTANCE.component(value).getString().isEmpty(), "Patbox compass becomes empty after disable");
        }
        manager.getOrCreateViewer(player).removeScoreboard(provider);
        manager.removeScoreboard(board);
        manager.tick();
    }
    private static void command(MinecraftServer server, CommandSourceStack source, String text) throws Exception {
        require(server.getCommands().getDispatcher().execute(text, source) == 1, "Command: " + text);
    }
    private static final List<Connection> connections = new ArrayList<>();
    private static ServerPlayer player(MinecraftServer server, String name) {
        var profile = new GameProfile(UUID.randomUUID(), name);
        var player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        var connection = new RecordingConnection();
        connections.add(connection);
        new ServerGamePacketListenerImpl(server, connection, player, CommonListenerCookie.createInitial(profile, false));
        return player;
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static final class RecordingConnection extends Connection {
        final List<Packet<?>> packets = new ArrayList<>();
        RecordingConnection() { super(PacketFlow.SERVERBOUND); }
        @Override public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) { packets.add(packet); }
        @Override public boolean isConnected() { return true; }
        @Override public boolean isMemoryConnection() { return true; }
    }
}
