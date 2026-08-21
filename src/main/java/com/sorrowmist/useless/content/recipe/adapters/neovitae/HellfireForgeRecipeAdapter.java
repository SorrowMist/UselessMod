package com.sorrowmist.useless.content.recipe.adapters.neovitae;

import appeng.api.stacks.AEKey;
import com.breakinblocks.neovitae.common.block.NVBlocks;
import com.breakinblocks.neovitae.common.datacomponent.NVDataComponents;
import com.breakinblocks.neovitae.common.recipe.NVRecipes;
import com.breakinblocks.neovitae.common.recipe.forge.ForgeInput;
import com.breakinblocks.neovitae.common.recipe.forge.ForgeRecipe;
import com.breakinblocks.neovitae.common.recipe.forge.ForgeSpiritusInfusionRecipe;
import com.breakinblocks.neovitae.common.recipe.forge.ForgeTransformRecipe;
import com.breakinblocks.neovitae.common.recipe.forge.ForgeUpgradeRecipe;
import com.breakinblocks.neovitae.common.tag.NVTags;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts all four Hellfire Forge recipe variants. */
public final class HellfireForgeRecipeAdapter implements IRecipeAdapter<ForgeRecipe> {
    private static final ItemStack MOLD = new ItemStack(NVBlocks.HELLFIRE_FORGE.asItem());

    @Override
    public Class<ForgeRecipe> getRecipeClass() {
        return ForgeRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return MOLD.copy();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<ForgeRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        ForgeRecipe source = holder.value();
        if (source instanceof ForgeUpgradeRecipe upgrade) {
            return staticUpgradeRecipes(holder.id(), upgrade);
        }
        if (source instanceof ForgeSpiritusInfusionRecipe infusion) {
            return staticInfusionRecipes(holder.id(), infusion);
        }
        if (source instanceof ForgeTransformRecipe transform) {
            return staticTransformRecipes(holder.id(), transform);
        }

        ItemStack output = source.getOutput();
        if (output == null || output.isEmpty()) return List.of();
        return List.of(createRecipe(
                holder.id(),
                NeoVitaeAdapterUtils.counted(source.getCraftingIngredients()),
                output,
                NeoVitaeAdapterUtils.energyFor(value(source.getDrain())),
                AdapterUtils.DEFAULT_PROCESS_TIME));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<ForgeRecipe> holder, Level level, List<ItemStack> actualInputs) {
        if (holder == null || holder.value() == null || actualInputs == null) return List.of();
        ForgeRecipe source = holder.value();
        if (source instanceof ForgeUpgradeRecipe upgrade) {
            return runtimeUpgradeRecipes(holder.id(), upgrade, actualInputs, level);
        }
        if (source instanceof ForgeSpiritusInfusionRecipe infusion) {
            return runtimeInfusionRecipes(holder.id(), infusion, actualInputs, level);
        }
        if (source instanceof ForgeTransformRecipe transform) {
            return runtimeTransformRecipes(holder.id(), transform, actualInputs, level);
        }
        return convertAll(holder, level);
    }

