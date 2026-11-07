package com.sorrowmist.useless.content.recipe.adapters.lychee;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.api.recipe.IRecipeAdapter;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess;
import com.sorrowmist.useless.compat.lychee.LycheeBlockPredicates;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import snownee.lychee.RecipeTypes;
import snownee.lychee.block_crushing.BlockCrushingRecipe;
import snownee.lychee.core.contextual.Chance;
import snownee.lychee.core.contextual.ContextualCondition;
import snownee.lychee.core.post.DropItem;
import snownee.lychee.core.post.PostAction;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Converts the statically representable subset of Lychee 5.2.1 block crushing.
 *
 * <p>The existing workstation projection is a reusable, regular anvil mold. The falling predicate
 * must admit that anvil without state/NBT constraints; the landing predicate must be unrestricted.
 * A constrained landing block is not an extra item ingredient and must not be discarded.</p>
 *
 * <p>Only explicit {@code max_repeats = 1} and native, fixed {@link DropItem} actions are supported.
 * The default unbounded repeats, other repeat limits/ranges, contextual recipe gates, compound or
 * world actions, input replacement/consumption prevention, damage and NBT patches are rejected as
 * a whole. Display outputs are never used as an action interpreter.</p>
 *
 * <p>Independent Chance conditions on drops retain the existing expected-output batch policy.
 * Every consumed ingredient slot, deterministic output, FE cost and processing time is multiplied
 * by the same operation count. A recipe-level chance is different: it gates matching <em>before</em>
 * consumption, so a fractional recipe chance cannot be folded into lossy output rolls.</p>
 */
public final class LycheeBlockCrushingRecipeAdapter implements IRecipeAdapter<BlockCrushingRecipe> {
    // BlockCrushingRecipe.Serializer in 5.2.1 limits item_in to 27 unit ingredient slots.
    private static final int MAX_INPUT_SLOTS = 27;

    @Override
    public String sourceId() {
        return RecipeSourceIds.LYCHEE;
    }

    @Override
    public Class<BlockCrushingRecipe> getRecipeClass() {
        return BlockCrushingRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(Items.ANVIL);
    }

