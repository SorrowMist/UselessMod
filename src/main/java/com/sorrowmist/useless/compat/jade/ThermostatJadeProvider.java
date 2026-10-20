package com.sorrowmist.useless.compat.jade;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blockentities.ThermostatBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade 温度显示：悬停「温度调节器」时展示当前设定温度。
 *
 * <p>温度已由 {@link ThermostatBlockEntity#getUpdateTag} 同步到客户端，因此这里直接读客户端
 * 方块实体即可，<b>不需要</b> {@code IServerDataProvider}。</p>
 */
public enum ThermostatJadeProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final ResourceLocation UID = UselessMod.id("temperature_regulator");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof ThermostatBlockEntity thermostat)) {
            return;
        }
        if (!thermostat.isEnabled()) {
            tooltip.add(Component.translatable("jade.useless_mod.thermostat.disabled")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        long kelvin = thermostat.getTemperature();
        tooltip.add(Component.translatable("jade.useless_mod.thermostat.temperature",
                        kelvin, String.format("%.2f", kelvin - 273.15D))
                .withStyle(ChatFormatting.GOLD));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
