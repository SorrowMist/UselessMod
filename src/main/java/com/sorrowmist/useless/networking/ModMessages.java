package com.sorrowmist.useless.networking;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModMessages {
    private static SimpleChannel INSTANCE;
    private static int packetId = 0;
    private static int id() { return packetId++; }

    public static void register() {
        SimpleChannel net = NetworkRegistry.ChannelBuilder
                .named( ResourceLocation.fromNamespaceAndPath(UselessMod.MOD_ID, "messages"))
                .networkProtocolVersion(() -> "1.0")
                .clientAcceptedVersions(s -> true)
                .serverAcceptedVersions(s -> true)
                .simpleChannel();

        INSTANCE = net;

        net.messageBuilder(EnchantmentSwitchPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(EnchantmentSwitchPacket::new)
                .encoder(EnchantmentSwitchPacket::toBytes)
                .consumerMainThread(EnchantmentSwitchPacket::handle)
                .add();

        net.messageBuilder(EnhancedChainMiningTogglePacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(EnhancedChainMiningTogglePacket::decode)
                .encoder(EnhancedChainMiningTogglePacket::encode)
                .consumerMainThread(EnhancedChainMiningTogglePacket::handle)
                .add();
        
        net.messageBuilder(ToolModeSwitchPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(ToolModeSwitchPacket::new)
                .encoder(ToolModeSwitchPacket::encode)
                .consumerMainThread(ToolModeSwitchPacket::handle)
                .add();
        
        net.messageBuilder(ForceMiningTogglePacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(ForceMiningTogglePacket::decode)
                .encoder(ForceMiningTogglePacket::encode)
                .consumerMainThread(ForceMiningTogglePacket::handle)
                .add();

        net.messageBuilder(TriggerForceMiningPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(TriggerForceMiningPacket::decode)
                .encoder(TriggerForceMiningPacket::encode)
                .consumerMainThread(TriggerForceMiningPacket::handle)
                .add();

        net.messageBuilder(SetMasterPatternPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SetMasterPatternPacket::decode)
                .encoder(SetMasterPatternPacket::encode)
                .consumerMainThread(SetMasterPatternPacket::handle)
                .add();

        net.messageBuilder(SetSlavePatternPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(SetSlavePatternPacket::decode)
                .encoder(SetSlavePatternPacket::encode)
                .consumerMainThread(SetSlavePatternPacket::handle)
                .add();

        net.messageBuilder(ResetMasterPatternPacket.class, id(), NetworkDirection.PLAY_TO_SERVER)
                .decoder(ResetMasterPatternPacket::decode)
                .encoder(ResetMasterPatternPacket::encode)
                .consumerMainThread(ResetMasterPatternPacket::handle)
                .add();

    }

    public static <MSG> void sendToServer(MSG message) {
        INSTANCE.sendToServer(message);
    }

    public static <MSG> void sendToPlayer(MSG message, ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}