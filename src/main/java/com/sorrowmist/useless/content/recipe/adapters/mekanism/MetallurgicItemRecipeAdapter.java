package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.BatchRecipeAdapter;
import mekanism.api.recipes.ItemStackToInfuseTypeRecipe;
import mekanism.api.recipes.MetallurgicInfuserRecipe;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/** Optional legacy shortcut; chemical-key processing remains the default. */
public final class MetallurgicItemRecipeAdapter extends BatchRecipeAdapter<MetallurgicInfuserRecipe> {
    public MetallurgicItemRecipeAdapter() {
        super(MetallurgicInfuserRecipe.class, RecipeSourceIds.LEGACY_METALLURGIC,
                "metallurgic_infuser", "mek_metallurgic_", AlloyFurnaceMode.NORMAL);
    }

    @Override public ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.METALLURGIC_INFUSER.getBlock());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<MetallurgicInfuserRecipe> holder, Level level) {
        if (holder == null || level == null) return List.of();
        var source = holder.value();
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        for (var chemical : source.getChemicalInput().getRepresentations()) {
            for (var candidate : level.getRecipeManager().getRecipes()) {
                if (!(candidate instanceof ItemStackToInfuseTypeRecipe conversion)) continue;
                for (var produced : conversion.getOutputDefinition()) {
                    if (produced.getType() != chemical.getType() || produced.getAmount() <= 0L) continue;
                    long needed = Math.multiplyExact(chemical.getAmount(), 64L);
                    long batches = needed / produced.getAmount() + (needed % produced.getAmount() == 0L ? 0L : 1L);
                    var materials = NativeMekanismIngredients.itemVariants(conversion.getInput().serialize(), batches);
                    var rawInputs = NativeMekanismIngredients.itemVariants(source.getItemInput().serialize(), 64L);
                    for (int itemIndex = 0; itemIndex < rawInputs.size(); itemIndex++) {
                        for (int materialIndex = 0; materialIndex < materials.size(); materialIndex++) {
                            var inputs = List.of(rawInputs.get(itemIndex), materials.get(materialIndex));
                            var conversionId = conversion.getId();
                            var chemicalId = chemical.getTypeRegistryName();
                            ResourceLocation id = new ResourceLocation(holder.id().getNamespace(), holder.id().getPath()
                                    + "/" + chemicalId.getNamespace() + "/" + chemicalId.getPath()
                                    + "/" + conversionId.getNamespace() + "/" + conversionId.getPath()
                                    + "/" + itemIndex + "_" + materialIndex);
                            result.add(recipe(id, inputs, List.of(), source.getOutputDefinition().stream()
                                    .map(output -> scale(output, 64L)).toList(), List.of(), 10000L));
                        }
                    }
                }
            }
        }
        return List.copyOf(result);
    }
}
