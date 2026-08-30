package com.sorrowmist.useless.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ConfigManagerTest {

    @Test
    void uselessDimensionBlacklistUsesAnEmptyServerListByDefault() {
        Object valueSpec = ConfigManager.SERVER_SPEC.getSpec()
                .get(List.of("useless_dimension", "floor_block_blacklist"));

        ModConfigSpec.ListValueSpec listSpec =
                assertInstanceOf(ModConfigSpec.ListValueSpec.class, valueSpec);
        assertEquals(List.of(), listSpec.getDefault());
        assertEquals("", listSpec.getNewElementSupplier().get());
        assertEquals(List.of(), ConfigManager.getUselessDimensionFloorBlockBlacklist());

        Object whitelistValueSpec = ConfigManager.SERVER_SPEC.getSpec()
                .get(List.of("useless_dimension", "floor_block_whitelist"));
        ModConfigSpec.ListValueSpec whitelistSpec =
                assertInstanceOf(ModConfigSpec.ListValueSpec.class, whitelistValueSpec);
        assertEquals(List.of(), whitelistSpec.getDefault());
        assertEquals("", whitelistSpec.getNewElementSupplier().get());
        assertEquals(List.of(), ConfigManager.getUselessDimensionFloorBlockWhitelist());
    }
}
