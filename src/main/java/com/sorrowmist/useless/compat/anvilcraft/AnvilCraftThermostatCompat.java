package com.sorrowmist.useless.compat.anvilcraft;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blockentities.ThermostatBlockEntity;
import com.sorrowmist.useless.content.blockentities.ThermostatHeatHook;
import com.sorrowmist.useless.content.blocks.TemperatureRegulatorBlock;
import com.sorrowmist.useless.content.recipe.AdapterGuards;
import dev.dubhe.anvilcraft.api.heat.HeatRecorder;
import dev.dubhe.anvilcraft.api.heat.HeatTier;
import dev.dubhe.anvilcraft.api.heat.HeatTierLine;
import dev.dubhe.anvilcraft.api.heat.HeaterInfo;
import dev.dubhe.anvilcraft.api.heat.HeaterManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.Set;

/** 将温度调节器接入铁砧工艺的加热方块系统。 */
public final class AnvilCraftThermostatCompat {
    public static final String MOD_ID = "anvilcraft";

    private static final int MAX_HEAT_LEVEL = 5;
    private static final HeatTierLine HEAT_TIERS = HeatTierLine.builder()
            .addPointInTick(1, HeatTier.NORMAL, 2)
            .addPointInTick(2, HeatTier.HEATED, 2)
            .addPointInTick(3, HeatTier.REDHOT, 2)
            .addPointInTick(4, HeatTier.GLOWING, 2)
            .addPointInTick(5, HeatTier.INCANDESCENT, 2)
            .addPointInTick(HeatTier.OVERHEATED, 2)
            .build();

    private static volatile HeaterInfo<ThermostatBlockEntity> heaterInfo;
    private static volatile boolean disabled;

    private AnvilCraftThermostatCompat() {
    }

    public static void register() {
        ThermostatHeatHook.registerDriver(MOD_ID, AnvilCraftThermostatCompat::registerProducer);
    }

    private static void registerProducer(ServerLevel level, BlockPos pos, ThermostatBlockEntity thermostat) {
        if (disabled) return;
        try {
            HeaterManager.addProducer(pos, level, heaterInfo());
        } catch (LinkageError | RuntimeException exception) {
            disabled = true;
            ThermostatHeatHook.removeDriver(MOD_ID);
            UselessMod.LOGGER.error("Disabling AnvilCraft thermostat heat compatibility; Cause: {}",
                    AdapterGuards.describe(exception));
        }
    }

    private static HeaterInfo<ThermostatBlockEntity> heaterInfo() {
        HeaterInfo<ThermostatBlockEntity> current = heaterInfo;
        if (current != null) return current;
        synchronized (AnvilCraftThermostatCompat.class) {
            if (heaterInfo == null) {
                heaterInfo = HeatRecorder.registerProducerInfo(HeaterInfo.blockEntity(
                        AnvilCraftThermostatCompat::getThermostat,
                        thermostat -> Set.of(thermostat.getBlockPos().above()),
                        HEAT_TIERS,
                        thermostat -> heatLevel(thermostat.getTemperature())));
            }
            return heaterInfo;
        }
    }

    private static Optional<ThermostatBlockEntity> getThermostat(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return Optional.empty();
        if (level.getBlockEntity(pos) instanceof ThermostatBlockEntity thermostat && thermostat.isEnabled()) {
            return Optional.of(thermostat);
        }
        return Optional.empty();
    }

    private static int heatLevel(long temperature) {
        return (int) Math.min(MAX_HEAT_LEVEL, temperature / TemperatureRegulatorBlock.LIGHT_START_KELVIN);
    }
}