    @Override
    public List<RecipeHolder<ForgeRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        return findMatchingRecipes(level, mergedInputs, mergedFluids, Map.of(), mold,
                representativeInputs(mergedInputs));
    }

    @Override
    public List<RecipeHolder<ForgeRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, Map<AEKey, Long> mergedKeys,
            @Nullable ItemStack mold,
            List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) return List.of();

        List<RecipeHolder<ForgeRecipe>> matches = new ArrayList<>();
        RecipeManager manager = level.getRecipeManager();
        for (RecipeHolder<ForgeRecipe> holder : manager.getAllRecipesFor(NVRecipes.HELLFIRE_FORGE_TYPE.get())) {
            ForgeRecipe source = holder.value();
            if (source == null) continue;

            List<ItemStack> inputs = actualInputs == null ? List.of() : actualInputs;
            if (source instanceof ForgeUpgradeRecipe upgrade) {
                if (!upgradeTargets(upgrade, inputs).isEmpty()) matches.add(holder);
                continue;
            }
            if (source instanceof ForgeSpiritusInfusionRecipe infusion) {
                if (!infusionMatches(infusion, inputs)) continue;
                matches.add(holder);
                continue;
            }

            List<CountedIngredient> requirements = NeoVitaeAdapterUtils.counted(
                    source.getCraftingIngredients());
            if (actualInputs != null
                    ? NeoVitaeAdapterUtils.matchesItems(requirements, actualInputs)
                    : NeoVitaeAdapterUtils.matchesItems(mergedInputs, requirements)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    public static boolean isUpgradeTarget(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isDamageableItem()
                && !stack.is(NVTags.Items.BLOOD_MENDING_BLACKLIST)
                && !stack.has(NVDataComponents.BLOOD_MENDING.get());
    }

    public static boolean isInfusionTarget(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.is(NVTags.Items.SPIRITUS_CAPABLE)
                && !stack.has(NVDataComponents.SPIRITUS_MAX.get())
                && !stack.is(ItemTags.SWORDS)
                && !stack.is(ItemTags.AXES)
                && !stack.is(ItemTags.PICKAXES)
                && !stack.is(ItemTags.SHOVELS)
                && !stack.is(ItemTags.HOES);
    }

    static List<ItemStack> upgradeTargets(ForgeUpgradeRecipe source, List<ItemStack> actualInputs) {
        List<ItemStack> result = new ArrayList<>();
        if (source == null || actualInputs == null) return result;
        List<CountedIngredient> catalysts = NeoVitaeAdapterUtils.counted(source.getCraftingIngredients());
        int catalystSlots = source.getCraftingIngredients().size();
        for (int slot = 0; slot < actualInputs.size(); slot++) {
            ItemStack target = actualInputs.get(slot);
            if (!isUpgradeTarget(target)) continue;

            List<ItemStack> remaining = new ArrayList<>();
            for (int index = 0; index < actualInputs.size(); index++) {
                if (index != slot && actualInputs.get(index) != null
                        && !actualInputs.get(index).isEmpty()) {
                    remaining.add(actualInputs.get(index));
                }
            }
            if (remaining.size() == catalystSlots
                    && ItemIngredientAllocator.matches(catalysts, remaining, 1L)
                    && result.stream().noneMatch(existing ->
                    ItemStack.isSameItemSameComponents(existing, target))) {
                result.add(target.copyWithCount(1));
            }
        }
        return result;
    }

    static boolean infusionMatches(ForgeSpiritusInfusionRecipe source, List<ItemStack> actualInputs) {
        if (source == null || actualInputs == null) return false;
        List<ItemStack> gems = NeoVitaeAdapterUtils.distinctMatches(actualInputs, source.getGemInput());
        List<ItemStack> targets = actualInputs.stream()
                .filter(HellfireForgeRecipeAdapter::isInfusionTarget)
                .filter(stack -> gems.stream().noneMatch(gem -> ItemStack.isSameItem(gem, stack)))
                .map(stack -> stack.copyWithCount(1))
                .distinct()
                .toList();
        return !gems.isEmpty() && !targets.isEmpty();
    }

    private static List<AdvancedAlloyFurnaceRecipe> staticUpgradeRecipes(
            ResourceLocation id, ForgeUpgradeRecipe source) {
        List<ItemStack> representatives = NeoVitaeAdapterUtils.representatives(source.getCraftingIngredients());
        if (representatives.isEmpty()) return List.of();

        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack target = item.getDefaultInstance();
            if (!isUpgradeTarget(target)) continue;
            List<ItemStack> sourceInputs = new ArrayList<>(representatives);
            sourceInputs.add(target.copyWithCount(1));
            ItemStack output = assemble(source, sourceInputs, forgeGem(), 4, null);
            if (output.isEmpty()) continue;
            List<CountedIngredient> inputs = NeoVitaeAdapterUtils.append(
                    NeoVitaeAdapterUtils.counted(source.getCraftingIngredients()),
                    NeoVitaeAdapterUtils.exact(target));
            result.add(createRecipe(
                    NeoVitaeAdapterUtils.variantId(id, target),
                    inputs,
                    output,
                    NeoVitaeAdapterUtils.energyFor(value(source.getDrain())),
                    AdapterUtils.DEFAULT_PROCESS_TIME));
        }
        return result;
    }

    private static List<AdvancedAlloyFurnaceRecipe> staticInfusionRecipes(
            ResourceLocation id, ForgeSpiritusInfusionRecipe source) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        for (ItemStack gem : NeoVitaeAdapterUtils.candidates(source.getGemInput())) {
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack target = item.getDefaultInstance();
                if (!isInfusionTarget(target)) continue;

                ItemStack output = assemble(source, List.of(gem, target), gem, 0, null);
                if (output.isEmpty()) continue;
                List<CountedIngredient> inputs = NeoVitaeAdapterUtils.append(
                        List.of(new CountedIngredient(NeoVitaeAdapterUtils.exact(gem), 1L)),
                        NeoVitaeAdapterUtils.exact(target));
                result.add(createRecipe(
                        NeoVitaeAdapterUtils.variantId(id, gem, target),
                        inputs,
                        output,
                        NeoVitaeAdapterUtils.energyFor(value(source.getDrain())),
                        AdapterUtils.DEFAULT_PROCESS_TIME));
            }
        }
        return result;
    }

    private static List<AdvancedAlloyFurnaceRecipe> staticTransformRecipes(
            ResourceLocation id, ForgeTransformRecipe source) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        List<ItemStack> catalysts = NeoVitaeAdapterUtils.representatives(source.getCatalysts());
        if (catalysts.size() != source.getCatalysts().size()) return List.of();
        for (ItemStack target : NeoVitaeAdapterUtils.candidates(source.getTransformInput())) {
            List<ItemStack> sourceInputs = new ArrayList<>(catalysts);
            sourceInputs.add(target);
            ItemStack output = assemble(source, sourceInputs, forgeGem(), 4, null);
            if (output.isEmpty()) continue;

            result.add(createRecipe(
                    NeoVitaeAdapterUtils.variantId(id, target),
                    NeoVitaeAdapterUtils.append(
                            NeoVitaeAdapterUtils.counted(source.getCatalysts()),
                            NeoVitaeAdapterUtils.exact(target)),
                    output,
                    NeoVitaeAdapterUtils.energyFor(value(source.getDrain())),
                    AdapterUtils.DEFAULT_PROCESS_TIME));
        }
        return result;
    }

    private static List<AdvancedAlloyFurnaceRecipe> runtimeUpgradeRecipes(
            ResourceLocation id, ForgeUpgradeRecipe source, List<ItemStack> actualInputs,
            @Nullable Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        for (ItemStack target : upgradeTargets(source, actualInputs)) {
            ItemStack output = assemble(source, actualInputs, forgeGem(), 4, level);
            if (output.isEmpty()) continue;
            List<CountedIngredient> inputs = NeoVitaeAdapterUtils.append(
                    NeoVitaeAdapterUtils.counted(source.getCraftingIngredients()),
                    NeoVitaeAdapterUtils.exact(target));
            result.add(createRecipe(
                    NeoVitaeAdapterUtils.variantId(id, target),
                    inputs,
                    output,
                    NeoVitaeAdapterUtils.energyFor(value(source.getDrain())),
                    AdapterUtils.DEFAULT_PROCESS_TIME));
        }
        return result;
    }

    private static List<AdvancedAlloyFurnaceRecipe> runtimeInfusionRecipes(
            ResourceLocation id, ForgeSpiritusInfusionRecipe source, List<ItemStack> actualInputs,
            @Nullable Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        List<ItemStack> gems = NeoVitaeAdapterUtils.distinctMatches(actualInputs, source.getGemInput());
        for (ItemStack gem : gems) {
            for (ItemStack target : actualInputs) {
                if (!isInfusionTarget(target) || ItemStack.isSameItem(gem, target)) continue;
                ItemStack output = assemble(source, actualInputs, gem,
                        actualInputs.indexOf(gem), level);
                if (output.isEmpty()) continue;
                List<CountedIngredient> inputs = NeoVitaeAdapterUtils.append(
                        List.of(new CountedIngredient(NeoVitaeAdapterUtils.exact(gem), 1L)),
                        NeoVitaeAdapterUtils.exact(target));
                result.add(createRecipe(
                        NeoVitaeAdapterUtils.variantId(id, gem, target),
                        inputs,
                        output,
                        NeoVitaeAdapterUtils.energyFor(value(source.getDrain())),
                        AdapterUtils.DEFAULT_PROCESS_TIME));
            }
        }
        return result;
    }

    private static List<AdvancedAlloyFurnaceRecipe> runtimeTransformRecipes(
            ResourceLocation id, ForgeTransformRecipe source, List<ItemStack> actualInputs,
            @Nullable Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        for (ItemStack target : NeoVitaeAdapterUtils.distinctMatches(actualInputs, source.getTransformInput())) {
            ItemStack output = assemble(source, actualInputs, forgeGem(), 4, level);
            if (output.isEmpty()) continue;
            List<CountedIngredient> inputs = transformInputs(source, target);
            result.add(createRecipe(
                    NeoVitaeAdapterUtils.variantId(id, target),
                    inputs,
                    output,
                    NeoVitaeAdapterUtils.energyFor(value(source.getDrain())),
                    AdapterUtils.DEFAULT_PROCESS_TIME));
        }
        return result;
    }

    private static List<CountedIngredient> transformInputs(
            ForgeTransformRecipe source, ItemStack target) {
        List<Ingredient> inputs = new ArrayList<>(source.getCraftingIngredients());
        for (int index = inputs.size() - 1; index >= 0; index--) {
            if (inputs.get(index).equals(source.getTransformInput())) {
                inputs.set(index, NeoVitaeAdapterUtils.exact(target));
                return NeoVitaeAdapterUtils.counted(inputs);
            }
        }
        return NeoVitaeAdapterUtils.counted(inputs);
    }

    private static AdvancedAlloyFurnaceRecipe createRecipe(
            ResourceLocation id, List<CountedIngredient> inputs, ItemStack output,
            long energy, int processTime) {
        return NeoVitaeAdapterUtils.recipe(
                id,
                inputs,
                List.of(),
                List.of(output),
                List.of(),
                energy,
                processTime,
                List.of(Ingredient.of(MOLD)));
    }

    private static ItemStack assemble(ForgeRecipe source, List<ItemStack> inputs,
                                      ItemStack gem, int gemIndex, @Nullable Level level) {
        try {
            return source.assemble(new ForgeInput(inputs, gem, gemIndex),
                    level == null ? null : level.registryAccess()).copy();
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack forgeGem() {
        ItemStack gem = new ItemStack(Items.PAPER);
        gem.set(NVDataComponents.SPIRITUS_AMOUNT.get(), Double.MAX_VALUE);
        return gem;
    }

    private static List<ItemStack> representativeInputs(Map<Ingredient, Long> mergedInputs) {
        List<ItemStack> result = new ArrayList<>();
        if (mergedInputs == null) return result;
        for (Ingredient ingredient : mergedInputs.keySet()) {
            ItemStack representative = NeoVitaeAdapterUtils.representative(ingredient);
            if (!representative.isEmpty()) result.add(representative);
        }
        return result;
    }

    private static double value(@Nullable Double value) {
        return value == null ? 0.0 : value;
    }
}
