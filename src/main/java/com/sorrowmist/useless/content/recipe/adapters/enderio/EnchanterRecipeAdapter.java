package com.sorrowmist.useless.content.recipe.adapters.enderio;

import com.sorrowmist.useless.utils.RateLimitedLogger;

import com.enderio.machines.common.recipe.EnchanterRecipe;
import com.enderio.machines.common.init.MachineBlocks;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.Tags;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Ender IO enchanter recipes to the alloy furnace. */
public final class EnchanterRecipeAdapter implements IRecipeAdapter<EnchanterRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Class<EnchanterRecipe> getRecipeClass() {
        return EnchanterRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(MachineBlocks.ENCHANTER.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<EnchanterRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        EnchanterRecipe source = holder.value();
        CountedIngredient catalyst = EnderIOAdapterUtils.counted(source.getInput());
        if (catalyst == null || source.getEnchantment() == null) {
            RateLimitedLogger.warn(LOGGER, "Skipping invalid Ender IO enchanter recipe: {}", holder.id());
            return List.of();
        }
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        for (int enchantmentLevel = 1; enchantmentLevel <= source.getEnchantment().getMaxLevel(); enchantmentLevel++) {
            long catalystCount;
            try { catalystCount = Math.multiplyExact(catalyst.count(), (long)enchantmentLevel); }
            catch (ArithmeticException overflow) { continue; }
            int lapis = source.getLapisForLevel(enchantmentLevel);
            int xpLevels = source.getXPCostForLevel(enchantmentLevel);
            if (lapis <= 0 || xpLevels < 0) continue;
            FluidStack experience = xpLevels == 0 ? FluidStack.EMPTY : experienceFluid(xpLevels);
            if (experience == null || (xpLevels > 0 && experience.isEmpty())) continue;
            ItemStack output = source.getBookForLevel(enchantmentLevel);
            if (output == null || output.isEmpty()) continue;
            var id = AdapterUtils.convertedId(holder.id());
            if (enchantmentLevel > 1) id = new net.minecraft.resources.ResourceLocation(
                    id.getNamespace(), id.getPath() + "_level_" + enchantmentLevel);
            result.add(new AdvancedAlloyFurnaceRecipe(
                    id,
                    List.of(new CountedIngredient(Ingredient.of(Items.WRITABLE_BOOK), 1),
                            new CountedIngredient(catalyst.ingredient(), catalystCount),
                            new CountedIngredient(Ingredient.of(Tags.Items.GEMS_LAPIS), lapis)),
                    experience.isEmpty() ? List.of() : List.of(
                            com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient.of(experience)),
                    List.of(output.copy()), List.of(),
                    0, AdapterUtils.DEFAULT_PROCESS_TIME, Ingredient.EMPTY, 0,
                    AdapterUtils.toMoldIngredient(getMoldItem()), AlloyFurnaceMode.NORMAL));
        }
        return List.copyOf(result);
    }

    /** Uses the installed Ender IO XP-level curve and fluid conversion configuration. */
    FluidStack experienceFluid(int levels) {
        return EnderIOAdapterUtils.experienceFluid(levels);
    }

    @Override
    public List<RecipeHolder<EnchanterRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty() || !matchesMold(mold)) {
            return List.of();
        }
        List<RecipeHolder<EnchanterRecipe>> matches = new ArrayList<>();
        RecipeManager manager = level.getRecipeManager();
        for (RecipeHolder<EnchanterRecipe> holder : com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.getAllRecipesFor(manager,
                com.enderio.machines.common.init.MachineRecipes.ENCHANTING.type().get())) {
            for (var converted : convertAll(holder, level)) {
                if (AdapterUtils.matchesRequired(mergedInputs, EnderIOAdapterUtils.requirements(converted.inputs()))
                        && com.sorrowmist.useless.content.recipe.FluidIngredientAllocator.matchesLong(
                                converted.inputFluids(), mergedFluids, 1L)) {
                    matches.add(holder);
                    break;
                }
            }
        }
        return matches;
    }
}
