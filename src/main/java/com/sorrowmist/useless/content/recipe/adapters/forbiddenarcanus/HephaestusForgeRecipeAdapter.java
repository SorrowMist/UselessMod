package com.sorrowmist.useless.content.recipe.adapters.forbiddenarcanus;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import com.sorrowmist.useless.utils.RateLimitedLogger;
import com.stal111.forbidden_arcanus.common.block.HephaestusForgeBlock;
import com.stal111.forbidden_arcanus.common.block.entity.forge.HephaestusForgeLevel;
import com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssencesDefinition;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.Ritual;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.RitualInput;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.RitualManager;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.RitualRequirements;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.CreateItemResult;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.UpgradeTierResult;
import com.stal111.forbidden_arcanus.common.item.BlacksmithGavelItem;
import com.stal111.forbidden_arcanus.core.registry.FARegistries;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Strict adapter for the native Forbidden & Arcanus 1.20.1 / 2.2.6 ritual registry. */
public final class HephaestusForgeRecipeAdapter implements IRecipeAdapter<ForbiddenArcanusSyntheticRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation FORGE_ID = new ResourceLocation("forbidden_arcanus", "hephaestus_forge");
    private static final int MAX_RITUAL_INPUTS = 8;

    @Override
    public Class<ForbiddenArcanusSyntheticRecipe> getRecipeClass() {
        return ForbiddenArcanusSyntheticRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        // Resolve only after registries have loaded; there are no separate tier-N forge items.
        return BuiltInRegistries.ITEM.getOptional(FORGE_ID).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        ItemStack forge = getMoldItem();
        return !forge.isEmpty() && mold != null && !mold.isEmpty()
                && ItemStack.isSameItem(forge, mold) && validTier(HephaestusForgeBlock.getTier(mold));
    }

    @Override
    public List<RecipeHolder<ForbiddenArcanusSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) return List.of();
        List<RecipeHolder<ForbiddenArcanusSyntheticRecipe>> result = new ArrayList<>();
        for (var entry : rituals(level).entrySet()) {
            // Catalogue variants have EXACT fresh gavel inputs and exact native tier molds. They
            // are executable recipes, not approximations of arbitrary tool NBT or result actions.
            for (var converted : convertRitual(entry.getKey().location(), entry.getValue())) {
                result.add(wrap(converted));
            }
        }
        return List.copyOf(result);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<ForbiddenArcanusSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<RecipeHolder<ForbiddenArcanusSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        // Display ingredients cannot prove a gavel's real RemainingRitualUses/NBT. The manager
        // invokes the concrete-input overload below; do not invent a tool for legacy callers.
        return List.of();
    }

    @Override
    public List<RecipeHolder<ForbiddenArcanusSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) return List.of();
        List<ItemStack> supplies = actualInputs == null ? List.of() : actualInputs;
        List<ItemStack> tools = new ArrayList<>(supplies);
        List<GenericStack> keys = new ArrayList<>();
        if (mergedKeys != null) {
            for (var entry : mergedKeys.entrySet()) {
                if (entry.getKey() instanceof AEItemKey itemKey && entry.getValue() != null && entry.getValue() > 0L) {
                    // AEItemKey is a concrete, NBT-bearing supply, unlike Ingredient#getItems().
                    tools.add(itemKey.toStack(1));
                    keys.add(new GenericStack(itemKey, entry.getValue()));
                }
            }
        }

        List<RecipeHolder<ForbiddenArcanusSyntheticRecipe>> matches = new ArrayList<>();
        for (var entry : rituals(level).entrySet()) {
            for (var converted : convertRitual(entry.getKey().location(), entry.getValue(), tools, mold)) {
                if (ItemIngredientAllocator.matches(converted.inputs(), supplies, keys, 1L)) {
                    matches.add(wrap(converted));
                }
            }
        }
        // IDs include the complete tool and mold NBT: the manager's conversion cache must never
        // reuse the returned gavel from a different remaining-use count or enchantment/owner tag.
        return List.copyOf(matches);
    }

    private static Registry<Ritual> rituals(Level level) {
        return level.registryAccess().registryOrThrow(FARegistries.RITUAL);
    }

    private static RecipeHolder<ForbiddenArcanusSyntheticRecipe> wrap(AdvancedAlloyFurnaceRecipe recipe) {
        return new RecipeHolder<>(recipe.id(), new ForbiddenArcanusSyntheticRecipe(recipe));
    }

    static List<AdvancedAlloyFurnaceRecipe> convertRitual(ResourceLocation sourceId, Ritual ritual) {
        List<ItemStack> freshGavels = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item.getClass() == BlacksmithGavelItem.class) freshGavels.add(item.getDefaultInstance());
        }
        return convertRitual(sourceId, ritual, freshGavels, null);
    }

    /** A null mold enumerates canonical native tier molds; tools must always be concrete stacks. */
    static List<AdvancedAlloyFurnaceRecipe> convertRitual(
            ResourceLocation sourceId, Ritual ritual, List<ItemStack> tools, @Nullable ItemStack mold) {
        if (sourceId == null || ritual == null) return List.of();
        if (ritual.result() instanceof UpgradeTierResult) {
            warn(sourceId, "upgrades a placed forge with an exact required tier; not an item output");
            return List.of();
        }
        // Exact class, not instanceof: addons may override apply/checkConditions even when the
        // display result is a constant. No Transmute/display-stack fallback exists on this path.
        if (ritual.result() == null || ritual.result().getClass() != CreateItemResult.class) {
            warn(sourceId, "has an unknown or dynamic result action");
            return List.of();
        }
        if (ritual.mainIngredient() == null || ritual.mainIngredient().isEmpty() || ritual.inputs() == null) {
            warn(sourceId, "has an invalid main ingredient or pedestal list");
            return List.of();
        }
        RitualRequirements requirements = ritual.requirements();
        int requiredTier = requirements == null ? 1 : requirements.tier();
        if (!validTier(requiredTier) || (requirements != null &&
                (requirements.enhancers() == null || !requirements.enhancers().isEmpty()))) {
            warn(sourceId, "has unsupported tier/enhancer requirements; installed enhancer effects cannot be dropped");
            return List.of();
        }
        EssencesDefinition essences = ritual.essences();
        if (!validEssences(essences)) {
            warn(sourceId, "has missing or negative essence costs");
            return List.of();
        }
        ItemStack output = ((CreateItemResult) ritual.result()).getResult();
        if (output == null || output.isEmpty() || output.getCount() <= 0) {
            warn(sourceId, "has an empty fixed item result");
            return List.of();
        }

        List<CountedIngredient> inputs = new ArrayList<>();
        // Retain the native predicate itself. Enumerating display stacks would broaden custom
        // NBT predicates and unnecessarily freeze tag membership, even for a fixed result.
        inputs.add(new CountedIngredient(ritual.mainIngredient(), 1L));
        long pedestalCount = 0L;
        for (RitualInput input : ritual.inputs()) {
            if (input == null || input.ingredient() == null || input.ingredient().isEmpty() || input.amount() <= 0) {
                warn(sourceId, "has an invalid pedestal input");
                return List.of();
            }
            pedestalCount += input.amount();
            if (pedestalCount > MAX_RITUAL_INPUTS) {
                warn(sourceId, "needs more than eight pedestals");
                return List.of();
            }
            inputs.add(new CountedIngredient(input.ingredient(), input.amount()));
        }

        ItemStack forge = new HephaestusForgeRecipeAdapter().getMoldItem();
        if (forge.isEmpty()) {
            warn(sourceId, "has no registered hephaestus_forge mold");
            return List.of();
        }
        List<ItemStack> molds = new ArrayList<>();
        if (mold == null) {
            for (int tier = requiredTier; tier <= 5; tier++) {
                molds.add(HephaestusForgeBlock.setTierOnStack(forge.copy(), tier));
            }
        } else if (!mold.isEmpty() && ItemStack.isSameItem(forge, mold)) {
            molds.add(mold.copyWithCount(1));
        }

        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        List<ItemStack> seenTools = new ArrayList<>();
        for (ItemStack tool : tools == null ? List.<ItemStack>of() : tools) {
            if (tool == null || tool.isEmpty() || tool.getItem().getClass() != BlacksmithGavelItem.class) continue;
            ItemStack exactTool = tool.copyWithCount(1);
            if (seenTools.stream().anyMatch(seen -> ItemStack.isSameItemSameTags(seen, exactTool))) continue;
            seenTools.add(exactTool);
            BlacksmithGavelItem gavel = (BlacksmithGavelItem) exactTool.getItem();
            // getRemainingUses calls getOrCreateTag, so even READS must operate on a copy.
            int remaining = gavel.getRemainingUses(exactTool.copy());
            if (remaining <= 0) continue;
            ItemStack returnedTool = exactTool.copy();
            gavel.setRemainingUses(returnedTool, remaining - 1);

            for (ItemStack tierMold : molds) {
                int tier = HephaestusForgeBlock.getTier(tierMold);
                if (!validTier(tier) || tier < requiredTier
                        || !HephaestusForgeLevel.getFromIndex(tier).getMaxEssences().hasMoreThan(essences)) continue;
                List<CountedIngredient> withTool = new ArrayList<>(inputs);
                withTool.add(new CountedIngredient(StrictNBTIngredient.of(exactTool.copy()), 1L));
                ResourceLocation id = new ResourceLocation(sourceId.getNamespace(), sourceId.getPath()
                        + "_converted_forge_" + stackHash(tierMold) + "_gavel_" + stackHash(exactTool));
                result.add(new AdvancedAlloyFurnaceRecipe(
                        id, List.copyOf(withTool), List.of(), List.of(output.copy(), returnedTool.copy()), List.of(),
                        energyFor(essences), (int) RitualManager.DEFAULT_RITUAL_TIME,
                        Ingredient.EMPTY, 0, StrictNBTIngredient.of(tierMold.copy()), AlloyFurnaceMode.NORMAL));
            }
        }
        return List.copyOf(result);
    }

    /** Existing source-gameplay policy: one base FE unit per essence, minimum one unit. */
    static long energyFor(EssencesDefinition essences) {
        if (!validEssences(essences)) throw new IllegalArgumentException("Invalid Forbidden Arcanus essence costs");
        long sum = (long) essences.aureal() + essences.souls() + essences.blood() + essences.experience();
        // Four nonnegative int costs times DEFAULT_ENERGY fit a long. API failures propagate to
        // the caller's adapter guard; they must NOT be reinterpreted as a cheap default ritual.
        return Math.multiplyExact((long) AdapterUtils.DEFAULT_ENERGY, Math.max(1L, sum));
    }

    private static boolean validEssences(@Nullable EssencesDefinition essences) {
        return essences != null && essences.aureal() >= 0 && essences.souls() >= 0
                && essences.blood() >= 0 && essences.experience() >= 0;
    }

    private static boolean validTier(int tier) {
        return tier >= 1 && tier <= 5;
    }

    private static String stackHash(ItemStack stack) {
        return AdapterUtils.stableHash(stack.save(new CompoundTag()).toString());
    }

    private static void warn(ResourceLocation sourceId, String reason) {
        RateLimitedLogger.warn(LOGGER, "Skipping Forbidden Arcanus Hephaestus Forge ritual {}: {}", sourceId, reason);
    }
}