    @Override
    public List<RecipeHolder<BlockCrushingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        return findMatchingRecipes(level, mergedInputs, mergedFluids, Map.of(), mold);
    }

    @Override
    public List<RecipeHolder<BlockCrushingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        return findMatchingRecipes(level, mergedInputs, mergedFluids, mergedKeys, mold, null);
    }

    @Override
    public List<RecipeHolder<BlockCrushingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold, @Nullable List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }

        List<GenericStack> itemKeys = new ArrayList<>();
        if (actualInputs != null && mergedKeys != null) {
            mergedKeys.forEach((key, count) -> {
                if (key instanceof AEItemKey && count != null && count > 0) {
                    itemKeys.add(new GenericStack(key, count));
                }
            });
        }

        List<RecipeHolder<BlockCrushingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<BlockCrushingRecipe> holder : RecipeManagerAccess.getAllRecipesFor(
                level.getRecipeManager(), RecipeTypes.BLOCK_CRUSHING)) {
            AdvancedAlloyFurnaceRecipe converted = convertRecipe(holder);
            if (converted == null) {
                continue;
            }
            if (matchesInputs(converted, mergedInputs, actualInputs, itemKeys)) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<BlockCrushingRecipe> holder, Level level) {
        AdvancedAlloyFurnaceRecipe converted = convertRecipe(holder);
        return converted == null ? List.of() : List.of(converted);
    }

    @Nullable
    private AdvancedAlloyFurnaceRecipe convertRecipe(@Nullable RecipeHolder<BlockCrushingRecipe> holder) {
        if (holder == null || holder.value() == null) {
            return null;
        }
        BlockCrushingRecipe source = holder.value();
        if (source.getClass() != BlockCrushingRecipe.class || source.ghost || source.isUnstableContext()
                || !LycheeBlockPredicates.acceptsRegularAnvil(source.getBlock())
                || !LycheeBlockPredicates.isUnrestricted(source.getLandingBlock())) {
            return null;
        }

        var repeats = source.getMaxRepeats();
        if (repeats == null || !Integer.valueOf(1).equals(repeats.getMin())
                || !Integer.valueOf(1).equals(repeats.getMax())) {
            // This is a limit (possibly random), not a free output multiplier or a minimum batch.
            return null;
        }
        OptionalDouble recipeChance = chanceOf(source.getConditions());
        if (recipeChance.isEmpty() || recipeChance.getAsDouble() != 1.0d) {
            return null;
        }

        List<Ingredient> ingredients = source.getIngredients();
        if (ingredients == null || ingredients.isEmpty() || ingredients.size() > MAX_INPUT_SLOTS) {
            return null;
        }
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty()) {
                return null;
            }
        }

        Optional<List<ExpectedOutputScaler.WeightedItemOutput>> outputs = collectOutputs(source);
        if (outputs.isEmpty()) {
            return null;
        }
        Optional<ExpectedOutputScaler.ScaledOutputs> scaled = ExpectedOutputScaler.scale(outputs.get());
        if (scaled.isEmpty() || scaled.get().outputs().isEmpty()) {
            return null;
        }
        int operations = scaled.get().operations();
        if (operations <= 0) {
            return null;
        }

        // 5.2.1 consumes ONE item per Ingredient entry, not a Kiwi SizedIngredient count or the
        // count of a display stack. Keep the original predicates and repeated slots, including NBT.
        List<CountedIngredient> inputs = new ArrayList<>(ingredients.size());
        for (Ingredient ingredient : ingredients) {
            inputs.add(new CountedIngredient(ingredient, (long) operations));
        }

        final long energy;
        final int processTime;
        try {
            energy = Math.multiplyExact((long) AdapterUtils.DEFAULT_ENERGY, operations);
            processTime = Math.multiplyExact(AdapterUtils.DEFAULT_PROCESS_TIME, operations);
        } catch (ArithmeticException overflow) {
            return null;
        }

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.copyOf(inputs),
                List.of(),
                List.of(),
                scaled.get().outputs(),
                List.of(),
                List.of(),
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL);
    }

    private static Optional<List<ExpectedOutputScaler.WeightedItemOutput>> collectOutputs(
            BlockCrushingRecipe recipe) {
        List<ExpectedOutputScaler.WeightedItemOutput> outputs = new ArrayList<>();
        for (PostAction action : recipe.getPostActions().toList()) {
            // Exact-class whitelist: SetItem, If, RandomSelect, custom actions and even DropItem
            // subclasses can advertise the same viewer stacks but have different runtime effects.
            if (action == null || action.getClass() != DropItem.class || action.path != null) {
                return Optional.empty();
            }
            OptionalDouble chance = chanceOf(action.getConditions());
            ItemStack stack = ((DropItem) action).stack;
            if (chance.isEmpty() || stack == null || stack.isEmpty() || stack.getCount() <= 0) {
                return Optional.empty();
            }
            if (chance.getAsDouble() > 0.0d) {
                outputs.add(new ExpectedOutputScaler.WeightedItemOutput(
                        stack.copy(), stack.getCount(), stack.getCount(), chance.getAsDouble()));
            }
        }
        return Optional.of(List.copyOf(outputs));
    }

    /** Every condition must be understood, including conditions after a zero chance. */
    private static OptionalDouble chanceOf(@Nullable List<ContextualCondition> conditions) {
        if (conditions == null) {
            return OptionalDouble.empty();
        }
        double chance = 1.0d;
        for (ContextualCondition condition : conditions) {
            if (!(condition instanceof Chance roll) || !Float.isFinite(roll.chance())
                    || roll.chance() < 0.0f || roll.chance() > 1.0f) {
                return OptionalDouble.empty();
            }
            chance *= roll.chance();
        }
        return OptionalDouble.of(chance);
    }

    // Shared with native tests: RecipeTypes itself registers entries in a static initializer and
    // must not be forced to initialize in a launcher-less test merely to exercise input allocation.
    static boolean matchesInputs(AdvancedAlloyFurnaceRecipe recipe, Map<Ingredient, Long> mergedInputs,
                                 @Nullable List<ItemStack> actualInputs, List<GenericStack> itemKeys) {
        return actualInputs == null
                ? AdapterUtils.matchesRequired(mergedInputs, requiredCounts(recipe.inputs()))
                : ItemIngredientAllocator.matches(recipe.inputs(), actualInputs, itemKeys, 1L);
    }

    private static Map<Ingredient, Long> requiredCounts(List<CountedIngredient> inputs) {
        // Do not equate custom predicates through viewer stacks or Ingredient.equals().
        Map<Ingredient, Long> required = new IdentityHashMap<>();
        for (CountedIngredient input : inputs) {
            required.merge(input.ingredient(), input.count(), Math::addExact);
        }
        return required;
    }
}
