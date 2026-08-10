package com.sorrowmist.useless.integration.dataenergistics.provider;

import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.provider.PatternProviderRegistry;
import com.fish_dan_.data_energistics.api.registry.provider.callback.PatternProviderPostCommitContext;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderMetadata;
import com.fish_dan_.data_energistics.api.registry.provider.definition.PatternProviderRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.definition.ProviderIdentityDescriptor;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderFactoryContext;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.init.ModBlockEntities;
import com.sorrowmist.useless.init.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Registers UselessMod alloy-furnace providers with Data Energistics during its common setup scan. */
@DataEnergisticsEntrypoint
public final class UselessDataEnergisticsPatternProviderPlugin implements DataEnergisticsPlugin {
    private static final ResourceLocation RECIPE_CATEGORY_ID = UselessMod.id("advanced_alloy_furnace");
    private static final ResourceLocation ADVANCED_ALLOY_FURNACE_REGISTRATION_ID =
            UselessMod.id("advanced_alloy_furnace");
    private static final ResourceLocation ME_PATTERN_ASSEMBLY_REGISTRATION_ID =
            UselessMod.id("me_pattern_assembly");

    /** Public constructor required by the Data Energistics entrypoint scanner. */
    public UselessDataEnergisticsPatternProviderPlugin() {}

    @Override
    public void register(@NotNull DataEnergisticsRegistry registry) {
        ResourceLocation advancedAlloyFurnaceTypeId = ModBlockEntities.ADVANCED_ALLOY_FURNACE.getId();
        ResourceLocation advancedAlloyFurnaceItemId = ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.getId();
        ResourceLocation mePatternAssemblyTypeId = ModBlockEntities.ME_PATTERN_ASSEMBLY.getId();
        ResourceLocation mePatternAssemblyItemId = ModBlocks.ME_PATTERN_ASSEMBLY.getId();
        registerAll(registry.patternProviders(), createRegistrations(
                advancedAlloyFurnaceTypeId,
                advancedAlloyFurnaceItemId,
                mePatternAssemblyTypeId,
                mePatternAssemblyItemId));
    }

    /** Creates the two immutable provider declarations used by this integration. */
    static @NotNull List<@NotNull PatternProviderRegistration> createRegistrations(
            @NotNull ResourceLocation advancedAlloyFurnaceTypeId,
            @NotNull ResourceLocation advancedAlloyFurnaceItemId,
            @NotNull ResourceLocation mePatternAssemblyTypeId,
            @NotNull ResourceLocation mePatternAssemblyItemId) {
        return List.of(
                new PatternProviderRegistration(
                        metadata(
                                ADVANCED_ALLOY_FURNACE_REGISTRATION_ID,
                                advancedAlloyFurnaceTypeId,
                                advancedAlloyFurnaceItemId),
                        UselessDataEnergisticsPatternProviderPlugin::createAdvancedAlloyFurnaceAdapter,
                        null,
                        UselessDataEnergisticsPatternProviderPlugin::markAdvancedAlloyFurnaceChanged),
                new PatternProviderRegistration(
                        metadata(
                                ME_PATTERN_ASSEMBLY_REGISTRATION_ID,
                                mePatternAssemblyTypeId,
                                mePatternAssemblyItemId),
                        UselessDataEnergisticsPatternProviderPlugin::createMePatternAssemblyAdapter,
                        null,
                        UselessDataEnergisticsPatternProviderPlugin::markMePatternAssemblyChanged));
    }

    /** Registers every declaration as a separate atomic Data Energistics provider registration. */
    static void registerAll(
            @NotNull PatternProviderRegistry registry,
            @NotNull List<@NotNull PatternProviderRegistration> registrations) {
        for (PatternProviderRegistration registration : registrations) {
            registry.register(registration);
        }
    }

    private static @NotNull PatternProviderMetadata metadata(
            @NotNull ResourceLocation registrationId,
            @NotNull ResourceLocation blockEntityTypeId,
            @NotNull ResourceLocation workstationItemId) {
        return new PatternProviderMetadata(
                registrationId,
                new ProviderIdentityDescriptor.Block(blockEntityTypeId),
                List.of(RECIPE_CATEGORY_ID),
                List.of(workstationItemId));
    }

    private static @NotNull CountedCraftingProviderAdapter createAdvancedAlloyFurnaceAdapter(
            @NotNull PatternProviderFactoryContext context) {
        if (!(context.provider() instanceof AdvancedAlloyFurnaceBlockEntity provider)) {
            throw new IllegalArgumentException("Advanced alloy furnace registration received an unexpected provider");
        }
        return AlloyFurnaceCountedCraftingAdapter.forAdvancedAlloyFurnace(provider, context.identity());
    }

    private static @NotNull CountedCraftingProviderAdapter createMePatternAssemblyAdapter(
            @NotNull PatternProviderFactoryContext context) {
        if (!(context.provider() instanceof MePatternAssemblyBlockEntity provider)) {
            throw new IllegalArgumentException("ME pattern assembly registration received an unexpected provider");
        }
        return AlloyFurnaceCountedCraftingAdapter.forMePatternAssembly(provider, context.identity());
    }

    private static void markAdvancedAlloyFurnaceChanged(@NotNull PatternProviderPostCommitContext context) {
        if (!(context.provider() instanceof AdvancedAlloyFurnaceBlockEntity provider)) {
            throw new IllegalArgumentException("Advanced alloy furnace commit received an unexpected provider");
        }
        provider.setChanged();
    }

    private static void markMePatternAssemblyChanged(@NotNull PatternProviderPostCommitContext context) {
        if (!(context.provider() instanceof MePatternAssemblyBlockEntity provider)) {
            throw new IllegalArgumentException("ME pattern assembly commit received an unexpected provider");
        }
        provider.setChanged();
    }
}
