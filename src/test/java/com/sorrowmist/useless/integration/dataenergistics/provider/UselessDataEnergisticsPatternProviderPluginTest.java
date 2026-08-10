package com.sorrowmist.useless.integration.dataenergistics.provider;

import com.fish_dan_.data_energistics.api.registry.provider.PatternProviderRegistry;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.definition.ProviderIdentityDescriptor;
import com.fish_dan_.data_energistics.api.registry.search.TrinityPatternSearchRegistry;
import com.fish_dan_.data_energistics.api.registry.search.TrinityPatternSearchTermRegistration;
import com.sorrowmist.useless.UselessMod;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UselessDataEnergisticsPatternProviderPluginTest {
    @Test
    void createsAndRegistersBothAlloyFurnaceProviders() {
        ResourceLocation advancedAlloyFurnaceType = UselessMod.id("test_advanced_alloy_furnace_type");
        ResourceLocation advancedAlloyFurnaceItem = UselessMod.id("test_advanced_alloy_furnace_item");
        ResourceLocation mePatternAssemblyType = UselessMod.id("test_me_pattern_assembly_type");
        ResourceLocation mePatternAssemblyItem = UselessMod.id("test_me_pattern_assembly_item");

        List<PatternProviderRegistration> registrations =
                UselessDataEnergisticsPatternProviderPlugin.createRegistrations(
                        advancedAlloyFurnaceType,
                        advancedAlloyFurnaceItem,
                        mePatternAssemblyType,
                        mePatternAssemblyItem);
        RecordingPatternProviderRegistry registry = new RecordingPatternProviderRegistry();
        UselessDataEnergisticsPatternProviderPlugin.registerAll(registry, registrations);

        assertEquals(2, registry.registrations.size());
        PatternProviderRegistration advanced = registry.registrations.getFirst();
        assertEquals(UselessMod.id("advanced_alloy_furnace"), advanced.metadata().registrationId());
        assertEquals(new ProviderIdentityDescriptor.Block(advancedAlloyFurnaceType),
                advanced.metadata().providerIdentity());
        assertEquals(List.of(UselessMod.id("advanced_alloy_furnace")),
                advanced.metadata().recipeCategoryIds());
        assertEquals(List.of(advancedAlloyFurnaceItem), advanced.metadata().workstationItemIds());
        assertNotNull(advanced.factory());
        assertNotNull(advanced.postCommitHook());

        PatternProviderRegistration assembly = registry.registrations.get(1);
        assertEquals(UselessMod.id("me_pattern_assembly"), assembly.metadata().registrationId());
        assertEquals(new ProviderIdentityDescriptor.Block(mePatternAssemblyType),
                assembly.metadata().providerIdentity());
        assertEquals(List.of(UselessMod.id("advanced_alloy_furnace")),
                assembly.metadata().recipeCategoryIds());
        assertEquals(List.of(mePatternAssemblyItem), assembly.metadata().workstationItemIds());
        assertNotNull(assembly.factory());
        assertNotNull(assembly.postCommitHook());
    }

    @Test
    void registersTheOmniversalMoldAsASeparateTrinitySearchContributor() {
        RecordingTrinityPatternSearchRegistry registry = new RecordingTrinityPatternSearchRegistry();

        UselessDataEnergisticsPatternProviderPlugin.registerSearchTerms(registry);

        assertEquals(1, registry.registrations.size());
        TrinityPatternSearchTermRegistration registration = registry.registrations.getFirst();
        assertEquals(UselessMod.id("omniversal_pattern_mold_search"), registration.registrationId());
        assertNotNull(registration.contributor());
    }

    private static final class RecordingPatternProviderRegistry implements PatternProviderRegistry {
        private final List<PatternProviderRegistration> registrations = new ArrayList<>();

        @Override
        public void register(@NotNull PatternProviderRegistration registration) {
            registrations.add(registration);
        }
    }

    private static final class RecordingTrinityPatternSearchRegistry implements TrinityPatternSearchRegistry {
        private final List<TrinityPatternSearchTermRegistration> registrations = new ArrayList<>();

        @Override
        public void register(@NotNull TrinityPatternSearchTermRegistration registration) {
            registrations.add(registration);
        }
    }
}
