package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.recipe.adapters.enderio.SoulVialSetIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** NeoForge custom ingredient types used by generated compatibility recipes. */
public final class ModIngredientTypes {
    public static final DeferredRegister<IngredientType<?>> INGREDIENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, UselessMod.MODID);

    public static final DeferredHolder<IngredientType<?>, IngredientType<SoulVialSetIngredient>>
            SOUL_VIAL_SET = INGREDIENT_TYPES.register("soul_vial_set", () -> SoulVialSetIngredient.TYPE);

    private ModIngredientTypes() {
    }
}
