package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;

public final class ModDamageTypes {
    public static final ResourceKey<DamageType> BEEF_TOOL = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            UselessMod.id("beef_tool")
    );

    private ModDamageTypes() {
    }

    public static DamageSource beefTool(ServerLevel level, Player player) {
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(BEEF_TOOL);
        return new DamageSource(holder, player, player);
    }
}
