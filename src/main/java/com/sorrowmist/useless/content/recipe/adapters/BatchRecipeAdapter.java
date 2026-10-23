package com.sorrowmist.useless.content.recipe.adapters;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.api.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeConversionUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared conversion and matching for the retained Forge batch-processing integrations. */
public abstract class BatchRecipeAdapter<T extends Recipe<?>> implements IRecipeAdapter<T> {
    private final Class<T> recipeClass;
    private final String sourceId;
    private final ResourceLocation moldId;
    private final String prefix;
    protected final AlloyFurnaceMode mode;

    protected BatchRecipeAdapter(Class<T> recipeClass, String sourceId, String mold,
                                 String prefix, AlloyFurnaceMode mode) {
        this.recipeClass = recipeClass;
        this.sourceId = sourceId;
        this.moldId = new ResourceLocation(sourceId, mold);
        this.prefix = prefix;
        this.mode = mode;
    }

    @Override public Class<T> getRecipeClass() { return recipeClass; }
    @Override public String sourceId() { return sourceId; }
    @Override public ItemStack getMoldItem() {
        var item = ForgeRegistries.ITEMS.getValue(moldId);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    public boolean matchesMold(ItemStack mold) {
        ItemStack required = getMoldItem();
        return !required.isEmpty() && mold != null && !mold.isEmpty() && ItemStack.isSameItem(required, mold);
    }

    protected AdvancedAlloyFurnaceRecipe recipe(ResourceLocation id, List<CountedIngredient> inputs,
                                               List<LongSizedFluidIngredient> fluids, List<ItemStack> outputs,
                                               List<FluidStack> outputFluids, long energy) {
        Ingredient catalyst = Ingredient.of(TagKey.create(Registries.ITEM, UselessMod.id("useless_ingots")));
        ItemStack mold = getMoldItem();
        if (mold.isEmpty()) throw new IllegalStateException("Missing batch recipe mold: " + moldId);
        return new AdvancedAlloyFurnaceRecipe(UselessMod.id(prefix + id.getNamespace() + "_" + id.getPath()),
                inputs, fluids, outputs, outputFluids, Math.max(0L, energy), 40, catalyst, 1,
                Ingredient.of(mold), mode);
    }

    @Override
    public List<RecipeHolder<T>> findMatchingRecipes(Level level, Map<Ingredient, Long> items,
                                                    Map<FluidStack, Long> fluids, Map<AEKey, Long> keys,
                                                    ItemStack mold, List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) return List.of();
        List<RecipeHolder<T>> result = new ArrayList<>();
        var keyInputs = keys.entrySet().stream().map(entry -> new GenericStack(entry.getKey(), entry.getValue())).toList();
        for (Recipe<?> source : level.getRecipeManager().getRecipes()) {
            if (!recipeClass.isInstance(source)) continue;
            var holder = RecipeHolder.of(recipeClass.cast(source));
            for (AdvancedAlloyFurnaceRecipe converted : RecipeConversionUtils.convertAll(this, holder, level, actualInputs)) {
                if (ItemIngredientAllocator.matches(converted.inputs(), actualInputs, keyInputs, 1L)
                        && FluidIngredientAllocator.matchesLong(converted.inputFluids(),
                        AdapterUtils.fluidSupplies(fluids), keyInputs, 1L)) {
                    result.add(holder);
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    public static List<CountedIngredient> items(List<Ingredient> inputs, long batch) {
        List<CountedIngredient> result = new ArrayList<>();
        for (Ingredient ingredient : inputs) {
            if (ingredient == Ingredient.EMPTY) continue;
            if (ingredient.isEmpty()) throw new IllegalArgumentException("Unresolved required batch ingredient");
            if (batch <= 0L) throw new IllegalArgumentException("Nonpositive batch ingredient count");
            result.add(new CountedIngredient(ingredient, batch));
        }
        return List.copyOf(result);
    }

    /** Expands only differing amounts; equivalent fluid/tag predicates remain live unions. */
    public static List<List<LongSizedFluidIngredient>> fluids(List<JsonElement> inputs, long batch) {
        List<List<LongSizedFluidIngredient>> combinations = new ArrayList<>();
        combinations.add(List.of());
        for (JsonElement input : inputs) {
            Map<Long, List<FluidIngredient>> groups = new LinkedHashMap<>();
            collectFluids(input, batch, groups, 0);
            if (groups.isEmpty()) throw new IllegalArgumentException("Missing required batch fluid");
            if ((long) combinations.size() * groups.size() > 4096L) {
                throw new IllegalArgumentException("Too many batch fluid alternatives");
            }
            List<List<LongSizedFluidIngredient>> next = new ArrayList<>();
            for (var previous : combinations) {
                for (var group : groups.entrySet()) {
                    var choice = new ArrayList<>(previous);
                    choice.add(new LongSizedFluidIngredient(FluidIngredient.any(group.getValue()), group.getKey()));
                    next.add(List.copyOf(choice));
                }
            }
            combinations = next;
        }
        return List.copyOf(combinations);
    }

    private static void collectFluids(JsonElement input, long batch,
                                      Map<Long, List<FluidIngredient>> groups, int depth) {
        if (depth > 64) throw new IllegalArgumentException("Batch fluid nesting exceeds 64");
        if (input.isJsonArray()) {
            for (JsonElement child : input.getAsJsonArray()) collectFluids(child, batch, groups, depth + 1);
            return;
        }
        JsonObject json = input.getAsJsonObject().deepCopy();
        long amount = json.has("amount") ? json.remove("amount").getAsLong() : 1L;
        if (amount <= 0L || batch <= 0L) throw new IllegalArgumentException("Nonpositive batch fluid amount");
        if (json.has("fluid_tag")) json.add("tag", json.remove("fluid_tag"));
        groups.computeIfAbsent(Math.multiplyExact(amount, batch), ignored -> new ArrayList<>())
                .add(FluidIngredient.fromJson(json));
    }

    public static ItemStack scale(ItemStack stack, long batch) {
        return stack.copyWithCount(Math.toIntExact(Math.multiplyExact(stack.getCount(), batch)));
    }

    public static FluidStack scale(FluidStack stack, long batch) {
        return new FluidStack(stack, Math.toIntExact(Math.multiplyExact(stack.getAmount(), batch)));
    }
}
