package com.sorrowmist.useless.content.recipe.adapters.ironsspellbooks;

import appeng.api.stacks.AEKey;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.config.ServerConfigs;
import io.redspace.ironsspellbooks.fluids.PotionFluid;
import io.redspace.ironsspellbooks.item.InkItem;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Generates item-only recipes for the dynamic alchemist-cauldron interactions. */
public final class AlchemistCauldronDynamicRecipeAdapter
        implements IRecipeAdapter<AlchemistCauldronDynamicRecipeAdapter.DynamicRecipe> {
    private static final int POTION_AMOUNT = 250;
    private static final int PROCESS_TIME = 100;
    private static final Item[] POTION_CONTAINERS = {
            Items.POTION,
            Items.SPLASH_POTION,
            Items.LINGERING_POTION
    };

    private volatile PotionBrewing cachedBrewing;
    private volatile boolean cachedCauldronBrewing;
    private volatile List<RecipeHolder<DynamicRecipe>> cachedRecipes = List.of();

    @Override
    public String sourceId() {
        return RecipeSourceIds.IRONS_SPELLBOOKS;
    }

    @Override
    public Class<DynamicRecipe> getRecipeClass() {
        return DynamicRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(ItemRegistry.ALCHEMIST_CAULDRON_BLOCK_ITEM.get());
    }

    @Override
    public List<RecipeHolder<DynamicRecipe>> getGeneratedRecipes(Level level) {
        if (level == null) {
            return List.of();
        }

        PotionBrewing brewing = level.potionBrewing();
        boolean allowCauldronBrewing = ServerConfigs.ALLOW_CAULDRON_BREWING.get();
        if (cachedBrewing == brewing && cachedCauldronBrewing == allowCauldronBrewing) {
            return cachedRecipes;
        }

        synchronized (this) {
            if (cachedBrewing != brewing || cachedCauldronBrewing != allowCauldronBrewing) {
                cachedRecipes = createRecipes(brewing, allowCauldronBrewing);
                cachedBrewing = brewing;
                cachedCauldronBrewing = allowCauldronBrewing;
            }
            return cachedRecipes;
        }
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<DynamicRecipe> holder, Level level) {
        if (holder == null || holder.value() == null
                || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public List<RecipeHolder<DynamicRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        return findMatchingRecipes(level, mergedInputs, mergedFluids, Map.of(), mold, List.of());
    }

    @Override
    public List<RecipeHolder<DynamicRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys,
            @Nullable ItemStack mold,
            List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }

        List<RecipeHolder<DynamicRecipe>> matchingRecipes = new ArrayList<>();
        for (RecipeHolder<DynamicRecipe> holder : getGeneratedRecipes(level)) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value().convertedRecipe();
            boolean matches = actualInputs != null && !actualInputs.isEmpty()
                    ? ItemIngredientAllocator.matches(recipe.inputs(), actualInputs, 1L)
                    : matchesMergedInputs(recipe, mergedInputs);
            if (matches) {
                matchingRecipes.add(holder);
            }
        }
        return List.copyOf(matchingRecipes);
    }

    private static List<RecipeHolder<DynamicRecipe>> createRecipes(
            @Nullable PotionBrewing brewing, boolean allowCauldronBrewing) {
        Map<ResourceLocation, RecipeHolder<DynamicRecipe>> recipes = new LinkedHashMap<>();
        addScrollRecipes(recipes);
        if (allowCauldronBrewing && brewing != null) {
            addPotionRecipes(recipes, brewing);
        }
        return List.copyOf(recipes.values());
    }

    private static void addScrollRecipes(
            Map<ResourceLocation, RecipeHolder<DynamicRecipe>> recipes) {
        ItemStack waterBottle = waterBottle();
        if (waterBottle.isEmpty()) {
            return;
        }

        for (AbstractSpell spell : SpellRegistry.getEnabledSpells()) {
            for (int level = spell.getMinLevel(); level <= spell.getMaxLevel(); level++) {
                SpellRarity rarity = spell.getRarity(level);
                if (rarity == null) {
                    continue;
                }

                ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get());
                ISpellContainer.createScrollContainer(spell, level, scroll);
                ItemStack ink = InkItem.getInkForRarity(rarity).getDefaultInstance();
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                        "useless_mod",
                        "irons_spellbooks/alchemist_cauldron/scroll_"
                                + idPart(spell.getSpellId()) + "_level_" + level);
                addRecipe(recipes, createHolder(id, List.of(
                        new CountedIngredient(exact(waterBottle), 1L),
                        new CountedIngredient(exact(scroll), 1L)),
                        List.of(ink)));
            }
        }
    }

    private static void addPotionRecipes(
            Map<ResourceLocation, RecipeHolder<DynamicRecipe>> recipes,
            PotionBrewing brewing) {
        List<Item> reagents = brewingReagents(brewing);
        List<Holder<Potion>> potions = potionHolders();
        for (Item container : POTION_CONTAINERS) {
            for (Holder<Potion> potion : potions) {
                ItemStack input = PotionContents.createItemStack(container, potion);
                if (PotionFluid.from(input).isEmpty()) {
                    continue;
                }

                for (Item reagent : reagents) {
                    ItemStack reagentStack = reagent.getDefaultInstance();
                    boolean hasPotionMix;
                    try {
                        hasPotionMix = brewing.hasPotionMix(input, reagentStack)
                                || brewing.hasContainerMix(input, reagentStack);
                    } catch (RuntimeException ignored) {
                        continue;
                    }
                    if (!hasPotionMix) {
                        continue;
                    }

                    ItemStack result;
                    try {
                        result = brewing.mix(reagentStack, input);
                    } catch (RuntimeException ignored) {
                        continue;
                    }
                    FluidStack resultFluid = PotionFluid.from(result);
                    if (resultFluid.isEmpty()) {
                        continue;
                    }
                    ItemStack resultItem = PotionFluid.from(resultFluid);
                    if (resultItem.isEmpty()) {
                        continue;
                    }

                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                            "useless_mod",
                            "irons_spellbooks/alchemist_cauldron/potion_"
                                    + idPart(BuiltInRegistries.ITEM.getKey(container)) + "_"
                                    + idPart(BuiltInRegistries.POTION.getKey(potion.value())) + "_"
                                    + idPart(BuiltInRegistries.ITEM.getKey(reagent)));
                    addRecipe(recipes, createHolder(id, List.of(
                            new CountedIngredient(exact(input), 1L),
                            new CountedIngredient(exact(reagentStack), 1L)),
                            List.of(resultItem)));
                }
            }
        }
    }

    private static List<Item> brewingReagents(PotionBrewing brewing) {
        Set<Item> reagents = new LinkedHashSet<>();
        List<Item> registeredItems = BuiltInRegistries.ITEM.stream().toList();
        for (Item container : POTION_CONTAINERS) {
            for (Holder<Potion> potion : potionHolders()) {
                ItemStack input = PotionContents.createItemStack(container, potion);
                for (Item reagent : registeredItems) {
                    ItemStack reagentStack = reagent.getDefaultInstance();
                    try {
                        if (brewing.hasPotionMix(input, reagentStack)
                                || brewing.hasContainerMix(input, reagentStack)) {
                            reagents.add(reagent);
                        }
                    } catch (RuntimeException ignored) {
                        // A broken third-party mix must not prevent other recipes from loading.
                    }
                }
            }
        }
        return List.copyOf(reagents);
    }

    private static List<Holder<Potion>> potionHolders() {
        return BuiltInRegistries.POTION.holders()
                .map(holder -> (Holder<Potion>) holder)
                .toList();
    }

    private static ItemStack waterBottle() {
        ItemStack water = PotionFluid.from(new FluidStack(Fluids.WATER, POTION_AMOUNT));
        return water.isEmpty()
                ? PotionContents.createItemStack(Items.POTION, Potions.WATER)
                : water;
    }

    private static Ingredient exact(ItemStack stack) {
        return DataComponentIngredient.of(true, stack.copyWithCount(1));
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
            ResourceLocation id, List<CountedIngredient> inputs, List<ItemStack> outputs) {
        return new AdvancedAlloyFurnaceRecipe(
                id,
                inputs,
                List.of(),
                List.of(),
                outputs.stream().map(ItemStack::copy).toList(),
                List.of(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(
                        new ItemStack(ItemRegistry.ALCHEMIST_CAULDRON_BLOCK_ITEM.get()))),
                AlloyFurnaceMode.NORMAL);
    }

    private static RecipeHolder<DynamicRecipe> createHolder(
            ResourceLocation id, List<CountedIngredient> inputs, List<ItemStack> outputs) {
        return new RecipeHolder<>(id, new DynamicRecipe(converted(id, inputs, outputs)));
    }

    private static void addRecipe(
            Map<ResourceLocation, RecipeHolder<DynamicRecipe>> recipes,
            RecipeHolder<DynamicRecipe> holder) {
        recipes.putIfAbsent(holder.id(), holder);
    }

    private static String idPart(@Nullable ResourceLocation id) {
        return id == null ? "unknown" : (id.getNamespace() + "_" + id.getPath()).replace('/', '_');
    }

    private static String idPart(String id) {
        return id.replace(':', '_').replace('/', '_');
    }

    /** Recipe-manager payload for interactions owned by PotionBrewing or spell data. */
    public static final class DynamicRecipe implements Recipe<RecipeInput> {
        private final AdvancedAlloyFurnaceRecipe convertedRecipe;

        private DynamicRecipe(AdvancedAlloyFurnaceRecipe convertedRecipe) {
            this.convertedRecipe = convertedRecipe;
        }

        public AdvancedAlloyFurnaceRecipe convertedRecipe() {
            return convertedRecipe;
        }

        @Override
        public boolean matches(RecipeInput input, Level level) {
            return convertedRecipe != null && convertedRecipe.matches(input, level);
        }

        @Override
        public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
            return convertedRecipe == null ? ItemStack.EMPTY : convertedRecipe.assemble(input, registries);
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return convertedRecipe != null && convertedRecipe.canCraftInDimensions(width, height);
        }

        @Override
        public ItemStack getResultItem(HolderLookup.Provider registries) {
            return convertedRecipe == null ? ItemStack.EMPTY : convertedRecipe.getResultItem(registries);
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return convertedRecipe == null ? null : convertedRecipe.getSerializer();
        }

        @Override
        public RecipeType<?> getType() {
            return convertedRecipe == null ? null : convertedRecipe.getType();
        }
    }
}
