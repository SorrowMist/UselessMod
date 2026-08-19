package com.sorrowmist.useless.content.recipe;

import com.sorrowmist.useless.core.config.ConfigManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.crafting.CompoundFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies the configurable cross-mod fluid aliases to adapted recipe fluid inputs.
 *
 * <p>The config maps a source fluid/tag to extra fluids that should also be accepted, which is
 * used to bridge mods that produce the same logical fluid under different ids (for example crude
 * oil from Modern Industrialization being accepted for Oritech's {@code c:oil} recipes).</p>
 */
public final class FluidAliasCompat {

    private FluidAliasCompat() {
    }

    /** Returns the base ingredient, extended with any configured aliases for {@code sourceKey}. */
    public static FluidIngredient applyAliases(FluidIngredient base, String sourceKey) {
        List<FluidIngredient> aliases = resolveAliases(sourceKey, ConfigManager.getFluidInputAliases());
        if (aliases.isEmpty()) {
            return base;
        }
        List<FluidIngredient> all = new ArrayList<>(aliases.size() + 1);
        all.add(base);
        all.addAll(aliases);
        return CompoundFluidIngredient.of(all);
    }

    /**
     * Parses the config string ({@code source=alias1,alias2;source2=alias3}) and returns the
     * NeoForge fluid ingredients for the aliases matching {@code sourceKey}.
     */
    static List<FluidIngredient> resolveAliases(String sourceKey, String config) {
        if (sourceKey == null || sourceKey.isBlank() || config == null || config.isBlank()) {
            return List.of();
        }
        List<FluidIngredient> result = new ArrayList<>();
        for (String entry : config.split(";")) {
            if (entry.isBlank()) {
                continue;
            }
            int equals = entry.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = entry.substring(0, equals).trim();
            if (!key.equals(sourceKey)) {
                continue;
            }
            for (String alias : entry.substring(equals + 1).split(",")) {
                FluidIngredient ingredient = parseAlias(alias.trim());
                if (ingredient != null) {
                    result.add(ingredient);
                }
            }
        }
        return result;
    }

    private static FluidIngredient parseAlias(String alias) {
        if (alias.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(alias.substring(1));
            if (tagId == null) {
                return null;
            }
            return FluidIngredient.tag(TagKey.create(Registries.FLUID, tagId));
        }
        ResourceLocation fluidId = ResourceLocation.tryParse(alias);
        if (fluidId == null) {
            return null;
        }
        Fluid fluid = BuiltInRegistries.FLUID.get(fluidId);
        if (fluid == null || fluid == Fluids.EMPTY) {
            return null;
        }
        return FluidIngredient.single(fluid);
    }
}
