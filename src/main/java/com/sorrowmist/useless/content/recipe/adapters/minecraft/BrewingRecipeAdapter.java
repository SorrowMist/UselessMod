package com.sorrowmist.useless.content.recipe.adapters.minecraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.brewing.BrewingRecipe;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts brewing-stand recipes into fixed three-bottle alloy-furnace batches. */
public final class BrewingRecipeAdapter implements IRecipeAdapter<BrewingSyntheticRecipe> {
    private static final int INPUT_BOTTLES = 3;
    private static final int BREWING_TIME_TICKS = PotionBrewing.BREWING_TIME_SECONDS * 20;
    private static final Item[] POTION_CONTAINERS = {
            Items.POTION,
            Items.SPLASH_POTION,
            Items.LINGERING_POTION
    };

    private volatile PotionBrewing cachedBrewing;
    private volatile List<RecipeHolder<BrewingSyntheticRecipe>> cachedRecipes = List.of();

    @Override
    public Class<BrewingSyntheticRecipe> getRecipeClass() {
        return BrewingSyntheticRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(Items.BREWING_STAND);
    }

    @Override
    public List<RecipeHolder<BrewingSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null || level.potionBrewing() == null) {
            return List.of();
        }

        PotionBrewing brewing = level.potionBrewing();
        if (cachedBrewing == brewing) {
            return cachedRecipes;
        }

        synchronized (this) {
            if (cachedBrewing != brewing) {
                cachedRecipes = createStaticRecipes(
                        brewing, level.registryAccess(), level.enabledFeatures());
                cachedBrewing = brewing;
            }
            return cachedRecipes;
        }
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<BrewingSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null
                || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<RecipeHolder<BrewingSyntheticRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }
        return matchingStaticRecipes(getGeneratedRecipes(level), mergedInputs, List.of());
    }

    @Override
    public List<RecipeHolder<BrewingSyntheticRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            Map<appeng.api.stacks.AEKey, Long> mergedKeys,
            @Nullable ItemStack mold,
            List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }

        PotionBrewing brewing = level.potionBrewing();
        if (brewing != null && actualInputs != null && !actualInputs.isEmpty()) {
            List<RecipeHolder<BrewingSyntheticRecipe>> runtime = findRuntimeRecipes(
                    brewing, actualInputs, level.registryAccess());
            if (!runtime.isEmpty()) {
                return runtime;
            }
        }

        return matchingStaticRecipes(getGeneratedRecipes(level), mergedInputs, actualInputs);
    }

    static List<RecipeHolder<BrewingSyntheticRecipe>> createStaticRecipes(
            PotionBrewing brewing, RegistryAccess registryAccess, FeatureFlagSet enabledFeatures) {
        if (brewing == null || registryAccess == null) {
            return List.of();
        }

        Map<ResourceLocation, RecipeHolder<BrewingSyntheticRecipe>> recipes = new LinkedHashMap<>();
        RecordingBuilder recording = new RecordingBuilder(enabledFeatures);
        PotionBrewing.addVanillaMixes(recording);

        List<Holder<Potion>> potions = potionHolders();
        List<Item> registeredItems = BuiltInRegistries.ITEM.stream().toList();
        for (CapturedPotionMix mix : recording.potionMixes) {
            for (Item container : POTION_CONTAINERS) {
                ItemStack input = PotionContents.createItemStack(container, mix.input());
                ItemStack output = scaleOutput(PotionContents.createItemStack(container, mix.output()));
                addRecipe(recipes, holder(converted(
                        recipeId("potion_mix", registryAccess,
                                DataComponentIngredient.of(true, input), mix.ingredient(), output),
                        DataComponentIngredient.of(true, input), mix.ingredient(), output)));
            }
        }

        for (CapturedContainerMix mix : recording.containerMixes) {
            for (Holder<Potion> potion : potions) {
                ItemStack input = PotionContents.createItemStack(mix.input(), potion);
                ItemStack output = scaleOutput(PotionContents.createItemStack(mix.output(), potion));
                addRecipe(recipes, holder(converted(
                        recipeId("container_mix", registryAccess,
                                DataComponentIngredient.of(true, input),
                                Ingredient.of(mix.ingredient()), output),
                        DataComponentIngredient.of(true, input), Ingredient.of(mix.ingredient()), output)));
            }
        }

        for (Item container : POTION_CONTAINERS) {
            for (Holder<Potion> potion : potions) {
                ItemStack input = PotionContents.createItemStack(container, potion);
                addRegisteredPotionMixes(recipes, brewing, registryAccess, input, registeredItems);
                addRegisteredContainerMixes(recipes, brewing, registryAccess, input, registeredItems);
            }
        }

        for (IBrewingRecipe source : brewing.getRecipes()) {
            if (!(source instanceof BrewingRecipe simple)) {
                continue;
            }
            AdvancedAlloyFurnaceRecipe converted = convertStaticRecipe(simple, registryAccess);
            if (converted != null) {
                addRecipe(recipes, new RecipeHolder<>(
                        converted.id(), new BrewingSyntheticRecipe(converted)));
            }
        }
        return List.copyOf(recipes.values());
    }

    private static void addRegisteredPotionMixes(
            Map<ResourceLocation, RecipeHolder<BrewingSyntheticRecipe>> recipes,
            PotionBrewing brewing, RegistryAccess registryAccess, ItemStack input,
            List<Item> registeredItems) {
        for (Item reagent : registeredItems) {
            ItemStack reagentStack = new ItemStack(reagent);
            if (!brewing.hasPotionMix(input, reagentStack)) {
                continue;
            }
            ItemStack output = scaleOutput(brewing.mix(reagentStack, input));
            if (output.isEmpty()) {
                continue;
            }
            Ingredient inputIngredient = DataComponentIngredient.of(true, input);
            addRecipe(recipes, holder(converted(
                    recipeId("potion_mix", registryAccess, inputIngredient,
                            Ingredient.of(reagent), output),
                    inputIngredient, Ingredient.of(reagent), output)));
        }
    }

    private static void addRegisteredContainerMixes(
            Map<ResourceLocation, RecipeHolder<BrewingSyntheticRecipe>> recipes,
            PotionBrewing brewing, RegistryAccess registryAccess, ItemStack input,
            List<Item> registeredItems) {
        for (Item reagent : registeredItems) {
            ItemStack reagentStack = new ItemStack(reagent);
            if (!brewing.hasContainerMix(input, reagentStack)) {
                continue;
            }
            ItemStack output = scaleOutput(brewing.mix(reagentStack, input));
            if (output.isEmpty()) {
                continue;
            }
            Ingredient inputIngredient = DataComponentIngredient.of(true, input);
            addRecipe(recipes, holder(converted(
                    recipeId("container_mix", registryAccess, inputIngredient,
                            Ingredient.of(reagent), output),
                    inputIngredient, Ingredient.of(reagent), output)));
        }
    }

    private static List<Holder<Potion>> potionHolders() {
        return BuiltInRegistries.POTION.holders().map(holder -> (Holder<Potion>) holder).toList();
    }

    private static @Nullable AdvancedAlloyFurnaceRecipe convertStaticRecipe(
            BrewingRecipe source, RegistryAccess registryAccess) {
        Ingredient input = source.getInput();
        Ingredient ingredient = source.getIngredient();
        ItemStack output = source.getOutput();
        if (input == null || ingredient == null || output == null || output.isEmpty()) {
            return null;
        }

        ItemStack scaledOutput = scaleOutput(output);
        if (scaledOutput.isEmpty()) {
            return null;
        }
        return converted(
                recipeId("brewing_recipe", registryAccess, input, ingredient, scaledOutput),
                input, ingredient, scaledOutput);
    }

    static List<RecipeHolder<BrewingSyntheticRecipe>> findRuntimeRecipes(
            PotionBrewing brewing, List<ItemStack> actualInputs, RegistryAccess registryAccess) {
        Map<ResourceLocation, RecipeHolder<BrewingSyntheticRecipe>> matches = new LinkedHashMap<>();
        for (ItemStack input : actualInputs) {
            if (input == null || input.isEmpty()) {
                continue;
            }
            ItemStack inputProbe = input.copyWithCount(1);
            for (ItemStack ingredient : actualInputs) {
                if (ingredient == null || ingredient.isEmpty()) {
                    continue;
                }
                ItemStack ingredientProbe = ingredient.copyWithCount(1);
                ItemStack output;
                try {
                    if (!brewing.hasMix(inputProbe, ingredientProbe)) {
                        continue;
                    }
                    output = brewing.mix(ingredientProbe, inputProbe);
                } catch (RuntimeException ignored) {
                    continue;
                }
                if (output == null || output.isEmpty()) {
                    continue;
                }

                ItemStack scaledOutput = scaleOutput(output);
                if (scaledOutput.isEmpty()) {
                    continue;
                }
                Ingredient inputIngredient = DataComponentIngredient.of(true, inputProbe);
                Ingredient materialIngredient = DataComponentIngredient.of(true, ingredientProbe);
                AdvancedAlloyFurnaceRecipe converted = converted(
                        recipeId("runtime", registryAccess, inputIngredient,
                                materialIngredient, scaledOutput),
                        inputIngredient, materialIngredient, scaledOutput);
                if (ItemIngredientAllocator.matches(converted.inputs(), actualInputs, 1L)) {
                    matches.putIfAbsent(converted.id(), new RecipeHolder<>(
                            converted.id(), new BrewingSyntheticRecipe(converted)));
                }
            }
        }
        return List.copyOf(matches.values());
    }

    private static List<RecipeHolder<BrewingSyntheticRecipe>> matchingStaticRecipes(
            List<RecipeHolder<BrewingSyntheticRecipe>> candidates,
            @Nullable Map<Ingredient, Long> mergedInputs,
            @Nullable List<ItemStack> actualInputs) {
        if ((actualInputs == null || actualInputs.isEmpty())
                && (mergedInputs == null || mergedInputs.isEmpty())) {
            return List.of();
        }

        List<RecipeHolder<BrewingSyntheticRecipe>> result = new ArrayList<>();
        for (RecipeHolder<BrewingSyntheticRecipe> holder : candidates) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value().convertedRecipe();
            boolean matchesRecipe = actualInputs != null && !actualInputs.isEmpty()
                    ? ItemIngredientAllocator.matches(recipe.inputs(), actualInputs, 1L)
                    : matchesMergedInputs(recipe, mergedInputs);
            if (matchesRecipe) {
                result.add(holder);
            }
        }
        return result;
    }

    private static boolean matchesMergedInputs(
            AdvancedAlloyFurnaceRecipe recipe, @Nullable Map<Ingredient, Long> mergedInputs) {
        if (mergedInputs == null || mergedInputs.isEmpty()) {
            return false;
        }
        Map<Ingredient, Long> required = new LinkedHashMap<>();
        for (CountedIngredient input : recipe.inputs()) {
            AdapterUtils.mergeIngredient(required, input.ingredient(), input.count());
        }
        return AdapterUtils.matchesRequired(mergedInputs, required);
    }

    private static AdvancedAlloyFurnaceRecipe converted(
            ResourceLocation id, Ingredient input, Ingredient ingredient, ItemStack output) {
        return new AdvancedAlloyFurnaceRecipe(
                id,
                List.of(new CountedIngredient(input, INPUT_BOTTLES),
                        new CountedIngredient(ingredient, 1L)),
                List.of(),
                List.of(output.copy()),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                BREWING_TIME_TICKS,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(new ItemStack(Items.BREWING_STAND)),
                AlloyFurnaceMode.NORMAL);
    }

    private static void addRecipe(
            Map<ResourceLocation, RecipeHolder<BrewingSyntheticRecipe>> recipes,
            @Nullable RecipeHolder<BrewingSyntheticRecipe> holder) {
        if (holder != null) {
            recipes.putIfAbsent(holder.id(), holder);
        }
    }

    private static RecipeHolder<BrewingSyntheticRecipe> holder(
            AdvancedAlloyFurnaceRecipe converted) {
        return new RecipeHolder<>(converted.id(), new BrewingSyntheticRecipe(converted));
    }

    private static ItemStack scaleOutput(ItemStack output) {
        if (output == null || output.isEmpty() || output.getCount() <= 0) {
            return ItemStack.EMPTY;
        }
        long count = (long) output.getCount() * INPUT_BOTTLES;
        return count > Integer.MAX_VALUE ? ItemStack.EMPTY : output.copyWithCount((int) count);
    }

    private static ResourceLocation recipeId(
            String kind, RegistryAccess registryAccess, Ingredient input,
            Ingredient ingredient, ItemStack output) {
        String signature = kind + "\n"
                + encodeIngredient(registryAccess, input) + "\n"
                + encodeIngredient(registryAccess, ingredient) + "\n"
                + encodeStack(registryAccess, output);
        return ResourceLocation.fromNamespaceAndPath(
                "useless_mod", "brewing/" + kind + "_" + hash(signature));
    }

    private static String encodeIngredient(RegistryAccess registryAccess, Ingredient ingredient) {
        try {
            JsonElement encoded = Ingredient.CODEC
                    .encodeStart(registryAccess.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), ingredient)
                    .getOrThrow();
            return canonicalize(encoded).toString();
        } catch (RuntimeException exception) {
            return ingredient.toString();
        }
    }

    private static String encodeStack(RegistryAccess registryAccess, ItemStack stack) {
        try {
            JsonElement encoded = ItemStack.CODEC
                    .encodeStart(registryAccess.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), stack)
                    .getOrThrow();
            return canonicalize(encoded).toString();
        } catch (RuntimeException exception) {
            ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            return (itemId == null ? "unknown" : itemId.toString()) + "|" + stack.getComponents();
        }
    }

    private static JsonElement canonicalize(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject object = new JsonObject();
            element.getAsJsonObject().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> object.add(entry.getKey(), canonicalize(entry.getValue())));
            return object;
        }
        if (element.isJsonArray()) {
            JsonArray array = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                array.add(canonicalize(child));
            }
            return array;
        }
        return element;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static final class RecordingBuilder extends PotionBrewing.Builder {
        private final FeatureFlagSet enabledFeatures;
        private final List<CapturedPotionMix> potionMixes = new ArrayList<>();
        private final List<CapturedContainerMix> containerMixes = new ArrayList<>();

        private RecordingBuilder(FeatureFlagSet enabledFeatures) {
            super(enabledFeatures);
            this.enabledFeatures = enabledFeatures;
        }

        @Override
        public void addMix(Holder<Potion> input, Item reagent, Holder<Potion> output) {
            if (input.value().isEnabled(enabledFeatures)
                    && reagent.isEnabled(enabledFeatures)
                    && output.value().isEnabled(enabledFeatures)) {
                potionMixes.add(new CapturedPotionMix(input, Ingredient.of(reagent), output));
            }
            super.addMix(input, reagent, output);
        }

        @Override
        public void addContainerRecipe(Item input, Item reagent, Item output) {
            if (input.isEnabled(enabledFeatures)
                    && reagent.isEnabled(enabledFeatures)
                    && output.isEnabled(enabledFeatures)) {
                containerMixes.add(new CapturedContainerMix(input, reagent, output));
            }
            super.addContainerRecipe(input, reagent, output);
        }
    }

    private record CapturedPotionMix(
            Holder<Potion> input, Ingredient ingredient, Holder<Potion> output) {
    }

    private record CapturedContainerMix(Item input, Item ingredient, Item output) {
    }
}
