package com.sorrowmist.useless.content.recipe.adapters.apothicflux;

import appeng.api.stacks.AEKey;
import com.chuan.apothicflux.block.Tier;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.api.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import dev.shadowsoffire.apothic_enchanting.Ench;
import dev.shadowsoffire.apothic_enchanting.table.EnchantmentTableStats;
import dev.shadowsoffire.apothic_enchanting.table.infusion.InfusionRecipe;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Apothic Enchanting infusion recipes using configured Flux bookshelves as molds. */
public final class InfusionRecipeAdapter implements IRecipeAdapter<InfusionRecipe> {
    private static final float BASE_QUANTA = EnchantmentTableStats.vanilla(0).quanta();

    private static final List<ShelfDefinition> SHELVES = List.of(
            new ShelfDefinition("flux_stats_bookshelf_tier_1", Tier.TIER_1),
            new ShelfDefinition("flux_stats_bookshelf_tier_2", Tier.TIER_2),
            new ShelfDefinition("flux_stats_bookshelf_tier_3", Tier.TIER_3),
            new ShelfDefinition("flux_stats_bookshelf_tier_4", Tier.TIER_4));

    private static final Map<ResourceLocation, ShelfDefinition> SHELF_BY_ID = SHELVES.stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                    definition -> definition.id(), definition -> definition));

    @Override
    public String sourceId() {
        return RecipeSourceIds.APOTHIC_FLUX;
    }

    @Override
    public Class<InfusionRecipe> getRecipeClass() {
        return InfusionRecipe.class;
    }

    /** This adapter handles all four shelf items, so it must use the fallback lookup path. */
    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return shelfDefinition(mold) != null;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<InfusionRecipe> holder, Level level) {
        return convertRecipes(holder, null);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<InfusionRecipe> holder, Level level, List<ItemStack> actualInputs) {
        InfusionRecipe source = holder == null ? null : holder.value();
        ItemStack actualInput = source == null ? null : findMatchingInput(source, actualInputs);
        return convertRecipes(holder, actualInput);
    }

    @Override
    public List<RecipeHolder<InfusionRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        return findMatchingRecipes(level, mergedInputs, mergedFluids, Map.of(), mold, List.of());
    }

    @Override
    public List<RecipeHolder<InfusionRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, Map<AEKey, Long> mergedKeys,
            @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) return List.of();

        ShelfDefinition shelf = shelfDefinition(mold);
        if (shelf == null) return List.of();
        ShelfStats stats = readStats(mold, shelf);

        RecipeHolder<InfusionRecipe> bestMatch = null;
        boolean hasActualInputs = actualInputs != null && actualInputs.stream()
                .anyMatch(stack -> stack != null && !stack.isEmpty());
        for (RecipeHolder<InfusionRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(Ench.RecipeTypes.INFUSION)) {
            InfusionRecipe recipe = holder.value();
            ItemStack input = findMatchingInput(recipe, actualInputs);
            boolean inputMatches;
            if (hasActualInputs) {
                inputMatches = input != null && recipe.matches(
                        input, stats.eterna(), stats.quanta(), stats.arcana());
            } else {
                ItemStack mergedInput = firstMergedInput(recipe, mergedInputs);
                inputMatches = mergedInput != null && recipe.matches(
                        mergedInput, stats.eterna(), stats.quanta(), stats.arcana());
            }
            if (!inputMatches) continue;

            if (bestMatch == null || recipe.getRequirements().eterna()
                    > bestMatch.value().getRequirements().eterna()) {
                bestMatch = holder;
            }
        }
        return bestMatch == null ? List.of() : List.of(bestMatch);
    }

    private static List<AdvancedAlloyFurnaceRecipe> convertRecipes(
            @Nullable RecipeHolder<InfusionRecipe> holder, @Nullable ItemStack actualInput) {
        if (holder == null || holder.value() == null) return List.of();

        InfusionRecipe source = holder.value();
        if (source.getInput() == null || source.getInput().isEmpty()
                || source.getOutput() == null || source.getOutput().isEmpty()) {
            return List.of();
        }

        ItemStack output = outputFor(source, actualInput);
        if (output.isEmpty()) return List.of();

        List<AdvancedAlloyFurnaceRecipe> converted = new ArrayList<>(SHELVES.size());
        for (ShelfDefinition shelf : SHELVES) {
            Item item = shelfItem(shelf);
            if (item == null) continue;

            converted.add(new AdvancedAlloyFurnaceRecipe(
                    variantId(holder.id(), shelf),
                    List.of(new CountedIngredient(source.getInput(), 1)),
                    List.of(),
                    List.of(output.copy()),
                    List.of(),
                    AdapterUtils.DEFAULT_ENERGY,
                    AdapterUtils.DEFAULT_PROCESS_TIME,
                    Ingredient.EMPTY,
                    0,
                    Ingredient.of(item),
                    AlloyFurnaceMode.NORMAL));
        }
        return List.copyOf(converted);
    }

    @Nullable
    private static ItemStack findMatchingInput(
            InfusionRecipe recipe, @Nullable List<ItemStack> actualInputs) {
        if (actualInputs == null || actualInputs.isEmpty()) return null;
        return actualInputs.stream()
                .filter(stack -> stack != null && !stack.isEmpty() && recipe.getInput().test(stack))
                .findFirst()
                .orElse(null);
    }

    @Nullable
    private static ItemStack firstMergedInput(
            InfusionRecipe recipe, @Nullable Map<Ingredient, Long> mergedInputs) {
        if (mergedInputs == null || mergedInputs.isEmpty()) return null;

        for (Map.Entry<Ingredient, Long> entry : mergedInputs.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0
                    || entry.getKey() == null || entry.getKey().isEmpty()) {
                continue;
            }
            try {
                for (ItemStack stack : entry.getKey().getItems()) {
                    if (stack != null && !stack.isEmpty() && recipe.getInput().test(stack)) {
                        return stack;
                    }
                }
            } catch (RuntimeException ignored) {
                // Opaque custom ingredients are handled by the concrete-input path.
            }
        }
        return null;
    }

    private static ItemStack outputFor(InfusionRecipe recipe, @Nullable ItemStack actualInput) {
        if (actualInput == null || actualInput.isEmpty()) return recipe.getOutput().copy();

        return recipe.assemble(actualInput,
                recipe.getRequirements().eterna(),
                recipe.getRequirements().quanta(),
                recipe.getRequirements().arcana());
    }

    @Nullable
    private static ShelfDefinition shelfDefinition(@Nullable ItemStack mold) {
        if (mold == null || mold.isEmpty()) return null;
        return SHELF_BY_ID.get(BuiltInRegistries.ITEM.getKey(mold.getItem()));
    }

    @Nullable
    private static Item shelfItem(ShelfDefinition shelf) {
        return BuiltInRegistries.ITEM.getOptional(shelf.id()).orElse(null);
    }

    private static ShelfStats readStats(ItemStack mold, ShelfDefinition shelf) {
        // A plain shelf item has the block entity defaults: zero configurable stats. Configured
        // values are read from the block-entity/custom-data component when the item carries them.
        float rawQuanta = clamp(readStat(mold, "Quanta", 0F), Tier.MIN_QUANTA, shelf.maxQuanta());
        return new ShelfStats(
                clamp(readStat(mold, "Eterna", 0F), 0F, shelf.maxEterna()),
                clamp(rawQuanta + BASE_QUANTA, 0F, 100F),
                clamp(readStat(mold, "Arcana", 0F), 0F, shelf.maxArcana()));
    }

    private static float readStat(ItemStack stack, String key, float fallback) {
        CustomData blockEntityData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        Float value = readStat(blockEntityData == null ? null : blockEntityData.copyTag(), key);
        if (value != null) return value;

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        value = readStat(customData == null ? null : customData.copyTag(), key);
        return value == null ? fallback : value;
    }

    @Nullable
    private static Float readStat(@Nullable CompoundTag tag, String key) {
        if (tag == null) return null;
        if (tag.contains(key, Tag.TAG_INT)) return (float) tag.getInt(key);
        if (tag.contains(key, Tag.TAG_FLOAT)) return tag.getFloat(key);
        if (tag.contains(key, Tag.TAG_DOUBLE)) return (float) tag.getDouble(key);
        if (tag.contains(key, Tag.TAG_LONG)) return (float) tag.getLong(key);
        return null;
    }

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private record ShelfDefinition(ResourceLocation id, float maxEterna, float maxQuanta, float maxArcana) {
        private ShelfDefinition(String path, Tier tier) {
            this(ResourceLocation.fromNamespaceAndPath("apothic_flux", path),
                    tier.getMaxEterna(), tier.getMaxQuanta(), tier.getMaxArcana());
        }
    }

    private record ShelfStats(float eterna, float quanta, float arcana) {
    }

    private static ResourceLocation variantId(ResourceLocation source, ShelfDefinition shelf) {
        return ResourceLocation.fromNamespaceAndPath(
                source.getNamespace(),
                source.getPath() + "_converted_" + shelf.id().getPath());
    }
}
