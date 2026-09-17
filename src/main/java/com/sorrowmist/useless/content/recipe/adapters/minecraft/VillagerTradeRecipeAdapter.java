package com.sorrowmist.useless.content.recipe.adapters.minecraft;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import com.sorrowmist.useless.init.ModItems;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts the active vanilla villager trade tables into alloy-furnace recipes. */
public final class VillagerTradeRecipeAdapter
        implements IRecipeAdapter<VillagerTradeSyntheticRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RECIPE_NAMESPACE = "useless_mod";
    private static final int MIN_VILLAGER_LEVEL = 1;
    private static final int MAX_VILLAGER_LEVEL = 5;

    private volatile Cached cached;

    @Override
    public Class<VillagerTradeSyntheticRecipe> getRecipeClass() {
        return VillagerTradeSyntheticRecipe.class;
    }

    /** The generated villager recipes use both the spawn egg and the matching job block. */
    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        if (mold == null || mold.isEmpty()) {
            return false;
        }
        if (mold.is(Items.VILLAGER_SPAWN_EGG)) {
            return true;
        }
        return registeredProfessions().stream()
                .flatMap(profession -> profession.workstations().stream())
                .anyMatch(workstation -> mold.is(workstation));
    }

    /**
     * Builds the profession/workstation pairs from the live registries. This also covers
     * professions added by mods whose POI is registered after the vanilla professions.
     */
    private static List<ProfessionWorkstation> registeredProfessions() {
        List<ProfessionWorkstation> professions = new ArrayList<>();
        for (VillagerProfession profession : BuiltInRegistries.VILLAGER_PROFESSION) {
            List<Item> workstations = BuiltInRegistries.POINT_OF_INTEREST_TYPE.holders()
                    .filter(profession.heldJobSite())
                    .flatMap(holder -> holder.value().matchingStates().stream())
                    .map(state -> state.getBlock().asItem())
                    .filter(item -> item != Items.AIR)
                    .distinct()
                    .sorted(Comparator.comparing(VillagerTradeRecipeAdapter::itemKey))
                    .toList();
            professions.add(new ProfessionWorkstation(profession, workstations));
        }
        professions.sort(Comparator.comparing(
                profession -> professionKey(profession.profession())));
        return List.copyOf(professions);
    }

    private static String professionKey(VillagerProfession profession) {
        net.minecraft.resources.ResourceLocation id =
                BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        return id == null ? profession.toString() : id.toString();
    }

    private static String itemKey(Item item) {
        net.minecraft.resources.ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? item.toString() : id.toString();
    }

    /** Keeps the villager spawn egg and all valid workstation blocks as two mold slots. */
    private static List<Ingredient> villagerMolds(ProfessionWorkstation profession) {
        Ingredient workstation = Ingredient.of(profession.workstations().stream()
                .map(Item::getDefaultInstance));
        return List.of(
                AdapterUtils.toMoldIngredient(new ItemStack(Items.VILLAGER_SPAWN_EGG)),
                workstation);
    }

    @Override
    public List<RecipeHolder<VillagerTradeSyntheticRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) {
            return List.of();
        }

        boolean experimental = level.enabledFeatures()
                .contains(net.minecraft.world.flag.FeatureFlags.TRADE_REBALANCE);
        int tradeTableSignature = tradeTableSignature(experimental);
        Cached snapshot = cached;
        if (snapshot != null && snapshot.level() == level
                && snapshot.experimental() == experimental
                && snapshot.tradeTableSignature() == tradeTableSignature) {
            return snapshot.recipes();
        }

        synchronized (this) {
            snapshot = cached;
            if (snapshot == null || snapshot.level() != level
                    || snapshot.experimental() != experimental
                    || snapshot.tradeTableSignature() != tradeTableSignature) {
                snapshot = new Cached(level, experimental, tradeTableSignature,
                        buildRecipes(level, experimental));
                cached = snapshot;
            }
            return snapshot.recipes();
        }
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<VillagerTradeSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null
                || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<RecipeHolder<VillagerTradeSyntheticRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<net.neoforged.neoforge.fluids.FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        return findMatchingRecipes(level, mergedInputs, mergedFluids, Map.of(), mold, List.of());
    }

    @Override
    public List<RecipeHolder<VillagerTradeSyntheticRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<net.neoforged.neoforge.fluids.FluidStack, Long> mergedFluids,
            Map<appeng.api.stacks.AEKey, Long> mergedKeys,
            @Nullable ItemStack mold,
            List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }

        List<RecipeHolder<VillagerTradeSyntheticRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<VillagerTradeSyntheticRecipe> holder : getGeneratedRecipes(level)) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value().convertedRecipe();
            // A regular furnace has one mold slot. Dual-mold villager trades are exposed through
            // the multiblock mold hub and must not be returned as single-mold matches here.
            if (recipe == null || recipe.molds().size() != 1
                    || !AdapterUtils.matchesMold(recipe.mold(), mold)) {
                continue;
            }

            boolean matchesRecipe = actualInputs != null && !actualInputs.isEmpty()
                    ? ItemIngredientAllocator.matches(recipe.inputs(), actualInputs, 1L)
                    : matchesMergedInputs(recipe, mergedInputs);
            if (matchesRecipe) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    private static List<RecipeHolder<VillagerTradeSyntheticRecipe>> buildRecipes(
            Level level, boolean experimental) {
        Map<net.minecraft.resources.ResourceLocation,
                RecipeHolder<VillagerTradeSyntheticRecipe>> recipes = new LinkedHashMap<>();

        addSupplementalVillagerRecipe(recipes);

        for (ProfessionWorkstation profession : registeredProfessions()) {
            if (profession.workstations().isEmpty()) {
                continue;
            }
            for (int villagerLevel = MIN_VILLAGER_LEVEL;
                    villagerLevel <= MAX_VILLAGER_LEVEL; villagerLevel++) {
                VillagerTrades.ItemListing[] listings = listingsFor(
                        profession.profession(), villagerLevel, experimental);
                if (listings == null) {
                    continue;
                }

                for (int listingIndex = 0; listingIndex < listings.length; listingIndex++) {
                    VillagerTrades.ItemListing listing = listings[listingIndex];
                    if (listing == null) {
                        continue;
                    }

                    for (TradeContext context : contextsFor(listing)) {
                        try {
                            MerchantOffer offer = createOffer(
                                    level, profession.profession(), villagerLevel,
                                    listing, context.villagerType());
                            if (offer == null) {
                                continue;
                            }

                            AdvancedAlloyFurnaceRecipe converted = convertOffer(
                                    recipeId(profession.profession(), villagerLevel, listingIndex,
                                            context.villagerType(), experimental),
                                    offer, villagerMolds(profession));
                            if (converted != null) {
                                RecipeHolder<VillagerTradeSyntheticRecipe> holder =
                                        new RecipeHolder<>(converted.id(),
                                                new VillagerTradeSyntheticRecipe(converted));
                                recipes.putIfAbsent(holder.id(), holder);
                            }
                        } catch (RuntimeException exception) {
                            LOGGER.debug("Skipping villager trade: profession={}, level={}, index={}",
                                    profession.profession(), villagerLevel, listingIndex, exception);
                        }
                    }
                }
            }
        }

        addWanderingTraderRecipes(level, experimental, recipes);
        return List.copyOf(recipes.values());
    }

    private static void addWanderingTraderRecipes(
            Level level,
            boolean experimental,
            Map<net.minecraft.resources.ResourceLocation,
                    RecipeHolder<VillagerTradeSyntheticRecipe>> recipes) {
        if (experimental) {
            int groupIndex = 0;
            for (Pair<VillagerTrades.ItemListing[], Integer> group
                    : VillagerTrades.EXPERIMENTAL_WANDERING_TRADER_TRADES) {
                addWanderingTraderListings(level, experimental, groupIndex++,
                        group.getLeft(), recipes);
            }
            return;
        }

        List<Int2ObjectMap.Entry<VillagerTrades.ItemListing[]>> groups =
                new ArrayList<>(VillagerTrades.WANDERING_TRADER_TRADES.int2ObjectEntrySet());
        groups.sort(Comparator.comparingInt(Int2ObjectMap.Entry::getIntKey));
        for (Int2ObjectMap.Entry<VillagerTrades.ItemListing[]> group : groups) {
            addWanderingTraderListings(level, false, group.getIntKey(),
                    group.getValue(), recipes);
        }
    }

    private static void addWanderingTraderListings(
            Level level,
            boolean experimental,
            int groupIndex,
            @Nullable VillagerTrades.ItemListing[] listings,
            Map<net.minecraft.resources.ResourceLocation,
                    RecipeHolder<VillagerTradeSyntheticRecipe>> recipes) {
        if (listings == null) {
            return;
        }
        for (int listingIndex = 0; listingIndex < listings.length; listingIndex++) {
            VillagerTrades.ItemListing listing = listings[listingIndex];
            if (listing == null) {
                continue;
            }
            try {
                MerchantOffer offer = createWanderingTraderOffer(level, listing);
                AdvancedAlloyFurnaceRecipe converted = convertOffer(
                        wanderingRecipeId(experimental, groupIndex, listingIndex), offer,
                        List.of(AdapterUtils.toMoldIngredient(
                                new ItemStack(Items.WANDERING_TRADER_SPAWN_EGG))));
                if (converted != null) {
                    RecipeHolder<VillagerTradeSyntheticRecipe> holder =
                            new RecipeHolder<>(converted.id(), new VillagerTradeSyntheticRecipe(converted));
                    recipes.putIfAbsent(holder.id(), holder);
                }
            } catch (RuntimeException exception) {
                LOGGER.debug("Skipping wandering trader trade: group={}, index={}",
                        groupIndex, listingIndex, exception);
            }
        }
    }

    private static void addSupplementalVillagerRecipe(
            Map<net.minecraft.resources.ResourceLocation,
                    RecipeHolder<VillagerTradeSyntheticRecipe>> recipes) {
        net.minecraft.resources.ResourceLocation id =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        RECIPE_NAMESPACE, "villager_trade/supplemental/tier_1_useless_ingot_to_dirt");
        AdvancedAlloyFurnaceRecipe converted = new AdvancedAlloyFurnaceRecipe(
                id,
                List.of(new CountedIngredient(
                        Ingredient.of(ModItems.USELESS_INGOT_TIER_1.get()), 1L)),
                List.of(),
                List.of(),
                List.of(new ItemStack(Items.DIRT)),
                List.of(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(new ItemStack(Items.VILLAGER_SPAWN_EGG))),
                AlloyFurnaceMode.NORMAL);
        recipes.putIfAbsent(id,
                new RecipeHolder<>(id, new VillagerTradeSyntheticRecipe(converted)));
    }

    @Nullable
    private static VillagerTrades.ItemListing[] listingsFor(
            VillagerProfession profession, int villagerLevel, boolean experimental) {
        var tradeLevels = experimental
                ? VillagerTrades.EXPERIMENTAL_TRADES.get(profession)
                : null;
        if (tradeLevels == null) {
            tradeLevels = VillagerTrades.TRADES.get(profession);
        }
        return tradeLevels == null ? null : tradeLevels.get(villagerLevel);
    }

    private static List<TradeContext> contextsFor(VillagerTrades.ItemListing listing) {
        if (listing instanceof VillagerTrades.TypeSpecificTrade typeSpecific) {
            return typeSpecific.trades().keySet().stream()
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(VillagerType::toString))
                    .map(TradeContext::new)
                    .toList();
        }
        if (listing instanceof VillagerTrades.EmeraldsForVillagerTypeItem) {
            return BuiltInRegistries.VILLAGER_TYPE.stream()
                    .sorted(Comparator.comparing(VillagerType::toString))
                    .map(TradeContext::new)
                    .toList();
        }
        return List.of(new TradeContext(null));
    }

    @Nullable
    private static MerchantOffer createOffer(
            Level level,
            VillagerProfession profession,
            int villagerLevel,
            VillagerTrades.ItemListing listing,
            @Nullable VillagerType villagerType) {
        Villager villager = EntityType.VILLAGER.create(level);
        if (villager == null) {
            return null;
        }

        VillagerType effectiveType = villagerType == null
                ? VillagerType.PLAINS : villagerType;
        villager.setVillagerData(new VillagerData(effectiveType, profession, villagerLevel));
        // Vanilla listings generate some base prices from a random range. Feeding them a source
        // that always returns the lower bound gives the minimum configured emerald cost.
        return listing.getOffer(villager, new MinimumRandomSource());
    }

    @Nullable
    private static MerchantOffer createWanderingTraderOffer(
            Level level, VillagerTrades.ItemListing listing) {
        WanderingTrader trader = EntityType.WANDERING_TRADER.create(level);
        return trader == null ? null : listing.getOffer(trader, new MinimumRandomSource());
    }

    private static int tradeTableSignature(boolean experimental) {
        int signature = experimental ? 1 : 0;
        for (ProfessionWorkstation profession : registeredProfessions()) {
            signature = appendProfessionSignature(signature, profession);
            for (int villagerLevel = MIN_VILLAGER_LEVEL;
                    villagerLevel <= MAX_VILLAGER_LEVEL; villagerLevel++) {
                VillagerTrades.ItemListing[] listings = listingsFor(
                        profession.profession(), villagerLevel, experimental);
                signature = 31 * signature + villagerLevel;
                signature = appendListingSignature(signature, listings);
            }
        }

        if (experimental) {
            int groupIndex = 0;
            for (Pair<VillagerTrades.ItemListing[], Integer> group
                    : VillagerTrades.EXPERIMENTAL_WANDERING_TRADER_TRADES) {
                signature = 31 * signature + groupIndex++;
                signature = 31 * signature + group.getRight();
                signature = appendListingSignature(signature, group.getLeft());
            }
        } else {
            List<Int2ObjectMap.Entry<VillagerTrades.ItemListing[]>> groups =
                    new ArrayList<>(VillagerTrades.WANDERING_TRADER_TRADES.int2ObjectEntrySet());
            groups.sort(Comparator.comparingInt(Int2ObjectMap.Entry::getIntKey));
            for (Int2ObjectMap.Entry<VillagerTrades.ItemListing[]> group : groups) {
                signature = 31 * signature + group.getIntKey();
                signature = appendListingSignature(signature, group.getValue());
            }
        }
        return signature;
    }

    private static int appendProfessionSignature(
            int signature, ProfessionWorkstation profession) {
        signature = 31 * signature + professionKey(profession.profession()).hashCode();
        for (Item workstation : profession.workstations()) {
            signature = 31 * signature + itemKey(workstation).hashCode();
        }
        return signature;
    }

    private static int appendListingSignature(
            int signature, @Nullable VillagerTrades.ItemListing[] listings) {
        if (listings == null) {
            return 31 * signature;
        }
        signature = 31 * signature + listings.length;
        for (VillagerTrades.ItemListing listing : listings) {
            signature = 31 * signature + System.identityHashCode(listing);
        }
        return signature;
    }

    @Nullable
    private static AdvancedAlloyFurnaceRecipe convertOffer(
            net.minecraft.resources.ResourceLocation id,
            MerchantOffer offer,
            List<Ingredient> moldIngredients) {
        if (offer == null || offer.getResult() == null || offer.getResult().isEmpty()) {
            return null;
        }

        List<CountedIngredient> inputs = offerInputs(offer);
        if (inputs.isEmpty()) {
            return null;
        }

        List<Ingredient> molds = new ArrayList<>();
        if (moldIngredients != null) {
            for (Ingredient moldIngredient : moldIngredients) {
                if (moldIngredient != null && !moldIngredient.isEmpty()) {
                    molds.add(moldIngredient);
                }
            }
        }
        if (molds.isEmpty()) {
            return null;
        }

        return new AdvancedAlloyFurnaceRecipe(
                id,
                inputs,
                List.of(),
                List.of(),
                List.of(offer.getResult().copy()),
                List.of(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                molds,
                AlloyFurnaceMode.NORMAL);
    }

    private static List<CountedIngredient> offerInputs(MerchantOffer offer) {
        Map<Ingredient, Long> inputs = new LinkedHashMap<>();

        // baseCostA is the unmodified, minimum cost. getCostA() includes demand and discounts.
        addCost(inputs, offer.getBaseCostA());
        offer.getItemCostB().map(ItemCost::itemStack)
                .ifPresent(cost -> addCost(inputs, cost));

        return inputs.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isEmpty()
                        && entry.getValue() != null && entry.getValue() > 0L)
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static void addCost(Map<Ingredient, Long> inputs, @Nullable ItemStack cost) {
        if (cost == null || cost.isEmpty() || cost.getCount() <= 0) {
            return;
        }
        Ingredient ingredient = DataComponentIngredient.of(true, cost.copyWithCount(1));
        AdapterUtils.mergeIngredient(inputs, ingredient, cost.getCount());
    }

    private static boolean matchesMergedInputs(
            AdvancedAlloyFurnaceRecipe recipe, @Nullable Map<Ingredient, Long> mergedInputs) {
        if (mergedInputs == null || mergedInputs.isEmpty()) {
            return false;
        }
        Map<Ingredient, Long> required = new LinkedHashMap<>();
        for (CountedIngredient input : recipe.inputs()) {
            if (input != null && input.ingredient() != null && input.count() > 0L) {
                AdapterUtils.mergeIngredient(required, input.ingredient(), input.count());
            }
        }
        return ItemIngredientAllocator.matches(mergedInputs, required);
    }

    private static net.minecraft.resources.ResourceLocation recipeId(
            VillagerProfession profession,
            int villagerLevel,
            int listingIndex,
            @Nullable VillagerType villagerType,
            boolean experimental) {
        net.minecraft.resources.ResourceLocation professionId =
                net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        String professionPart;
        if (professionId == null) {
            professionPart = profession.toString();
        } else if ("minecraft".equals(professionId.getNamespace())) {
            professionPart = professionId.getPath();
        } else {
            // Include the namespace so different mods can use the same profession path safely.
            professionPart = professionId.getNamespace() + "_" + professionId.getPath();
        }
        String path = "villager_trade/"
                + (experimental ? "experimental" : "vanilla") + "/"
                + pathPart(professionPart) + "/level_" + villagerLevel
                + "/trade_" + listingIndex;
        if (villagerType != null) {
            path += "/type_" + pathPart(villagerType.toString());
        }
        path += "/mold_spawn_egg_and_workstation";
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                RECIPE_NAMESPACE, path);
    }

    private static net.minecraft.resources.ResourceLocation wanderingRecipeId(
            boolean experimental, int groupIndex, int listingIndex) {
        String path = "villager_trade/"
                + (experimental ? "experimental" : "vanilla")
                + "/wandering_trader/group_" + groupIndex
                + "/trade_" + listingIndex + "/mold_spawn_egg";
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                RECIPE_NAMESPACE, path);
    }

    private static String pathPart(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = Character.toLowerCase(value.charAt(index));
            result.append(character >= 'a' && character <= 'z'
                    || character >= '0' && character <= '9'
                    || character == '_' || character == '.' || character == '-'
                    ? character : '_');
        }
        return result.isEmpty() ? "unknown" : result.toString();
    }

    private record ProfessionWorkstation(VillagerProfession profession, List<Item> workstations) {
    }

    private record TradeContext(@Nullable VillagerType villagerType) {
    }

    private record Cached(
            Level level,
            boolean experimental,
            int tradeTableSignature,
            List<RecipeHolder<VillagerTradeSyntheticRecipe>> recipes) {
    }

    /** Random source used to evaluate the lower endpoint of vanilla random trade prices. */
    private static final class MinimumRandomSource implements RandomSource {
        @Override
        public RandomSource fork() {
            return new MinimumRandomSource();
        }

        @Override
        public PositionalRandomFactory forkPositional() {
            return new MinimumPositionalRandomFactory();
        }

        @Override
        public void setSeed(long seed) {
        }

        @Override
        public int nextInt() {
            return 0;
        }

        @Override
        public int nextInt(int bound) {
            if (bound <= 0) {
                throw new IllegalArgumentException("bound must be positive");
            }
            return 0;
        }

        @Override
        public long nextLong() {
            return 0L;
        }

        @Override
        public boolean nextBoolean() {
            return false;
        }

        @Override
        public float nextFloat() {
            return 0.0F;
        }

        @Override
        public double nextDouble() {
            return 0.0D;
        }

        @Override
        public double nextGaussian() {
            return 0.0D;
        }
    }

    private static final class MinimumPositionalRandomFactory implements PositionalRandomFactory {
        @Override
        public RandomSource fromHashOf(String name) {
            return new MinimumRandomSource();
        }

        @Override
        public RandomSource fromSeed(long seed) {
            return new MinimumRandomSource();
        }

        @Override
        public RandomSource at(int x, int y, int z) {
            return new MinimumRandomSource();
        }

        @Override
        public void parityConfigString(StringBuilder builder) {
            builder.append("MinimumRandomSource");
        }
    }
}
