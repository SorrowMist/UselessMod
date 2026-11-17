package com.sorrowmist.useless.content.recipe.adapters.anvilcraft;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import dev.anvilcraft.lib.v2.multiblock.dynamic.definition.DefinitionSerialization;
import dev.dubhe.anvilcraft.recipe.multiblock.MultiblockRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将多方块合成转换为对应的材料与模具要求。 */
public final class MultiblockRecipeAdapter implements IRecipeAdapter<MultiblockRecipe> {
    @Override
    public String sourceId() {
        return RecipeSourceIds.ANVILCRAFT;
    }

    @Override
    public Class<MultiblockRecipe> getRecipeClass() {
        return MultiblockRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<MultiblockRecipe> holder, Level level) {
        if (holder == null || holder.value() == null || holder.value().getResult().isEmpty()) return List.of();
        DefinitionSerialization serialization = DefinitionSerialization.fromDefinition(holder.value().getPattern());
        Map<Ingredient, Long> materials = new LinkedHashMap<>();
        for (String[] layer : serialization.grid()) {
            for (String row : layer) {
                for (int i = 0; i < row.length(); i++) {
                    char symbol = row.charAt(i);
                    if (symbol == ' ') continue;
                    if (!Multiblock4DRecipeAdapter.addBlockMaterial(materials, serialization.mapping().get(symbol), level)) {
                        return List.of();
                    }
                }
            }
        }
        List<Ingredient> molds = List.of(Multiblock4DRecipeAdapter.mold("giant_anvil"),
                Multiblock4DRecipeAdapter.mold("transparent_crafting_table"),
                Multiblock4DRecipeAdapter.mold("space_overcompressor"));
        if (materials.isEmpty() || molds.stream().anyMatch(Ingredient::isEmpty)) return List.of();
        List<CountedIngredient> inputs = materials.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue())).toList();
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()), inputs, List.of(), List.of(),
                List.of(holder.value().getResult().copy()), List.of(), List.of(),
                AdapterUtils.energyForProcessTime(AdapterUtils.DEFAULT_PROCESS_TIME),
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY, 0, molds, AlloyFurnaceMode.NORMAL));
    }
}
