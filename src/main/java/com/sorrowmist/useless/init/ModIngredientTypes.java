package com.sorrowmist.useless.init;

import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.apotheosis.ApotheosisGemIngredient;
import com.sorrowmist.useless.content.recipe.adapters.enderio.SoulVialSetIngredient;
import com.sorrowmist.useless.content.recipe.adapters.hostilenetworks.DataModelRangeIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fml.ModList;

/** Register native Forge ingredient serializers during common setup, before datapack loading. */
public final class ModIngredientTypes {
    private ModIngredientTypes() {}
    public static void register() {
        CraftingHelper.register(new ResourceLocation("useless_mod:top_level_nbt"),com.sorrowmist.useless.compat.forge1201.recipe.TopLevelNbtIngredient.TYPE);
        CraftingHelper.register(new ResourceLocation("useless_mod:soul_vial_set"),SoulVialSetIngredient.TYPE);
        if(ModList.get().isLoaded("enderio"))
            CraftingHelper.register(new ResourceLocation("useless_mod:filled_soul"),com.sorrowmist.useless.compat.forge1201.enderio.FilledSoulIngredient.TYPE);
        if(ModList.get().isLoaded(RecipeSourceIds.HOSTILE_NETWORKS))
            CraftingHelper.register(new ResourceLocation("useless_mod:data_model_range"),DataModelRangeIngredient.TYPE);
        if(ModList.get().isLoaded(RecipeSourceIds.APOTHEOSIS))
            CraftingHelper.register(new ResourceLocation("useless_mod:apotheosis_gem"),ApotheosisGemIngredient.TYPE);
    }
}
