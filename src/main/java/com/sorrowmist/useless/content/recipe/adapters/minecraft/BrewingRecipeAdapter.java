package com.sorrowmist.useless.content.recipe.adapters.minecraft;

import com.sorrowmist.useless.utils.RateLimitedLogger;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.recipe.DataComponentIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.NbtCanonical;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.recipe.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.brewing.BrewingRecipe;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Native Forge brewing, including mod IBrewingRecipe implementations and arbitrary input NBT.
 * The catalogue is a finite display of known candidates; the execution path always asks the
 * native ordered registry again for the actual input pair, never a flattened Ingredient sample.
 */
@Mod.EventBusSubscriber(modid = "useless_mod")
public final class BrewingRecipeAdapter implements IRecipeAdapter<BrewingSyntheticRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int INPUT_BOTTLES = 3;
    private static final int MAX_BUILD_ATTEMPTS = 32;
    private static final AtomicLong RELOAD_EPOCH = new AtomicLong();
    private volatile Cache cache;
    private record Cache(RecipeManager manager, List<IBrewingRecipe> sources, long epoch,
                         List<RecipeHolder<BrewingSyntheticRecipe>> recipes) {}

    @SubscribeEvent public static void tagsUpdated(TagsUpdatedEvent event) { RELOAD_EPOCH.incrementAndGet(); }
    @SubscribeEvent public static void datapackSync(OnDatapackSyncEvent event) { RELOAD_EPOCH.incrementAndGet(); }

    @Override public Class<BrewingSyntheticRecipe> getRecipeClass() { return BrewingSyntheticRecipe.class; }
    @Override public ItemStack getMoldItem() { return new ItemStack(Items.BREWING_STAND); }

    @Override public List<RecipeHolder<BrewingSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) return List.of();
        var sources = nativeSnapshot();
        long epoch = RELOAD_EPOCH.get();
        var cached = cache;
        if (cached != null && cached.manager() == level.getRecipeManager()
                && cached.epoch() == epoch && cached.sources().equals(sources)) return cached.recipes();
        synchronized (this) {
            cached = cache;
            if (cached != null && cached.manager() == level.getRecipeManager()
                    && cached.epoch() == epoch && cached.sources().equals(sources)) return cached.recipes();
            for (int attempt = 0; ; attempt++) {
                try {
                    var result = createStaticRecipes(sources, level.registryAccess());
                    cache = new Cache(level.getRecipeManager(), sources, epoch, result);
                    return result;
                } catch (ConcurrentModificationException busy) {
                    if (attempt >= MAX_BUILD_ATTEMPTS - 1 || Thread.currentThread().isInterrupted()) throw busy;
                    Thread.yield();
                }
            }
        }
    }

    private static List<IBrewingRecipe> nativeSnapshot() {
        for (int attempt = 0; ; attempt++) {
            try { return List.copyOf(BrewingRecipeRegistry.getRecipes()); }
            catch (ConcurrentModificationException busy) {
                if (attempt >= MAX_BUILD_ATTEMPTS - 1 || Thread.currentThread().isInterrupted()) throw busy;
                Thread.yield();
            }
        }
    }

    @Override public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<BrewingSyntheticRecipe> holder, Level level) {
        return holder == null || holder.value() == null || holder.value().convertedRecipe() == null
                ? List.of() : List.of(holder.value().convertedRecipe());
    }

    @Override public List<RecipeHolder<BrewingSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        return matchingRecipes(getGeneratedRecipes(level), mergedInputs);
    }

    @Override public List<RecipeHolder<BrewingSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<appeng.api.stacks.AEKey, Long> mergedKeys, @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) return List.of();
        if (actualInputs != null && !actualInputs.isEmpty()) {
            // An empty native result must NOT fall back to an old/static recipe for the same
            // item id. It may have been disabled, overridden or made NBT-dependent by a mod.
            return findRuntimeRecipes(nativeSnapshot(), actualInputs, level.registryAccess());
        }
        return matchingRecipes(getGeneratedRecipes(level), mergedInputs);
    }

    static List<RecipeHolder<BrewingSyntheticRecipe>> createStaticRecipes(
            List<IBrewingRecipe> sources, RegistryAccess registries) {
        var inputs = new LinkedHashMap<String, ItemStack>();
        var reagents = new LinkedHashMap<String, ItemStack>();
        // Native vanilla potion stacks use PotionUtils / Potion NBT, not a 1.21 component.
        for (var potion : BuiltInRegistries.POTION) {
            for (var item : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION)) {
                addCandidate(inputs, PotionUtils.setPotion(new ItemStack(item), potion));
            }
        }
        for (var source : sources) {
            if (source instanceof BrewingRecipe simple) {
                for (var stack : simple.getInput().getItems()) addCandidate(inputs, stack);
                for (var stack : simple.getIngredient().getItems()) addCandidate(reagents, stack);
            }
        }
        for (var item : BuiltInRegistries.ITEM) {
            var probe = new ItemStack(item);
            if (probe.isEmpty()) continue;
            for (var source : sources) {
                if (source.isInput(probe)) addCandidate(inputs, probe);
                if (source.isIngredient(probe)) addCandidate(reagents, probe);
            }
        }
        return createStaticRecipes(sources, registries, List.copyOf(inputs.values()), List.copyOf(reagents.values()));
    }

    static List<RecipeHolder<BrewingSyntheticRecipe>> createStaticRecipes(
            List<IBrewingRecipe> sources, RegistryAccess registries,
            List<ItemStack> inputs, List<ItemStack> reagents) {
        var recipes = new LinkedHashMap<ResourceLocation, RecipeHolder<BrewingSyntheticRecipe>>();
        for (var input : inputs) for (var reagent : reagents) {
            var converted = convertPair(sources, input, reagent);
            if (converted != null) recipes.putIfAbsent(converted.id(), holder(converted));
        }
        return List.copyOf(recipes.values());
    }

    private static void addCandidate(Map<String, ItemStack> candidates, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) candidates.putIfAbsent(stackKey(stack), stack.copyWithCount(1));
    }

    static List<RecipeHolder<BrewingSyntheticRecipe>> findRuntimeRecipes(
            List<IBrewingRecipe> sources, List<ItemStack> actualInputs, RegistryAccess registries) {
        var matches = new LinkedHashMap<ResourceLocation, RecipeHolder<BrewingSyntheticRecipe>>();
        if (actualInputs == null) return List.of();
        for (var input : actualInputs) for (var reagent : actualInputs) {
            var converted = convertPair(sources, input, reagent);
            if (converted != null && ItemIngredientAllocator.matches(converted.inputs(), actualInputs, 1L))
                matches.putIfAbsent(converted.id(), holder(converted));
        }
        return List.copyOf(matches.values());
    }

    @Nullable private static AdvancedAlloyFurnaceRecipe convertPair(
            List<IBrewingRecipe> sources, ItemStack input, ItemStack reagent) {
        if (input == null || input.isEmpty() || reagent == null || reagent.isEmpty()) return null;
        var inputProbe = input.copyWithCount(1);
        var reagentProbe = reagent.copyWithCount(1);
        ItemStack output = nativeOutput(sources, inputProbe, reagentProbe);
        if (output == null || output.isEmpty() || output.getCount() <= 0) return null;
        long count = (long) output.getCount() * INPUT_BOTTLES;
        if (count > Integer.MAX_VALUE) return null;
        var scaled = output.copyWithCount((int) count);
        var id = new ResourceLocation("useless_mod", "brewing/native_" + hash(
                stackKey(inputProbe) + "\n" + stackKey(reagentProbe) + "\n" + stackKey(scaled) + "|" + count));
        return new AdvancedAlloyFurnaceRecipe(id,
                List.of(new CountedIngredient(DataComponentIngredient.of(true, inputProbe), INPUT_BOTTLES),
                        new CountedIngredient(DataComponentIngredient.of(true, reagentProbe), 1L)),
                List.of(), List.of(scaled), List.of(), AdapterUtils.DEFAULT_ENERGY,
                PotionBrewing.BREWING_TIME_SECONDS * 20, Ingredient.EMPTY, 0,
                AdapterUtils.toMoldIngredient(new ItemStack(Items.BREWING_STAND)), AlloyFurnaceMode.NORMAL);
    }

    /** Same first-nonempty-output rule and unit input count as BrewingRecipeRegistry.getOutput.
     * A stable snapshot avoids querying a partly modified list on the catalogue worker thread.
     */
    private static ItemStack nativeOutput(List<IBrewingRecipe> sources, ItemStack input, ItemStack reagent) {
        for (var source : sources) {
            try {
                var output = source.getOutput(input.copy(), reagent.copy());
                if (output != null && !output.isEmpty()) return output.copy();
            } catch (ConcurrentModificationException busy) { throw busy; }
            catch (RuntimeException invalid) {
                RateLimitedLogger.warn(LOGGER, "Cannot evaluate native brewing recipe {} for {} + {}; not falling through to a different recipe",
                        source.getClass().getName(), input, reagent, invalid);
                return ItemStack.EMPTY;
            }
        }
        return ItemStack.EMPTY;
    }

    private static List<RecipeHolder<BrewingSyntheticRecipe>> matchingRecipes(
            List<RecipeHolder<BrewingSyntheticRecipe>> candidates, Map<Ingredient, Long> available) {
        if (available == null || available.isEmpty()) return List.of();
        return candidates.stream().filter(holder -> {
            var required = new LinkedHashMap<Ingredient, Long>();
            for (var input : holder.value().convertedRecipe().inputs())
                AdapterUtils.mergeIngredient(required, input.ingredient(), input.count());
            return AdapterUtils.matchesRequired(available, required);
        }).toList();
    }

    private static RecipeHolder<BrewingSyntheticRecipe> holder(AdvancedAlloyFurnaceRecipe recipe) {
        return new RecipeHolder<>(recipe.id(), new BrewingSyntheticRecipe(recipe));
    }
    private static String stackKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "|" + NbtCanonical.snbt(stack.getTag());
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("SHA-256 is unavailable", unavailable); }
    }
}
