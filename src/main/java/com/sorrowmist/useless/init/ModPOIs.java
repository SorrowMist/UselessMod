package com.sorrowmist.useless.init;

import com.google.common.collect.ImmutableSet;
import com.sorrowmist.useless.UselessMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModPOIs {
    public static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, UselessMod.MODID);

    public static final DeferredHolder<PoiType, PoiType> TELEPORT_PAD_POI = POI_TYPES.register(
            "teleport_pad",
            () -> new PoiType(
                    ImmutableSet.copyOf(ModBlocks.TELEPORT_BLOCK.get().getStateDefinition().getPossibleStates()),
                    0,
                    1
            )
    );

    public static final DeferredHolder<PoiType, PoiType> TELEPORT_PAD_2_POI = POI_TYPES.register(
            "teleport_pad_2",
            () -> new PoiType(
                    ImmutableSet.copyOf(ModBlocks.TELEPORT_BLOCK_2.get().getStateDefinition().getPossibleStates()),
                    0,
                    1
            )
    );

    public static final DeferredHolder<PoiType, PoiType> TELEPORT_PAD_3_POI = POI_TYPES.register(
            "teleport_pad_3",
            () -> new PoiType(
                    ImmutableSet.copyOf(ModBlocks.TELEPORT_BLOCK_3.get().getStateDefinition().getPossibleStates()),
                    0,
                    1
            )
    );
}
