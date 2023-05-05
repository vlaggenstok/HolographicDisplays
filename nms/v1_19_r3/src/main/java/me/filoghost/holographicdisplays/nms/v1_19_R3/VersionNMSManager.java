/*
 * Copyright (C) filoghost and contributors
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package me.filoghost.holographicdisplays.nms.v1_19_R3;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import me.filoghost.fcommons.logging.ErrorCollector;
import me.filoghost.fcommons.logging.Log;
import me.filoghost.fcommons.reflection.ReflectField;
import me.filoghost.holographicdisplays.nms.common.EntityID;
import me.filoghost.holographicdisplays.nms.common.FallbackEntityIDGenerator;
import me.filoghost.holographicdisplays.nms.common.NMSErrors;
import me.filoghost.holographicdisplays.nms.common.NMSManager;
import me.filoghost.holographicdisplays.nms.common.PacketListener;
import me.filoghost.holographicdisplays.nms.common.entity.ClickableNMSPacketEntity;
import me.filoghost.holographicdisplays.nms.common.entity.ItemNMSPacketEntity;
import me.filoghost.holographicdisplays.nms.common.entity.TextNMSPacketEntity;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.network.PlayerConnection;
import net.minecraft.world.entity.Entity;
import org.bukkit.craftbukkit.v1_19_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class VersionNMSManager implements NMSManager {

    private static final ReflectField<AtomicInteger> ENTITY_ID_COUNTER_FIELD = ReflectField.lookup(AtomicInteger.class, Entity.class, "d");
    private static final ReflectField<NetworkManager> NETWORK_MANAGER_FIELD = ReflectField.lookup(NetworkManager.class, PlayerConnection.class, "h");
    private final Supplier<Integer> entityIDGenerator;

    public VersionNMSManager(ErrorCollector errorCollector) {
        this.entityIDGenerator = getEntityIDGenerator(errorCollector);

        // Force initialization of class to eventually throw exceptions early
        DataWatcherKey.ENTITY_STATUS.getIndex();
    }

    private Supplier<Integer> getEntityIDGenerator(ErrorCollector errorCollector) {
        try {
            AtomicInteger nmsEntityIDCounter = ENTITY_ID_COUNTER_FIELD.getStatic();
            return nmsEntityIDCounter::incrementAndGet;
        } catch (ReflectiveOperationException e) {
            errorCollector.add(e, NMSErrors.EXCEPTION_GETTING_ENTITY_ID_GENERATOR);
            return new FallbackEntityIDGenerator();
        }
    }

    private EntityID newEntityID() {
        return new EntityID(entityIDGenerator);
    }

    @Override
    public TextNMSPacketEntity newTextPacketEntity() {
        return new VersionTextNMSPacketEntity(newEntityID());
    }

    @Override
    public ItemNMSPacketEntity newItemPacketEntity() {
        return new VersionItemNMSPacketEntity(newEntityID(), newEntityID());
    }

    @Override
    public ClickableNMSPacketEntity newClickablePacketEntity() {
        return new VersionClickableNMSPacketEntity(newEntityID());
    }

    @Override
    public void injectPacketListener(Player player, PacketListener packetListener) {
        modifyPipeline(player, (ChannelPipeline pipeline) -> {
            ChannelHandler currentListener = pipeline.get(InboundPacketHandler.HANDLER_NAME);
            if (currentListener != null) {
                pipeline.remove(InboundPacketHandler.HANDLER_NAME);
            }
            pipeline.addBefore("packet_handler", InboundPacketHandler.HANDLER_NAME, new InboundPacketHandler(player, packetListener));
        });
    }

    @Override
    public void uninjectPacketListener(Player player) {
        modifyPipeline(player, (ChannelPipeline pipeline) -> {
            ChannelHandler currentListener = pipeline.get(InboundPacketHandler.HANDLER_NAME);
            if (currentListener != null) {
                pipeline.remove(InboundPacketHandler.HANDLER_NAME);
            }
        });
    }

    /*
     * Modifying the pipeline in the main thread can cause deadlocks, delays and other concurrency issues,
     * which can be avoided by using the event loop. Thanks to ProtocolLib for this insight.
     */
    private void modifyPipeline(Player player, Consumer<ChannelPipeline> pipelineModifierTask) {
        PlayerConnection playerConnection = ((CraftPlayer) player).getHandle().b;
        NetworkManager networkManager;
        try {
            networkManager = NETWORK_MANAGER_FIELD.get(playerConnection);
        } catch (ReflectiveOperationException e) {
            Log.warning(NMSErrors.EXCEPTION_MODIFYING_CHANNEL_PIPELINE, e);
            return;
        }
        Channel channel = networkManager.m;

        channel.eventLoop().execute(() -> {
            if (!player.isOnline()) {
                return;
            }
            try {
                pipelineModifierTask.accept(channel.pipeline());
            } catch (Exception e) {
                Log.warning(NMSErrors.EXCEPTION_MODIFYING_CHANNEL_PIPELINE, e);
            }
        });
    }

}