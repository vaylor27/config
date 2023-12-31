package net.vakror.jamesconfig.config.packet;

import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.vakror.jamesconfig.JamesConfigMod;
import net.vakror.jamesconfig.config.config.Config;
import net.vakror.jamesconfig.config.config.object.ConfigObject;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;

public class ArchModPackets {
    public static final ResourceLocation syncPacketId = new ResourceLocation(JamesConfigMod.MOD_ID,"config_sync_packet");
    public static final ResourceLocation reloadPacketId = new ResourceLocation(JamesConfigMod.MOD_ID,"reload_config_packet");

    public static void register(){
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, syncPacketId,(buf, context)-> new SyncAllConfigsS2CPacket(buf).handle());
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, reloadPacketId,(buf, context)-> new ReloadConfigS2CPacket(buf).handle());
    }

    public static void onLogIn(ServerPlayer player){
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        Multimap<ResourceLocation, ConfigObject> map = Multimaps.newMultimap(new HashMap<>(), ArrayList::new);
        for (Config config : JamesConfigMod.CONFIGS.values().stream().filter(Config::shouldSync).toList()) {
            map.putAll(config.getName(), config.getAll());
        }
        SyncAllConfigsS2CPacket syncAllConfigsS2CPacket = new SyncAllConfigsS2CPacket(map);
        syncAllConfigsS2CPacket.encode(buf);
        NetworkManager.sendToPlayer(player, syncPacketId, buf);
    }

    public static void sendReloadPacket(ServerPlayer player, @NotNull ResourceLocation configToSync) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ReloadConfigS2CPacket reloadConfigS2CPacket = new ReloadConfigS2CPacket(configToSync);
        reloadConfigS2CPacket.encode(buf);
        NetworkManager.sendToPlayer(player, reloadPacketId, buf);
    }

    public static void sendReloadPacket(ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ReloadConfigS2CPacket reloadConfigS2CPacket = new ReloadConfigS2CPacket(JamesConfigMod.CONFIGS.keySet().stream().toList());
        reloadConfigS2CPacket.encode(buf);
        NetworkManager.sendToPlayer(player, reloadPacketId, buf);
    }

    public static void sendSyncPacket(Iterable<ServerPlayer> players) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        Multimap<ResourceLocation, ConfigObject> map = Multimaps.newMultimap(new HashMap<>(), ArrayList::new);
        for (Config config : JamesConfigMod.CONFIGS.values().stream().filter(Config::shouldSync).toList()) {
            map.putAll(config.getName(), config.getAll());
        }
        SyncAllConfigsS2CPacket syncAllConfigsS2CPacket = new SyncAllConfigsS2CPacket(map);
        syncAllConfigsS2CPacket.encode(buf);
        NetworkManager.sendToPlayers(players, syncPacketId, buf);
    }

    public static void sendSyncPacket(Iterable<ServerPlayer> players, ResourceLocation location) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        Multimap<ResourceLocation, ConfigObject> map = Multimaps.newMultimap(new HashMap<>(), ArrayList::new);
        map.putAll(location, JamesConfigMod.CONFIGS.get(location).getAll());
        SyncAllConfigsS2CPacket syncAllConfigsS2CPacket = new SyncAllConfigsS2CPacket(map);
        syncAllConfigsS2CPacket.encode(buf);
        NetworkManager.sendToPlayers(players, syncPacketId, buf);
    }
}
