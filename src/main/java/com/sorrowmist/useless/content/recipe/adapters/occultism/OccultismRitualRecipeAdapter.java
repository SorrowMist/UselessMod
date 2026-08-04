package com.sorrowmist.useless.content.recipe.adapters.occultism;

import com.klikli_dev.occultism.common.item.spirit.BookOfBindingBoundItem;
import com.klikli_dev.occultism.crafting.recipe.RitualRecipe;
import com.klikli_dev.occultism.registry.OccultismDataComponents;
import com.klikli_dev.occultism.registry.OccultismRecipes;
import com.klikli_dev.occultism.registry.OccultismSpiritJobs;
import com.klikli_dev.occultism.util.ItemNBTUtil;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import com.sorrowmist.useless.core.component.RitualBlueprintPentacles;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Converts static Occultism ritual semantics into component-aware alloy-furnace recipes. */
public final class OccultismRitualRecipeAdapter implements IRecipeAdapter<RitualRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String OCCULTISM = "occultism";
    private static final ResourceLocation REPAIR = id("repair");
    private static final ResourceLocation UPGRADE = id("upgrade");
    private static final ResourceLocation UNBREAKABLE = id("unbreakable");
    private static final ResourceLocation CRAFT_MINER_SPIRIT = id("craft_miner_spirit");
    private static final ResourceLocation RESURRECT_FAMILIAR = id("resurrect_familiar");
    private static final ResourceLocation COMMAND = id("execute_command");
    private static final Set<ResourceLocation> MINER_SPIRIT_RECIPE_IDS = Set.of(
            id("craft_miner_foliot_unspecialized"),
            id("craft_miner_djinni_ores"),
            id("craft_miner_afrit_deeps"),
            id("craft_miner_marid_master"),
            id("misc_miner_ancient_eldritch"));
    public static final String AUTO_TAME_MARKER = "useless_mod_occultism_auto_tame";
    private static final Set<ResourceLocation> WARNED_SKIPPED_RECIPES = ConcurrentHashMap.newKeySet();

    @Override
    public Class<RitualRecipe> getRecipeClass() {
        return RitualRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null; // Blueprint components select the actual pentacle.
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        if (mold == null || mold.isEmpty() || ModItems.RITUAL_BLUEPRINT == null
                || !mold.is(ModItems.RITUAL_BLUEPRINT.get())) {
            return false;
        }
        RitualBlueprintPentacles pentacles = mold.get(UComponents.RITUAL_BLUEPRINT_PENTACLE.get());
        return pentacles != null && !pentacles.isEmpty();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<RitualRecipe> holder, Level level) {
        if (!isSupported(holder)) {
            return List.of();
        }
        RitualRecipe source = holder.value();
        if (isRepairLike(source)) {
            return staticRepairRecipes(holder, source);
        }
        if (isUpgrade(source)) {
            return staticUpgradeRecipes(holder, source);
        }
        Converted data = convert(source, null, null, null, level);
        return data == null ? List.of() : List.of(createRecipe(holder.id(), source, data));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<RitualRecipe> holder, Level level, List<ItemStack> actualInputs) {
        if (!isSupported(holder)) {
            return List.of();
        }
        RitualRecipe source = holder.value();
        if (isRepairLike(source)) {
            List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
            for (ItemStack activation : distinctMatches(actualInputs, source.getActivationItem())) {
                if (!activation.isDamageableItem()) {
                    continue;
                }
                Converted data = convert(source, activation, null, null);
                if (data != null) {
                    recipes.add(createRecipe(variantId(holder.id(), activation), source, data));
                }
            }
            return recipes;
        }
        if (isUpgrade(source)) {
            Ingredient baseIngredient = upgradeBaseIngredient(source);
            if (baseIngredient.isEmpty()) {
                return List.of();
            }
            List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
            for (ItemStack base : distinctMatches(actualInputs, baseIngredient)) {
                for (ItemStack activation : distinctMatches(actualInputs, source.getActivationItem())) {
                    Converted data = convert(source, null, base, activation);
                    if (data != null) {
                        recipes.add(createRecipe(variantId(holder.id(), base, activation), source, data));
                    }
                }
            }
            return recipes;
        }
        if (isCraftMinerSpirit(source)) {
            List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
            for (ItemStack activation : distinctMatches(actualInputs, source.getActivationItem())) {
                Converted data = convert(source, activation, null, null, level);
                if (data != null) {
                    recipes.add(createRecipe(variantId(holder.id(), activation), source, data));
                }
            }
            return recipes;
        }
        return convertAll(holder, level);
    }

    @Override
    public List<RecipeHolder<RitualRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }
        RitualBlueprintPentacles pentacles = mold.get(UComponents.RITUAL_BLUEPRINT_PENTACLE.get());
        if (pentacles == null || pentacles.isEmpty()) {
            return List.of();
        }

        List<RecipeHolder<RitualRecipe>> matches = new ArrayList<>();
        RecipeManager manager = level.getRecipeManager();
        for (RecipeHolder<RitualRecipe> holder : manager.getAllRecipesFor(OccultismRecipes.RITUAL_TYPE.get())) {
            RitualRecipe source = holder.value();
            if (!isSupported(holder) || !pentacles.contains(source.getPentacleId())) {
                continue;
            }
            Map<Ingredient, Long> requirements = requirements(source, null, null, null);
            if (requirements != null && AdapterUtils.matchesRequired(mergedInputs, requirements)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    /** Used by the AE pattern resolver to identify outputs generated from actual component-bearing inputs. */
    public static boolean isDynamicComponentRitual(RitualRecipe recipe) {
        return recipe != null && (REPAIR.equals(recipe.getRitualType())
                || UPGRADE.equals(recipe.getRitualType()) || UNBREAKABLE.equals(recipe.getRitualType())
                || isCraftMinerSpirit(recipe));
    }

    public static Optional<DynamicPatternProfile> findDynamicPatternProfile(
            @Nullable Level level, List<ItemStack> patternInputs, List<ItemStack> patternOutputs) {
        if (level == null) {
            return Optional.empty();
        }
        return findDynamicPatternProfile(
                level.getRecipeManager().getAllRecipesFor(OccultismRecipes.RITUAL_TYPE.get()),
                patternInputs,
                patternOutputs);
    }

    static Optional<DynamicPatternProfile> findDynamicPatternProfile(
            Iterable<RecipeHolder<RitualRecipe>> recipes,
            List<ItemStack> patternInputs,
            List<ItemStack> patternOutputs) {
        if (recipes == null || patternInputs == null || patternInputs.isEmpty()
                || patternOutputs == null || patternOutputs.isEmpty()
                || patternOutputs.stream().anyMatch(output -> output == null || output.isEmpty())) {
            return Optional.empty();
        }
        List<DynamicPatternProfile> matches = new ArrayList<>();
        for (RecipeHolder<RitualRecipe> holder : recipes) {
            if (!isSupported(holder)) {
                continue;
            }
            RitualRecipe source = holder.value();
            if (isMinerSpiritRecipe(holder)) {
                if (patternOutputs.size() == 1) {
                    addMinerSpiritPatternMatch(source, patternInputs, patternOutputs.getFirst(), matches);
                }
            } else if (isRepairLike(source)) {
                if (patternOutputs.size() == 1) {
                    addRepairPatternMatch(source, patternInputs, patternOutputs.getFirst(), matches);
                }
            } else if (isUpgrade(source)) {
                Set<Integer> boundBookSlots = boundBookActivationSlots(source, patternInputs);
                if (patternOutputs.size() == 1) {
                    addUpgradePatternMatch(
                            source, patternInputs, patternOutputs.getFirst(), boundBookSlots, matches);
                }
            } else {
                Set<Integer> boundBookSlots = boundBookActivationSlots(source, patternInputs);
                if (!boundBookSlots.isEmpty()) {
                    addBoundBookPatternMatch(
                            source, patternInputs, patternOutputs, boundBookSlots, matches);
                }
            }
            if (matches.size() > 1) {
                return Optional.empty();
            }
        }
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.getFirst());
    }

    private static void addMinerSpiritPatternMatch(
            RitualRecipe source, List<ItemStack> patternInputs, ItemStack patternOutput,
            List<DynamicPatternProfile> matches) {
        Converted data = convert(source, null, null, null);
        if (data == null || data.outputs().size() != 1
                || !matchesOutputItem(data.outputs().getFirst(), patternOutput)
                || !matchesPatternInputs(data.inputs(), patternInputs)) {
            return;
        }

        Set<Integer> activationSlots = new LinkedHashSet<>();
        Ingredient activation = source.getActivationItem();
        for (int slot = 0; slot < patternInputs.size(); slot++) {
            ItemStack input = patternInputs.get(slot);
            if (matchesItemId(activation, input)) {
                activationSlots.add(slot);
            }
        }
        if (activationSlots.size() == 1) {
            matches.add(new DynamicPatternProfile(activationSlots, Set.of(0)));
        }
    }

    private static boolean matchesItemId(@Nullable Ingredient ingredient, @Nullable ItemStack stack) {
        if (ingredient == null || ingredient.isEmpty() || stack == null || stack.isEmpty()) {
            return false;
        }
        for (ItemStack option : ingredient.getItems()) {
            if (option != null && !option.isEmpty() && option.is(stack.getItem())) {
                return true;
            }
        }
        // Custom ingredients do not necessarily expose enumerable item stacks. Their own test
        // remains the only safe fallback, while all five built-in miner rituals use simple item
        // ingredients and therefore take the component-agnostic branch above.
        return ingredient.getCustomIngredient() != null && ingredient.test(stack);
    }

    private static void addRepairPatternMatch(
            RitualRecipe source, List<ItemStack> patternInputs, ItemStack patternOutput,
            List<DynamicPatternProfile> matches) {
        for (int slot = 0; slot < patternInputs.size(); slot++) {
            ItemStack activation = patternInputs.get(slot);
            if (activation == null || activation.isEmpty() || !activation.isDamageableItem()
                    || !source.getActivationItem().test(activation)
                    || !activation.is(patternOutput.getItem())) {
                continue;
            }
            Converted data = convert(source, activation, null, null);
            if (data != null && matchesPatternInputs(data.inputs(), patternInputs)
                    && data.outputs().size() == 1
                    && matchesOutputItem(data.outputs().getFirst(), patternOutput)) {
                matches.add(new DynamicPatternProfile(Set.of(slot), Set.of(0)));
            }
        }
    }

    private static void addUpgradePatternMatch(
            RitualRecipe source, List<ItemStack> patternInputs, ItemStack patternOutput,
            Set<Integer> boundBookSlots,
            List<DynamicPatternProfile> matches) {
        Ingredient baseIngredient = upgradeBaseIngredient(source);
        for (int slot = 0; slot < patternInputs.size(); slot++) {
            ItemStack base = patternInputs.get(slot);
            if (base == null || base.isEmpty() || !baseIngredient.test(base)) {
                continue;
            }
            Converted data = convert(source, null, base, null);
            if (data != null && data.outputs().size() == 1
                    && matchesOutputItem(data.outputs().getFirst(), patternOutput)
                    && matchesPatternInputs(data.inputs(), patternInputs)) {
                Set<Integer> idOnlyInputs = new LinkedHashSet<>(boundBookSlots);
                idOnlyInputs.add(slot);
                matches.add(new DynamicPatternProfile(idOnlyInputs, Set.of(0)));
            }
        }
    }

    private static void addBoundBookPatternMatch(
            RitualRecipe source, List<ItemStack> patternInputs, List<ItemStack> patternOutputs,
            Set<Integer> boundBookSlots, List<DynamicPatternProfile> matches) {
        Converted data = convert(source, null, null, null);
        if (data == null || !matchesStaticOutputs(data.outputs(), patternOutputs)
                || !matchesPatternInputs(data.inputs(), patternInputs)) {
            return;
        }
        matches.add(new DynamicPatternProfile(boundBookSlots, Set.of()));
    }

    private static Set<Integer> boundBookActivationSlots(
            RitualRecipe source, List<ItemStack> patternInputs) {
        Set<Integer> slots = new LinkedHashSet<>();
        Ingredient activation = source.getActivationItem();
        if (activation.getCustomIngredient() != null) {
            return slots;
        }
        for (int slot = 0; slot < patternInputs.size(); slot++) {
            ItemStack input = patternInputs.get(slot);
            if (input != null && !input.isEmpty()
                    && input.getItem() instanceof BookOfBindingBoundItem
                    && activation.test(input)) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private static boolean matchesPatternInputs(
            List<CountedIngredient> requirements, List<ItemStack> patternInputs) {
        return totalRequiredItems(requirements) == totalPatternItems(patternInputs)
                && ItemIngredientAllocator.matches(requirements, patternInputs, 1L);
    }

    private static boolean matchesOutputItem(ItemStack expected, ItemStack patternOutput) {
        return expected != null && !expected.isEmpty()
                && patternOutput != null && !patternOutput.isEmpty()
                && expected.is(patternOutput.getItem())
                && expected.getCount() == patternOutput.getCount();
    }

    private static boolean matchesStaticOutput(ItemStack expected, ItemStack patternOutput) {
        return matchesOutputItem(expected, patternOutput)
                && ItemStack.isSameItemSameComponents(expected, patternOutput);
    }

    private static boolean matchesStaticOutputs(
            List<ItemStack> expected, List<ItemStack> patternOutputs) {
        if (expected.size() != patternOutputs.size()) {
            return false;
        }
        boolean[] matched = new boolean[patternOutputs.size()];
        for (ItemStack expectedOutput : expected) {
            boolean found = false;
            for (int slot = 0; slot < patternOutputs.size(); slot++) {
                if (!matched[slot] && matchesStaticOutput(expectedOutput, patternOutputs.get(slot))) {
                    matched[slot] = true;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static long totalRequiredItems(List<CountedIngredient> requirements) {
        long total = 0L;
        for (CountedIngredient requirement : requirements) {
            if (requirement == null || requirement.count() <= 0) {
                continue;
            }
            if (total > Long.MAX_VALUE - requirement.count()) {
                return Long.MAX_VALUE;
            }
            total += requirement.count();
        }
        return total;
    }

    private static long totalPatternItems(List<ItemStack> patternInputs) {
        long total = 0L;
        for (ItemStack input : patternInputs) {
            if (input == null || input.isEmpty() || input.getCount() <= 0) {
                continue;
            }
            total += input.getCount();
        }
        return total;
    }

    @Nullable
    private static Converted convert(
            RitualRecipe source, @Nullable ItemStack repairActivation, @Nullable ItemStack upgradeBase,
            @Nullable ItemStack upgradeActivation) {
        return convert(source, repairActivation, upgradeBase, upgradeActivation, null);
    }

    @Nullable
    private static Converted convert(
            RitualRecipe source, @Nullable ItemStack repairActivation, @Nullable ItemStack upgradeBase,
            @Nullable ItemStack upgradeActivation, @Nullable Level level) {
        if (source == null || source.getDuration() < 0 || source.getPentacleId() == null) {
            return null;
        }
        Map<Ingredient, Long> requirements = requirements(
                source, repairActivation, upgradeBase, upgradeActivation);
        if (requirements == null) {
            return null;
        }

        List<ItemStack> outputs;
        if (REPAIR.equals(source.getRitualType())) {
            ItemStack output = repairActivation == null ? ItemStack.EMPTY : repair(repairActivation);
            outputs = output.isEmpty() ? List.of() : List.of(output);
        } else if (UNBREAKABLE.equals(source.getRitualType())) {
            ItemStack output = repairActivation == null ? ItemStack.EMPTY : makeUnbreakable(repairActivation);
            outputs = output.isEmpty() ? List.of() : List.of(output);
        } else if (UPGRADE.equals(source.getRitualType())) {
            ItemStack output = upgradeBase == null ? ItemStack.EMPTY : upgrade(source, upgradeBase, upgradeActivation);
            outputs = output.isEmpty() ? List.of() : List.of(output);
        } else if (isCraftMinerSpirit(source)) {
            ItemStack output = craftMinerSpirit(source, repairActivation, level);
            outputs = output.isEmpty() ? List.of() : List.of(output);
        } else if (source.getEntityTagToSummon() != null) {
            outputs = randomSummonEggs(source);
        } else {
            ItemStack output = source.getResultItem(null).copy();
            if (source.getSpiritJobType() != null) {
                output = jobEgg(source, output);
            }
            outputs = output.isEmpty() ? List.of() : List.of(output);
        }
        if (outputs.isEmpty()) {
            return null;
        }
        List<CountedIngredient> inputs = requirements.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
        return new Converted(inputs, requirements, outputs);
    }

    @Nullable
    private static Map<Ingredient, Long> requirements(
            RitualRecipe source, @Nullable ItemStack repairActivation, @Nullable ItemStack upgradeBase,
            @Nullable ItemStack upgradeActivation) {
        if (source == null || source.getDuration() < 0 || source.getPentacleId() == null) {
            return null;
        }
        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        Ingredient activation = repairActivation != null
                ? exact(repairActivation)
                : upgradeActivation != null ? exact(upgradeActivation) : source.getActivationItem();
        if (!add(requirements, activation)) {
            return null;
        }
        List<Ingredient> ingredients = source.getIngredients();
        for (int index = 0; index < ingredients.size(); index++) {
            Ingredient ingredient = index == 0 && upgradeBase != null ? exact(upgradeBase) : ingredients.get(index);
            if (!add(requirements, ingredient)) {
                return null;
            }
        }
        if (source.requiresItemUse() && !add(requirements, source.getItemToUse())) {
            return null;
        }
        return requirements;
    }

    private static List<AdvancedAlloyFurnaceRecipe> staticRepairRecipes(
            RecipeHolder<RitualRecipe> holder, RitualRecipe source) {
        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
        for (ItemStack candidate : distinctDamageable(source.getActivationItem())) {
            Converted data = convert(source, candidate, null, null);
            if (data != null) {
                recipes.add(createRecipe(variantId(holder.id(), candidate), source, data));
            }
        }
        return recipes;
    }

    private static List<AdvancedAlloyFurnaceRecipe> staticUpgradeRecipes(
            RecipeHolder<RitualRecipe> holder, RitualRecipe source) {
        Ingredient baseIngredient = upgradeBaseIngredient(source);
        if (baseIngredient.isEmpty()) {
            return List.of();
        }
        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
        for (ItemStack base : distinct(baseIngredient.getItems())) {
            Converted data = convert(source, null, base, null);
            if (data != null) {
                recipes.add(createRecipe(variantId(holder.id(), base), source, data));
            }
        }
        return recipes;
    }

    private static AdvancedAlloyFurnaceRecipe createRecipe(
            ResourceLocation id, RitualRecipe source, Converted data) {
        return new AdvancedAlloyFurnaceRecipe(
                id,
                data.inputs(),
                List.of(),
                data.outputs().stream().map(ItemStack::copy).toList(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                Math.max(1, source.getDuration()),
                Ingredient.EMPTY,
                0,
                blueprintMold(source.getPentacleId()),
                AlloyFurnaceMode.NORMAL
        );
    }

    private static List<ItemStack> randomSummonEggs(RitualRecipe source) {
        List<EntityType<?>> entityTypes = new ArrayList<>();
        for (var holder : BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(source.getEntityTagToSummon())) {
            entityTypes.add(holder.value());
        }
        entityTypes.sort(Comparator.comparing(
                type -> BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()));
        return spawnEggOutputs(entityTypes, source.getEntityNbt());
    }

    static List<ItemStack> spawnEggOutputs(
            Iterable<EntityType<?>> entityTypes, @Nullable CompoundTag sourceEntityData) {
        List<ItemStack> outputs = new ArrayList<>();
        for (EntityType<?> entityType : entityTypes) {
            if (entityType == null) {
                continue;
            }
            SpawnEggItem spawnEgg = SpawnEggItem.byId(entityType);
            if (spawnEgg == null) {
                spawnEgg = SpawnEggItem.byId(EntityType.PIG);
            }
            if (spawnEgg == null) {
                continue;
            }
            ItemStack output = new ItemStack(spawnEgg);
            if (spawnEgg.getType(output) != entityType || sourceEntityData != null) {
                CompoundTag entityData = sourceEntityData == null
                        ? new CompoundTag() : sourceEntityData.copy();
                entityData.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString());
                output.set(DataComponents.ENTITY_DATA, CustomData.of(entityData));
            }
            outputs.add(output);
        }
        return List.copyOf(outputs);
    }

    private static ItemStack jobEgg(RitualRecipe source, ItemStack result) {
        if (!(result.getItem() instanceof SpawnEggItem egg) || source.getEntityToSummon() == null
                || egg.getType(result) != source.getEntityToSummon()
                || OccultismSpiritJobs.REGISTRY.get(source.getSpiritJobType()) == null) {
            return ItemStack.EMPTY;
        }
        applyJobEntityData(source, result);

        CompoundTag marker = result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        marker.putBoolean(AUTO_TAME_MARKER, true);
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        return result;
    }

    static void applyJobEntityData(RitualRecipe source, ItemStack result) {
        CompoundTag entityData = source.getEntityNbt() == null ? new CompoundTag() : source.getEntityNbt().copy();
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(source.getEntityToSummon());
        entityData.putString("id", entityId.toString());
        entityData.putInt("spiritMaxAge", source.getSpiritMaxAge());
        CompoundTag job = new CompoundTag();
        job.putString("factoryId", source.getSpiritJobType().toString());
        entityData.put("spiritJob", job);
        result.set(DataComponents.ENTITY_DATA, CustomData.of(entityData));
    }

    private static ItemStack repair(ItemStack source) {
        ItemStack output = source.copyWithCount(1);
        output.setDamageValue(0);
        return output;
    }

    private static ItemStack makeUnbreakable(ItemStack source) {
        ItemStack output = repair(source);
        output.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        if (output.isEnchanted()) {
            EnchantmentHelper.updateEnchantments(output, enchantments -> enchantments.removeIf(entry ->
                    entry.is(Enchantments.UNBREAKING) || entry.is(Enchantments.MENDING)));
        }
        output.set(DataComponents.RARITY, Rarity.EPIC);
        output.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.empty()
                .append(ChatFormatting.OBFUSCATED + "nice   " + ChatFormatting.RESET)
                .append(output.getHoverName())
                .append(ChatFormatting.OBFUSCATED + "   ecin" + ChatFormatting.RESET));
        return output;
    }

    private static ItemStack upgrade(RitualRecipe source, ItemStack base, @Nullable ItemStack activation) {
        ItemStack output = source.getResultItem(null).copy();
        Rarity rarity = output.getRarity();
        Integer targetMaxDamage = output.has(DataComponents.MAX_DAMAGE) ? output.getMaxDamage() : null;
        output.applyComponents(base.getComponents());
        if (targetMaxDamage != null) {
            output.applyComponents(DataComponentMap.builder()
                    .set(DataComponents.MAX_DAMAGE, targetMaxDamage)
                    .build());
        }
        if (activation != null && activation.has(OccultismDataComponents.SPIRIT_NAME)) {
            ItemNBTUtil.setBoundSpiritName(output, ItemNBTUtil.getBoundSpiritName(activation));
        }
        output.set(DataComponents.RARITY, rarity);
        return output;
    }

    private static ItemStack craftMinerSpirit(
            RitualRecipe source, @Nullable ItemStack activation, @Nullable Level level) {
        ItemStack output = source.getResultItem(null).copy();
        output.getItem().onCraftedBy(output, level, null);
        if (activation == null) {
            // The static JEI representative must not contain a generated spirit name. The
            // runtime ritual adds the name after configuring the miner from the activation item.
            output.remove(OccultismDataComponents.SPIRIT_NAME);
        } else {
            // CraftMinerSpiritRitual works on a copy of the activation item. Keep the real input
            // stack untouched, including the case where Occultism generates a name for an input
            // which did not already carry SPIRIT_NAME.
            ItemStack activationCopy = activation.copy();
            ItemNBTUtil.setBoundSpiritName(output, ItemNBTUtil.getBoundSpiritName(activationCopy));
        }
        return output;
    }

    private static boolean isSupported(@Nullable RecipeHolder<RitualRecipe> holder) {
        if (holder == null || holder.value() == null) {
            return false;
        }
        ResourceLocation type = holder.value().getRitualType();
        if (RESURRECT_FAMILIAR.equals(type) || COMMAND.equals(type)) {
            warnSkipped(holder, "requires source entity or command state");
            return false;
        }
        RitualRecipe source = holder.value();
        if (source.getActivationItem() == null || source.getActivationItem().isEmpty()) {
            warnSkipped(holder, "has no enumerable activation ingredient");
            return false;
        }
        if (isRepairLike(source) && distinctDamageable(source.getActivationItem()).isEmpty()) {
            warnSkipped(holder, "has no enumerable damageable activation item");
            return false;
        }
        if (isUpgrade(source) && (upgradeBaseIngredient(source).isEmpty()
                || distinct(upgradeBaseIngredient(source).getItems()).isEmpty())) {
            warnSkipped(holder, "has no enumerable upgrade base item");
            return false;
        }
        return true;
    }

    private static void warnSkipped(RecipeHolder<RitualRecipe> holder, String reason) {
        if (WARNED_SKIPPED_RECIPES.add(holder.id())) {
            LOGGER.warn("Skipping unsupported Occultism ritual recipe {}: {}", holder.id(), reason);
        }
    }

    private static boolean isRepairLike(RitualRecipe source) {
        return REPAIR.equals(source.getRitualType()) || UNBREAKABLE.equals(source.getRitualType());
    }

    private static boolean isUpgrade(RitualRecipe source) {
        return UPGRADE.equals(source.getRitualType());
    }

    private static boolean isCraftMinerSpirit(RitualRecipe source) {
        return source != null && CRAFT_MINER_SPIRIT.equals(source.getRitualType());
    }

    private static boolean isMinerSpiritRecipe(RecipeHolder<RitualRecipe> holder) {
        return holder != null && MINER_SPIRIT_RECIPE_IDS.contains(holder.id())
                && isCraftMinerSpirit(holder.value());
    }

    private static Ingredient upgradeBaseIngredient(RitualRecipe source) {
        return source.getIngredients().isEmpty() ? Ingredient.EMPTY : source.getIngredients().getFirst();
    }

    private static boolean add(Map<Ingredient, Long> requirements, @Nullable Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return false;
        }
        AdapterUtils.mergeIngredient(requirements, ingredient, 1L);
        return true;
    }

    private static Ingredient exact(ItemStack stack) {
        return DataComponentIngredient.of(true, stack.copyWithCount(1));
    }

    private static Ingredient blueprintMold(ResourceLocation pentacle) {
        if (ModItems.RITUAL_BLUEPRINT == null) {
            return Ingredient.EMPTY;
        }
        ItemStack blueprint = new ItemStack(ModItems.RITUAL_BLUEPRINT.get());
        blueprint.set(UComponents.RITUAL_BLUEPRINT_PENTACLE.get(), RitualBlueprintPentacles.of(pentacle));
        return DataComponentIngredient.of(true, blueprint);
    }

    private static List<ItemStack> distinctDamageable(Ingredient ingredient) {
        return distinct(ingredient.getItems()).stream().filter(ItemStack::isDamageableItem).toList();
    }

    private static List<ItemStack> distinctMatches(List<ItemStack> stacks, Ingredient ingredient) {
        List<ItemStack> matches = new ArrayList<>();
        if (stacks == null || ingredient == null || ingredient.isEmpty()) {
            return matches;
        }
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty() || !ingredient.test(stack)
                    || matches.stream().anyMatch(existing -> ItemStack.isSameItemSameComponents(existing, stack))) {
                continue;
            }
            matches.add(stack.copyWithCount(1));
        }
        return matches;
    }

    private static List<ItemStack> distinct(ItemStack[] stacks) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()
                    || result.stream().anyMatch(existing -> ItemStack.isSameItemSameComponents(existing, stack))) {
                continue;
            }
            result.add(stack.copyWithCount(1));
        }
        return result;
    }

    private static ResourceLocation variantId(ResourceLocation source, ItemStack... stacks) {
        StringBuilder suffix = new StringBuilder();
        for (ItemStack stack : stacks) {
            ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
            suffix.append('_').append(key.getNamespace()).append('_').append(key.getPath().replace('/', '_'));
        }
        return ResourceLocation.fromNamespaceAndPath(source.getNamespace(), source.getPath() + "_converted" + suffix);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(OCCULTISM, path);
    }

    private record Converted(
            List<CountedIngredient> inputs,
            Map<Ingredient, Long> requirements,
            List<ItemStack> outputs) {
        private Converted {
            outputs = outputs.stream().map(ItemStack::copy).toList();
        }
    }

    public record DynamicPatternProfile(Set<Integer> idOnlyInputSlots, Set<Integer> idOnlyOutputSlots) {
        public DynamicPatternProfile {
            idOnlyInputSlots = Set.copyOf(idOnlyInputSlots);
            idOnlyOutputSlots = Set.copyOf(idOnlyOutputSlots);
        }
    }
}
