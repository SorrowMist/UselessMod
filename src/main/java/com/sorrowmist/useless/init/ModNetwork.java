package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.network.AECancelPacket;
import com.sorrowmist.useless.network.AEReturnOutputTogglePacket;
import com.sorrowmist.useless.network.AETaskProgressPacket;
import com.sorrowmist.useless.network.AETaskProgressRequestPacket;
import com.sorrowmist.useless.network.AutoIOChangePacket;
import com.sorrowmist.useless.network.BeefInvulnerabilitySyncPacket;
import com.sorrowmist.useless.network.BeefInvulnerabilityStatePacket;
import com.sorrowmist.useless.network.EnchantmentSwitchPacket;
import com.sorrowmist.useless.network.FaceModeChangePacket;
import com.sorrowmist.useless.network.ForceBreakKeyPacket;
import com.sorrowmist.useless.network.MiningDataSyncPacket;
import com.sorrowmist.useless.network.ModeTogglePacket;
import com.sorrowmist.useless.network.MultiblockAlloyFurnaceEnergyLimitPacket;
import com.sorrowmist.useless.network.PatternPageChangePacket;
import com.sorrowmist.useless.network.PassiveCraftingSettingsPacket;
import com.sorrowmist.useless.network.PassiveCraftingStatusPacket;
import com.sorrowmist.useless.network.OreGeneratorOutputTogglePacket;
import com.sorrowmist.useless.network.OreGeneratorSettingsPacket;
import com.sorrowmist.useless.network.RedstoneControlPacket;
import com.sorrowmist.useless.network.SelectOmniversalPatternRecipePacket;
import com.sorrowmist.useless.network.TabKeyPressedPacket;
import com.sorrowmist.useless.network.TankClearPacket;
import com.sorrowmist.useless.network.ToolTypeModeSwitchPacket;
import com.sorrowmist.useless.network.DimensionConfigGhostSlotPacket;
import com.sorrowmist.useless.network.DimensionConfigSubmitPacket;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class ModNetwork {
    public static void registerPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(UselessMod.MODID).versioned("2");
        registrar.playToServer(EnchantmentSwitchPacket.TYPE, EnchantmentSwitchPacket.STREAM_CODEC,
                               EnchantmentSwitchPacket::handle
        );
        registrar.playToServer(ToolTypeModeSwitchPacket.TYPE, ToolTypeModeSwitchPacket.STREAM_CODEC,
                               ToolTypeModeSwitchPacket::handle
        );
        registrar.playToServer(TabKeyPressedPacket.TYPE, TabKeyPressedPacket.STREAM_CODEC,
                               TabKeyPressedPacket::handle
        );
        registrar.playToServer(ModeTogglePacket.TYPE, ModeTogglePacket.STREAM_CODEC,
                               ModeTogglePacket::handle
        );
        registrar.playToServer(ForceBreakKeyPacket.TYPE, ForceBreakKeyPacket.STREAM_CODEC,
                               ForceBreakKeyPacket::handle
        );
        registrar.playToServer(TankClearPacket.TYPE, TankClearPacket.STREAM_CODEC,
                               TankClearPacket::handle
        );
        registrar.playToClient(MiningDataSyncPacket.TYPE, MiningDataSyncPacket.STREAM_CODEC,
                               MiningDataSyncPacket::handle
        );
        registrar.playToClient(AETaskProgressPacket.TYPE, AETaskProgressPacket.STREAM_CODEC,
                               AETaskProgressPacket::handle
        );
        registrar.playToServer(AETaskProgressRequestPacket.TYPE, AETaskProgressRequestPacket.STREAM_CODEC,
                               AETaskProgressRequestPacket::handle
        );
        registrar.playToClient(BeefInvulnerabilitySyncPacket.TYPE, BeefInvulnerabilitySyncPacket.STREAM_CODEC,
                               BeefInvulnerabilitySyncPacket::handle
        );
        registrar.playToClient(BeefInvulnerabilityStatePacket.TYPE, BeefInvulnerabilityStatePacket.STREAM_CODEC,
                               BeefInvulnerabilityStatePacket::handle
        );
        registrar.playToServer(PatternPageChangePacket.TYPE, PatternPageChangePacket.STREAM_CODEC,
                               PatternPageChangePacket::handle
        );
        registrar.playToServer(FaceModeChangePacket.TYPE, FaceModeChangePacket.STREAM_CODEC,
                               FaceModeChangePacket::handle
        );
        registrar.playToServer(AutoIOChangePacket.TYPE, AutoIOChangePacket.STREAM_CODEC,
                               AutoIOChangePacket::handle
        );
        registrar.playToServer(RedstoneControlPacket.TYPE, RedstoneControlPacket.STREAM_CODEC,
                               RedstoneControlPacket::handle
        );
        registrar.playToServer(AECancelPacket.TYPE, AECancelPacket.STREAM_CODEC,
                               AECancelPacket::handle
        );
        registrar.playToServer(AEReturnOutputTogglePacket.TYPE, AEReturnOutputTogglePacket.STREAM_CODEC,
                               AEReturnOutputTogglePacket::handle);
        registrar.playToServer(SelectOmniversalPatternRecipePacket.TYPE,
                               SelectOmniversalPatternRecipePacket.STREAM_CODEC,
                               SelectOmniversalPatternRecipePacket::handle);
        registrar.playToServer(PassiveCraftingSettingsPacket.TYPE,
                               PassiveCraftingSettingsPacket.STREAM_CODEC,
                               PassiveCraftingSettingsPacket::handle);
        registrar.playToServer(MultiblockAlloyFurnaceEnergyLimitPacket.TYPE,
                               MultiblockAlloyFurnaceEnergyLimitPacket.STREAM_CODEC,
                               MultiblockAlloyFurnaceEnergyLimitPacket::handle);
        registrar.playToClient(PassiveCraftingStatusPacket.TYPE,
                               PassiveCraftingStatusPacket.STREAM_CODEC,
                               PassiveCraftingStatusPacket::handle);
        registrar.playToServer(OreGeneratorSettingsPacket.TYPE,
                               OreGeneratorSettingsPacket.STREAM_CODEC,
                               OreGeneratorSettingsPacket::handle);
        registrar.playToServer(OreGeneratorOutputTogglePacket.TYPE,
                               OreGeneratorOutputTogglePacket.STREAM_CODEC,
                               OreGeneratorOutputTogglePacket::handle);
        registrar.playToServer(DimensionConfigGhostSlotPacket.TYPE,
                               DimensionConfigGhostSlotPacket.STREAM_CODEC,
                               DimensionConfigGhostSlotPacket::handle);
        registrar.playToServer(DimensionConfigSubmitPacket.TYPE,
                               DimensionConfigSubmitPacket.STREAM_CODEC,
                               DimensionConfigSubmitPacket::handle);
    }
}
