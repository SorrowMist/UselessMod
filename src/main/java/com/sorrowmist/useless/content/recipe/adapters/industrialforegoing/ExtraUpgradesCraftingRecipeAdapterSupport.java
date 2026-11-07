package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.yxiao233.ifeu.common.config.machine.PrecisionCraftingTableConfig;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Native Forge IFEU 2.5.000 semantics, not the later List<Ingredient> API. */
final class ExtraUpgradesCraftingRecipeAdapterSupport {
    // PrecisionCraftingTableEntity inherits IndustrialProcessingTile.getMaxProgress() = 100.
    private static final int PRECISION_PROCESS_TIME = 100;
    private static final ResourceLocation AIR_PLACEHOLDER = new ResourceLocation("ifeu", "air");
    private static final ClassValue<Boolean> STATIC_CRAFTED_OUTPUT = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            return inheritsItemMethod(type, void.class, ItemStack.class, Level.class, Player.class);
        }
    };

    private ExtraUpgradesCraftingRecipeAdapterSupport() {
    }

    static ItemStack mold(String path) {
        // IFEUBlocks uses Pair<RegistryObject<Block>, RegistryObject<BlockEntityType<?>>> in
        // this release. Resolve its registered item ID without initializing the mod's modules.
        return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("ifeu", path)));
    }

    static <T extends Recipe<?>> List<RecipeHolder<T>> recipes(Level level, Class<T> type) {
        if (level == null) return List.of();
        List<RecipeHolder<T>> result = new ArrayList<>();
        // Enumerate only loaded recipes; never resurrect RECIPES (datagen constructors append
        // to that static list, even when a datapack condition has excluded the recipe).
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            if (type.isInstance(recipe)) {
                result.add(new RecipeHolder<>(recipe.getId(), type.cast(recipe)));
            }
        }
        return List.copyOf(result);
    }

    @Nullable
    static AdvancedAlloyFurnaceRecipe precisionRecipe(
            ResourceLocation id, Ingredient.Value[] sourceInputs, boolean shaped,
            ItemStack sourceOutput, float chance, ItemStack table) {
        // onFinish clamps chance to 1, but otherwise rolls once and consumes every occupied
        // slot even on failure. An expected-value batch is NOT an exact deterministic recipe.
        if (!Float.isFinite(chance) || chance < 1.0F
                || PrecisionCraftingTableConfig.powerPerTick < 0 || !staticOutput(sourceOutput)
                || table == null || table.isEmpty()) return null;
        List<CountedIngredient> inputs = countedInputs(sourceInputs, shaped);
        if (inputs == null) return null;
        return recipe(id, inputs, List.of(), sourceOutput,
                (long) PrecisionCraftingTableConfig.powerPerTick * PRECISION_PROCESS_TIME,
                PRECISION_PROCESS_TIME, table);
    }

    @Nullable
    static AdvancedAlloyFurnaceRecipe fluidRecipe(
            ResourceLocation id, Ingredient.Value[] sourceInputs, boolean shaped,
            FluidStack sourceFluid, ItemStack sourceOutput, ItemStack table) {
        if (sourceFluid == null || sourceFluid.isEmpty() || sourceFluid.getAmount() <= 0
                || !staticOutput(sourceOutput) || table == null || table.isEmpty()) return null;
        List<CountedIngredient> inputs = countedInputs(sourceInputs, shaped);
        if (inputs == null) return null;
        // FluidCraftingTableEntity: getMaxProgress() = 1, getTickPower() = 0.
        return recipe(id, inputs, List.of(exactFluid(sourceFluid)), sourceOutput, 0L, 1, table);
    }

    private static AdvancedAlloyFurnaceRecipe recipe(
            ResourceLocation id, List<CountedIngredient> inputs,
            List<LongSizedFluidIngredient> fluids, ItemStack output,
            long energy, int time, ItemStack table) {
        return new AdvancedAlloyFurnaceRecipe(id, inputs, fluids, List.of(),
                List.of(output.copy()), List.of(), List.of(), energy, time, Ingredient.EMPTY, 0,
                List.of(AdapterUtils.toMoldIngredient(table)), AlloyFurnaceMode.NORMAL);
    }

    static LongSizedFluidIngredient exactFluid(FluidStack fluid) {
        // Titanium drainForced(FluidStack, ...) uses isFluidEqual: fluid identity AND exact
        // NBT, including absence of NBT. SizedFluidIngredient.of(untagged) would broaden it.
        return new LongSizedFluidIngredient(FluidIngredient.nbt(true, fluid), fluid.getAmount());
    }

    static boolean staticOutput(@Nullable ItemStack output) {
        // Both recipe constructors and BE onFinish invoke Item.onCraftedBy. Do not freeze a
        // map/world/random/custom crafting callback into its display stack, nor execute it
        // during catalog building. Input-dependent recipe subclasses are rejected by callers.
        return output != null && !output.isEmpty() && output.getCount() > 0
                && STATIC_CRAFTED_OUTPUT.get(output.getItem().getClass());
    }

    static boolean staticDefaultStack(Item item) {
        return inheritsItemMethod(item.getClass(), ItemStack.class);
    }

    private static boolean inheritsItemMethod(Class<?> type, Class<?> result, Class<?>... parameters) {
        try {
            // Structural lookup survives Mojmap/SRG runtime names. Each signature is unique
            // on Item in MC 1.20.1 (onCraftedBy / getDefaultInstance respectively).
            var candidates = Arrays.stream(Item.class.getDeclaredMethods())
                    .filter(method -> !Modifier.isStatic(method.getModifiers())
                            && method.getReturnType() == result
                            && Arrays.equals(method.getParameterTypes(), parameters))
                    .toList();
            return candidates.size() == 1
                    && type.getMethod(candidates.get(0).getName(), parameters).getDeclaringClass() == Item.class;
        } catch (ReflectiveOperationException | SecurityException exception) {
            return false;
        }
    }

    static boolean matches(
            AdvancedAlloyFurnaceRecipe recipe,
            @Nullable Map<Ingredient, Long> mergedInputs,
            @Nullable Map<FluidStack, Long> mergedFluids) {
        if (recipe == null) return false;
        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        for (CountedIngredient input : recipe.inputs()) {
            requirements.put(input.ingredient(), input.count());
        }
        return AdapterUtils.matchesRequired(
                mergedInputs == null ? Map.of() : mergedInputs, requirements)
                && FluidIngredientAllocator.matchesLong(
                recipe.inputFluids(), mergedFluids == null ? Map.of() : mergedFluids, 1L);
    }

    @Nullable
    private static List<CountedIngredient> countedInputs(Ingredient.Value[] values, boolean shaped) {
        // Shaped.matches initializes nine booleans to false; a short array never matches,
        // and a long one indexes outside the actual 3x3 inventory. Shapeless has no padding.
        if (values == null || (shaped ? values.length != 9 : values.length > 9)) return null;
        Map<String, CountedIngredient> counts = new LinkedHashMap<>();
        for (Ingredient.Value value : values) {
            // Native Value is a candidate supplier, NOT an arbitrary Ingredient predicate.
            // Only the two known vanilla implementations have statically understood meaning.
            if (value == null || (value.getClass() != Ingredient.ItemValue.class
                    && value.getClass() != Ingredient.TagValue.class)) return null;
            Ingredient ingredient;
            if (value instanceof Ingredient.ItemValue) {
                var items = value.getItems();
                if (items.size() != 1) return null;
                ItemStack stack = items.iterator().next();
                if (stack == null || stack.isEmpty()) return null;
                if (shaped && isPlaceholder(stack)) continue;
                // Both shaped and shapeless match item identity only. Count/NBT on the
                // candidate stack do not constrain input; BE shrinks ONE per occupied slot,
                // including tools and container items (no damage/remainder callback).
                ingredient = Ingredient.of(stack.getItem());
            } else {
                // Keep a live tag, including an unbound tag. A tag mixing the shaped hole
                // marker with real items is an optional slot, which cannot be flattened.
                if (shaped && value.getItems().stream().anyMatch(
                        ExtraUpgradesCraftingRecipeAdapterSupport::isPlaceholder)) return null;
                ingredient = Ingredient.fromValues(Stream.of(value));
            }
            String key = ingredient.toJson().toString();
            if (!shaped) {
                // Native shapeless greedily chooses the FIRST candidate in each Value,
                // unlike our global allocator. Overlapping nonidentical alternatives could
                // make it fail for an otherwise feasible allocation; do not broaden those.
                for (Map.Entry<String, CountedIngredient> row : counts.entrySet()) {
                    if (!key.equals(row.getKey()) && overlaps(ingredient, row.getValue().ingredient())) {
                        return null;
                    }
                }
            }
            CountedIngredient previous = counts.get(key);
            counts.put(key, new CountedIngredient(ingredient,
                    previous == null ? 1L : previous.count() + 1L));
        }
        return List.copyOf(counts.values());
    }

    private static boolean overlaps(Ingredient left, Ingredient right) {
        for (ItemStack a : left.getItems()) {
            for (ItemStack b : right.getItems()) {
                if (ItemStack.isSameItem(a, b)) return true;
            }
        }
        return false;
    }

    private static boolean isPlaceholder(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && AIR_PLACEHOLDER.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
}
