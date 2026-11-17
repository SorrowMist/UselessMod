package com.sorrowmist.useless.content.recipe.adapters.anvilcraft;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import dev.dubhe.anvilcraft.recipe.JewelCraftingRecipe;
import dev.dubhe.anvilcraft.recipe.generate.JewelCraftingRecipeGeneratingCache;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 转换以成品作为可重复使用样本的珠宝加工配方。 */
public final class JewelCraftingRecipeAdapter implements IRecipeAdapter<JewelCraftingRecipe> {
    @Override
    public String sourceId() { return RecipeSourceIds.ANVILCRAFT; }

    @Override
    public Class<JewelCraftingRecipe> getRecipeClass() { return JewelCraftingRecipe.class; }

    @Override
    @Nullable
    public ItemStack getMoldItem() { return null; }

    @Override
    public List<RecipeHolder<JewelCraftingRecipe>> getGeneratedRecipes(Level level) {
        return level == null ? List.of()
                : new JewelCraftingRecipeGeneratingCache(level.registryAccess()).buildRecipes().orElse(List.of());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<JewelCraftingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null || level == null) return List.of();
        JewelCraftingRecipe source = holder.value();
        ItemStack result = source.getResult().copy();
        Ingredient table = Multiblock4DRecipeAdapter.mold("jewelcrafting_table");
        if (result.isEmpty() || table.isEmpty()) return List.of();
        Map<Ingredient, Long> materials = new LinkedHashMap<>();
        for (Ingredient ingredient : source.getIngredients()) {
            if (ingredient.isEmpty()) return List.of();
            AdapterUtils.mergeIngredient(materials, ingredient, 1);
        }
        if (materials.isEmpty()) return List.of();
        if (source.isHasVanishingCurse()) {
            ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            enchantments.set(level.registryAccess().holderOrThrow(Enchantments.VANISHING_CURSE), 1);
            result.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
        }
        return List.of(new AdvancedAlloyFurnaceRecipe(AdapterUtils.convertedId(holder.id()),
                materials.entrySet().stream().map(e -> new CountedIngredient(e.getKey(), e.getValue())).toList(),
                List.of(), List.of(), List.of(result), List.of(), List.of(),
                AdapterUtils.energyForProcessTime(AdapterUtils.DEFAULT_PROCESS_TIME),
                AdapterUtils.DEFAULT_PROCESS_TIME, Ingredient.EMPTY, 0,
                List.of(table, Ingredient.of(source.getResult().getItem())), AlloyFurnaceMode.NORMAL));
    }
}
