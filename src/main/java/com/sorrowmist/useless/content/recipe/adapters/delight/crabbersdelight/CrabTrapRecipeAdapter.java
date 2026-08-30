package com.sorrowmist.useless.content.recipe.adapters.delight.crabbersdelight;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Converts Crabbers Delight's data-driven crab-trap loot into deterministic recipes. */
public final class CrabTrapRecipeAdapter implements IRecipeAdapter<CrabTrapSyntheticRecipe> {
    private static final String MOD_ID = "crabbersdelight";
    private static final ResourceLocation CRAB_TRAP_ID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "crab_trap");
    private static final ResourceLocation CRAB_TRAP_BAIT_TAG =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "crab_trap_bait");
    private static final String DISPLAY_RESULT_TAG_PREFIX = "jei_display_results/";
    private static final int WATER_PER_OUTPUT = 1_000;

    @Override
    public String sourceId() {
        return RecipeSourceIds.CRABBERS_DELIGHT;
    }

    @Override
    public Class<CrabTrapSyntheticRecipe> getRecipeClass() {
        return CrabTrapSyntheticRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        Item trap = registeredItem(CRAB_TRAP_ID);
        return trap == null ? null : trap.getDefaultInstance();
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        Item trap = registeredItem(CRAB_TRAP_ID);
        return trap != null && mold != null && !mold.isEmpty() && mold.is(trap);
    }

    @Override
    public List<RecipeHolder<CrabTrapSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) {
            return List.of();
        }

        // Item tags are rebound on BuiltInRegistries.ITEM on the client when the server sends its
        // tag packet. ClientLevel.registryAccess() may still expose the pre-sync view here.
        Registry<Item> itemRegistry = BuiltInRegistries.ITEM;
        ResourceManager resourceManager = resourceManager(level);

        List<RecipeHolder<CrabTrapSyntheticRecipe>> recipes = new ArrayList<>();
        for (Item bait : baitItems(itemRegistry)) {
            ResourceLocation baitId = itemRegistry.getKey(bait);
            if (baitId == null || baitId.getPath().isBlank()) {
                continue;
            }

            // The JEI display tags intentionally omit some weighted entries (for example,
            // message_bottle), so the actual loot table is the authoritative source for the
            // deterministic all-output conversion.
            JsonObject table = resourceManager == null ? null : readLootTable(resourceManager,
                    ResourceLocation.fromNamespaceAndPath(
                            MOD_ID, "gameplay/crab_trap_loot/" + baitId.getPath()));
            List<ItemStack> fallbackOutputs = table == null
                    ? displayOutputs(itemRegistry, baitId) : List.of();
            AdvancedAlloyFurnaceRecipe converted = table == null
                    ? convertOutputs(itemRegistry, bait, baitId, fallbackOutputs)
                    : convertLootTable(itemRegistry, bait, baitId, table);

            if (converted != null) {
                recipes.add(new RecipeHolder<>(converted.id(),
                        new CrabTrapSyntheticRecipe(converted)));
            }
        }
        return List.copyOf(recipes);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<CrabTrapSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null
                || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<RecipeHolder<CrabTrapSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<net.neoforged.neoforge.fluids.FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        return findMatchingRecipes(level, mergedInputs, mergedFluids, Map.of(), mold, List.of());
    }

    @Override
    public List<RecipeHolder<CrabTrapSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<net.neoforged.neoforge.fluids.FluidStack, Long> mergedFluids,
            Map<appeng.api.stacks.AEKey, Long> mergedKeys,
            @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }

        List<RecipeHolder<CrabTrapSyntheticRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<CrabTrapSyntheticRecipe> holder : getGeneratedRecipes(level)) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value().convertedRecipe();
            if (recipe == null || !recipe.keyInputs().isEmpty()) {
                continue;
            }

            boolean hasConcreteInputs = hasConcreteInputs(actualInputs);
            boolean hasMergedInputs = mergedInputs != null && !mergedInputs.isEmpty();
            if (recipe.inputs().isEmpty() && (hasConcreteInputs || hasMergedInputs)) {
                continue;
            }

            boolean itemsMatch = hasConcreteInputs
                    ? ItemIngredientAllocator.matches(recipe.inputs(), actualInputs, 1L)
                    : matchesMergedInputs(recipe.inputs(), mergedInputs);
            boolean fluidsMatch = FluidIngredientAllocator
                    .matchesLong(recipe.inputFluids(), mergedFluids == null ? Map.of() : mergedFluids, 1L);
            if (itemsMatch && fluidsMatch) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private static List<Item> baitItems(Registry<Item> itemRegistry) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, CRAB_TRAP_BAIT_TAG);
        List<Item> items = new ArrayList<>();
        // The source tag contains AIR for the trap's no-bait mode. Keep it so that mode is
        // represented by a water-only converted recipe.
        for (net.minecraft.core.Holder<Item> holder : itemRegistry.getTagOrEmpty(tag)) {
            Item item = holder.value();
            if (item != null) {
                items.add(item);
            }
        }
        return List.copyOf(items);
    }

    private static List<ItemStack> displayOutputs(Registry<Item> itemRegistry,
                                                   ResourceLocation baitId) {
        TagKey<Item> outputTag = TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(
                        MOD_ID, DISPLAY_RESULT_TAG_PREFIX + baitId.getNamespace()
                                + "/" + baitId.getPath()));
        List<ItemStack> outputs = new ArrayList<>();
        for (var holder : itemRegistry.getTagOrEmpty(outputTag)) {
            Item item = holder.value();
            if (item != null && item != Items.AIR) {
                outputs.add(item.getDefaultInstance());
            }
        }
        return List.copyOf(outputs);
    }

    @Nullable
    private static Item registeredItem(ResourceLocation id) {
        return registeredItem(BuiltInRegistries.ITEM, id);
    }

    @Nullable
    private static Item registeredItem(Registry<Item> itemRegistry, ResourceLocation id) {
        return itemRegistry.getOptional(id).orElse(null);
    }

    @Nullable
    private static ResourceManager resourceManager(Level level) {
        MinecraftServer server = level.getServer();
        if (server != null) {
            return server.getResourceManager();
        }

        // A single-player client can read the integrated server's data resources. A remote client
        // cannot, so it deliberately falls back to the synced JEI output tags above.
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);
            Object integratedServer = minecraftClass.getMethod("getSingleplayerServer")
                    .invoke(minecraft);
            if (integratedServer instanceof MinecraftServer minecraftServer) {
                return minecraftServer.getResourceManager();
            }
        } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
        }
        return null;
    }

    @Nullable
    private static JsonObject readLootTable(ResourceManager resourceManager,
                                             ResourceLocation tableId) {
        ResourceLocation resourceId = ResourceLocation.fromNamespaceAndPath(
                tableId.getNamespace(), "loot_table/" + tableId.getPath() + ".json");
        Optional<Resource> resource = resourceManager.getResource(resourceId);
        if (resource.isEmpty()) {
            return null;
        }

        try (var reader = resource.get().openAsReader()) {
            return GsonHelper.parse(reader);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    static AdvancedAlloyFurnaceRecipe convertLootTable(
            Item bait, ResourceLocation baitId, JsonObject table) {
        return convertLootTable(BuiltInRegistries.ITEM, bait, baitId, table);
    }

    @Nullable
    static AdvancedAlloyFurnaceRecipe convertLootTable(
            Registry<Item> itemRegistry, Item bait, ResourceLocation baitId, JsonObject table) {
        if (bait == null || baitId == null || table == null
                || !isSupportedTable(table)) {
            return null;
        }

        List<ItemStack> outputs = new ArrayList<>();
        JsonArray pools = table.getAsJsonArray("pools");
        for (JsonElement poolElement : pools) {
            JsonObject pool = poolElement.isJsonObject() ? poolElement.getAsJsonObject() : null;
            if (pool == null || !hasNoEntries(pool, "conditions")
                    || !hasNoEntries(pool, "functions")) {
                return null;
            }

            int rolls = fixedInteger(pool.get("rolls"), 1);
            if (rolls < 0) {
                return null;
            }
            if (rolls == 0) {
                continue;
            }
            if (!isZero(pool.get("bonus_rolls"))) {
                return null;
            }

            JsonElement entriesElement = pool.get("entries");
            if (entriesElement == null || !entriesElement.isJsonArray()) {
                return null;
            }
            JsonArray entries = entriesElement.getAsJsonArray();
            for (int roll = 0; roll < rolls; roll++) {
                for (JsonElement entryElement : entries) {
                    JsonObject entry = entryElement.isJsonObject()
                            ? entryElement.getAsJsonObject() : null;
                    if (entry == null || !hasNoEntries(entry, "conditions")
                            || !hasNoEntries(entry, "functions")) {
                        return null;
                    }

                    String type = stringValue(entry.get("type"));
                    if ("minecraft:empty".equals(type)) {
                        continue;
                    }
                    if (!"minecraft:item".equals(type)) {
                        return null;
                    }

                    ResourceLocation itemId = ResourceLocation.tryParse(stringValue(entry.get("name")));
                    Item item = itemId == null ? null : registeredItem(itemRegistry, itemId);
                    if (item == null || item == Items.AIR) {
                        return null;
                    }
                    // Crabbers Delight uses loot weights for random selection. The converted
                    // recipe intentionally makes every item entry deterministic and produces one.
                    outputs.add(item.getDefaultInstance());
                }
            }
        }

        if (outputs.isEmpty()) {
            return null;
        }

        return convertOutputs(itemRegistry, bait, baitId, outputs);
    }

    @Nullable
    static AdvancedAlloyFurnaceRecipe convertOutputs(
            Registry<Item> itemRegistry, Item bait, ResourceLocation baitId,
            List<ItemStack> outputs) {
        if (itemRegistry == null || bait == null || baitId == null
                || outputs == null || outputs.isEmpty()) {
            return null;
        }

        List<ItemStack> normalizedOutputs = new ArrayList<>();
        for (ItemStack output : outputs) {
            if (output != null && !output.isEmpty() && output.getCount() > 0) {
                normalizedOutputs.add(output.copy());
            }
        }
        if (normalizedOutputs.isEmpty()) {
            return null;
        }

        int operations = normalizedOutputs.size();
        long waterAmount = saturatingMultiply(operations, WATER_PER_OUTPUT);
        long energy = saturatingMultiply(AdapterUtils.DEFAULT_ENERGY, operations);
        int processTime = AdapterUtils.safeInt(
                saturatingMultiply(AdapterUtils.DEFAULT_PROCESS_TIME, operations));
        Item trap = registeredItem(CRAB_TRAP_ID);
        if (trap == null) {
            return null;
        }

        List<CountedIngredient> inputs = bait == Items.AIR
                ? List.of()
                : List.of(new CountedIngredient(Ingredient.of(bait.getDefaultInstance()), operations));
        return new AdvancedAlloyFurnaceRecipe(
                recipeId(baitId),
                inputs,
                List.of(new LongSizedFluidIngredient(FluidIngredient.single(Fluids.WATER), waterAmount)),
                List.of(),
                normalizedOutputs,
                List.of(),
                List.of(),
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                List.of(Ingredient.of(trap)),
                AlloyFurnaceMode.NORMAL);
    }

    private static boolean isSupportedTable(JsonObject table) {
        return "minecraft:fishing".equals(stringValue(table.get("type")))
                && hasNoEntries(table, "functions")
                && table.has("pools")
                && table.get("pools").isJsonArray()
                && !table.getAsJsonArray("pools").isEmpty();
    }

    private static boolean hasNoEntries(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || (value.isJsonArray() && value.getAsJsonArray().isEmpty());
    }

    private static boolean isZero(@Nullable JsonElement element) {
        return element == null || (element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isNumber()
                && element.getAsDouble() == 0.0D);
    }

    private static int fixedInteger(@Nullable JsonElement element, int defaultValue) {
        if (element == null) {
            return defaultValue;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return -1;
        }
        double value = element.getAsDouble();
        return Double.isFinite(value) && value >= 0.0D && value <= Integer.MAX_VALUE
                && Math.rint(value) == value ? (int) value : -1;
    }

    private static String stringValue(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : "";
    }

    private static boolean matchesMergedInputs(
            List<CountedIngredient> requirements, Map<Ingredient, Long> mergedInputs) {
        Map<Ingredient, Long> required = new LinkedHashMap<>();
        for (CountedIngredient requirement : requirements) {
            AdapterUtils.mergeIngredient(required, requirement.ingredient(), requirement.count());
        }
        return AdapterUtils.matchesRequired(mergedInputs == null ? Map.of() : mergedInputs, required);
    }

    private static boolean hasConcreteInputs(@Nullable List<ItemStack> inputs) {
        if (inputs == null) {
            return false;
        }
        return inputs.stream().anyMatch(stack -> stack != null && !stack.isEmpty()
                && stack.getCount() > 0);
    }

    private static ResourceLocation recipeId(ResourceLocation baitId) {
        String path = baitId.getNamespace() + "_" + baitId.getPath().replace('/', '_');
        return ResourceLocation.fromNamespaceAndPath(RecipeSourceIds.CRABBERS_DELIGHT,
                "crab_trap_" + path + "_converted");
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }
}
