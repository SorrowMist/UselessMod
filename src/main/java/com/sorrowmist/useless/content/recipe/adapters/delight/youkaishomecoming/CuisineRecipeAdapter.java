package com.sorrowmist.useless.content.recipe.adapters.delight.youkaishomecoming;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightSyntheticRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Youkai's cuisine-board recipes. */
public final class CuisineRecipeAdapter implements IRecipeAdapter<DelightSyntheticRecipe> {
    private static final List<String> SOURCE_CLASSES = List.of(
            "dev.xkmc.youkaishomecoming.content.pot.table.recipe.FixedCuisineRecipe",
            "dev.xkmc.youkaishomecoming.content.pot.table.recipe.MixedCuisineRecipe",
            "dev.xkmc.youkaishomecoming.content.pot.table.recipe.OrderedCuisineRecipe",
            "dev.xkmc.youkaishomecoming.content.pot.table.recipe.UnorderedCuisineRecipe"
    );
    private static final ResourceLocation CUISINE_BOARD_ID =
            ResourceLocation.fromNamespaceAndPath("youkaisfeasts", "cuisine_board");

    @Override
    public String sourceId() {
        return RecipeSourceIds.YOUKAI_HOMECOMING;
    }

    @Override
    public Class<DelightSyntheticRecipe> getRecipeClass() {
        return DelightSyntheticRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        Item item = DelightRecipeAdapterUtils.registeredItem(CUISINE_BOARD_ID);
        return item == null ? ItemStack.EMPTY : item.getDefaultInstance();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<DelightSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null
                || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<RecipeHolder<DelightSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) {
            return List.of();
        }

        List<RecipeHolder<DelightSyntheticRecipe>> generated = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (holder.value() == null
                    || !SOURCE_CLASSES.contains(holder.value().getClass().getName())) {
                continue;
            }
            AdvancedAlloyFurnaceRecipe converted = convertSource(holder.id(), holder.value());
            if (converted != null) {
                generated.add(new RecipeHolder<>(converted.id(), new DelightSyntheticRecipe(converted)));
            }
        }
        return List.copyOf(generated);
    }

    @Override
    public List<RecipeHolder<DelightSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }

        List<RecipeHolder<DelightSyntheticRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<DelightSyntheticRecipe> holder : getGeneratedRecipes(level)) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value().convertedRecipe();
            if (recipe != null
                    && DelightRecipeAdapterUtils.matchesItems(recipe.inputs(), mergedInputs, List.of())
                    && DelightRecipeAdapterUtils.matchesFluids(recipe.inputFluids(), mergedFluids)) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertSource(ResourceLocation sourceId,
                                                              Object source) {
        ResourceLocation base = DelightRecipeAdapterUtils.fieldValue(
                source, "base", ResourceLocation.class);
        ItemStack output = DelightRecipeAdapterUtils.fieldValue(source, "result", ItemStack.class);
        if (base == null || output == null || output.isEmpty() || output.getCount() <= 0) {
            return null;
        }

        List<Ingredient> ingredients = sourceIngredients(source, base);
        if (ingredients.isEmpty()) {
            return null;
        }

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(sourceId),
                AdapterUtils.mergeIngredients(ingredients),
                List.of(),
                List.of(),
                List.of(output.copy()),
                List.of(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(moldStack())),
                AlloyFurnaceMode.NORMAL
        );
    }

    private static List<Ingredient> sourceIngredients(Object source, ResourceLocation base) {
        List<Ingredient> baseIngredients = new ArrayList<>();
        List<Ingredient> customIngredients = new ArrayList<>();
        collectBaseIngredients(base, baseIngredients, customIngredients);

        String className = source.getClass().getName();
        if (className.endsWith("MixedCuisineRecipe")) {
            appendIngredients(customIngredients, source, "first");
            appendIngredients(customIngredients, source, "second");
        } else if (!className.endsWith("FixedCuisineRecipe")) {
            appendIngredients(customIngredients, source, "input");
        }

        baseIngredients.addAll(customIngredients);
        return baseIngredients.stream()
                .filter(ingredient -> ingredient != null && !ingredient.isEmpty())
                .toList();
    }

    private static void appendIngredients(List<Ingredient> target, Object source, String field) {
        List<?> values = DelightRecipeAdapterUtils.fieldValue(source, field, List.class);
        if (values == null) {
            return;
        }
        for (Object value : values) {
            if (value instanceof Ingredient ingredient) {
                target.add(ingredient);
            }
        }
    }

    private static void collectBaseIngredients(ResourceLocation base,
                                               List<Ingredient> baseIngredients,
                                               List<Ingredient> customIngredients) {
        try {
            Class<?> variantClass = Class.forName(
                    "dev.xkmc.youkaishomecoming.content.pot.table.item.VariantTableItemBase");
            Field mapField = variantClass.getDeclaredField("MAP");
            if (!mapField.trySetAccessible()) {
                return;
            }
            Object mapValue = mapField.get(null);
            if (mapValue instanceof Map<?, ?> map && map.get(base) != null) {
                Method collect = variantClass.getMethod(
                        "collectIngredients", List.class, List.class);
                collect.invoke(map.get(base), baseIngredients, customIngredients);
                return;
            }

            Class<?> fixedClass = Class.forName(
                    "dev.xkmc.youkaishomecoming.content.pot.table.item.IngredientTableItem");
            Field fixedField = fixedClass.getDeclaredField("FIXED");
            if (!fixedField.trySetAccessible()) {
                return;
            }
            Object fixedValue = fixedField.get(null);
            if (fixedValue instanceof Map<?, ?> fixed && fixed.get(base) != null) {
                Method collect = fixedClass.getMethod("collectIngredients", List.class);
                collect.invoke(fixed.get(base), baseIngredients);
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Fall back to a direct item base below when the optional table model is unavailable.
        }

        Item item = BuiltInRegistries.ITEM.get(base);
        if (item != null && item != Items.AIR) {
            baseIngredients.add(Ingredient.of(item));
        }
    }

    @Nullable
    private static ItemStack moldStack() {
        Item item = DelightRecipeAdapterUtils.registeredItem(CUISINE_BOARD_ID);
        return item == null ? null : item.getDefaultInstance();
    }
}
