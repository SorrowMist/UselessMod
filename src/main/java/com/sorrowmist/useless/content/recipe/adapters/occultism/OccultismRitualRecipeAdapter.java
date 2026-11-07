package com.sorrowmist.useless.content.recipe.adapters.occultism;

import com.sorrowmist.useless.utils.RateLimitedLogger;

import appeng.api.stacks.GenericStack;
import com.klikli_dev.occultism.common.entity.spirit.SpiritEntity;
import com.klikli_dev.occultism.common.item.spirit.BookOfBindingBoundItem;
import com.klikli_dev.occultism.common.item.spirit.MinerSpiritItem;
import com.klikli_dev.occultism.crafting.recipe.RitualRecipe;
import com.klikli_dev.occultism.registry.OccultismRecipes;
import com.klikli_dev.occultism.util.ItemNBTUtil;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.compat.forge1201.recipe.DataComponentIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PatternStackView;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import com.sorrowmist.useless.core.component.RitualBlueprintPentacles;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts native Forge 1.20.1 Occultism rituals into alloy-furnace recipes.
 *
 * <p>Occultism 1.158.0 stores ritual results and spirit data in item NBT. The
 * conversion therefore never touches the 1.21 data-component API: it copies the
 * native result stack, reads {@code spiritName} through {@link ItemNBTUtil}, and
 * writes spawn-egg entity data into the vanilla {@code EntityTag} compound.</p>
 *
 * <p>Rituals whose native progress depends on a sacrificed entity, a player item
 * use, a stored soul shard, or an executed command are rejected. Those conditions
 * have no equivalent in the alloy furnace and must not be silently reduced to an
 * ingredient list.</p>
 */
public final class OccultismRitualRecipeAdapter implements IRecipeAdapter<RitualRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String OCCULTISM = "occultism";

    private static final ResourceLocation CRAFT = id("craft");
    private static final ResourceLocation CRAFT_WITH_SPIRIT_NAME = id("craft_with_spirit_name");
    private static final ResourceLocation CRAFT_MINER_SPIRIT = id("craft_miner_spirit");
    private static final ResourceLocation SUMMON = id("summon");
    private static final ResourceLocation SUMMON_TAMED = id("summon_tamed");
    private static final ResourceLocation SUMMON_SPIRIT_WITH_JOB = id("summon_spirit_with_job");
    private static final ResourceLocation FAMILIAR = id("familiar");
    private static final ResourceLocation SUMMON_WILD_HUNT = id("summon_wild_hunt");
    private static final ResourceLocation SUMMON_WITH_CHANCE_OF_CHICKEN = id("summon_with_chance_of_chicken_tamed");
    private static final ResourceLocation RESURRECT_FAMILIAR = id("resurrect_familiar");
    private static final ResourceLocation COMMAND = id("execute_command");

    /** Ritual types whose native behaviour can be represented with item inputs and item outputs. */
    private static final Set<ResourceLocation> SUPPORTED_TYPES = Set.of(
            CRAFT, CRAFT_WITH_SPIRIT_NAME, CRAFT_MINER_SPIRIT,
            SUMMON, SUMMON_TAMED, SUMMON_SPIRIT_WITH_JOB, FAMILIAR);

    /** Ritual types rejected before any output is guessed. */
    private static final Set<ResourceLocation> EXPLICITLY_REJECTED_TYPES = Set.of(
            SUMMON_WILD_HUNT, SUMMON_WITH_CHANCE_OF_CHICKEN,
            RESURRECT_FAMILIAR, COMMAND);

    /** Root NBT marker read by {@link OccultismSpiritEggHandler} (1.20.1 has no custom-data component). */
    public static final String AUTO_TAME_MARKER = "useless_mod_occultism_auto_tame";
    private static final Set<ResourceLocation> WARNED_SKIPPED_RECIPES = ConcurrentHashMap.newKeySet();

    @Override
    public Class<RitualRecipe> getRecipeClass() {
        return RitualRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null; // The blueprint NBT selects the actual pentacle.
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        if (mold == null || mold.isEmpty() || ModItems.RITUAL_BLUEPRINT == null
                || !mold.is(ModItems.RITUAL_BLUEPRINT.get())) {
            return false;
        }
        RitualBlueprintPentacles pentacles = com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(
                mold, UComponents.RITUAL_BLUEPRINT_PENTACLE.get());
        return pentacles != null && !pentacles.isEmpty();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<RitualRecipe> holder, Level level) {
        if (!isSupported(holder)) {
            return List.of();
        }
        RitualRecipe source = holder.value();
        Converted data = convert(source, null, level);
        return data == null ? List.of() : List.of(createRecipe(holder.id(), source, data));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<RitualRecipe> holder, Level level, List<ItemStack> actualInputs) {
        if (!isSupported(holder)) {
            return List.of();
        }
        RitualRecipe source = holder.value();
        if (!isActivationDynamic(source)) {
            return convertAll(holder, level);
        }

        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>();
        for (ItemStack activation : distinctMatches(actualInputs, source.getActivationItem())) {
            Converted data = convert(source, activation, level);
            if (data != null) {
                recipes.add(createRecipe(variantId(holder.id(), activation), source, data));
            }
        }
        return recipes;
    }

    @Override
    public List<RecipeHolder<RitualRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }
        RitualBlueprintPentacles pentacles = com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.get(
                mold, UComponents.RITUAL_BLUEPRINT_PENTACLE.get());
        if (pentacles == null || pentacles.isEmpty()) {
            return List.of();
        }

        List<RecipeHolder<RitualRecipe>> matches = new ArrayList<>();
        RecipeManager manager = level.getRecipeManager();
        for (RecipeHolder<RitualRecipe> holder : RecipeManagerAccess.getAllRecipesFor(
                manager, OccultismRecipes.RITUAL_TYPE.get())) {
            RitualRecipe source = holder.value();
            if (!isSupported(holder) || !pentacles.contains(source.getPentacleId())) {
                continue;
            }
            Map<Ingredient, Long> requirements = requirements(source, null);
            if (requirements != null && AdapterUtils.matchesRequired(mergedInputs, requirements)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    /** Used by the AE pattern resolver to identify outputs synthesized from the actual activation stack. */
    public static boolean isDynamicComponentRitual(RitualRecipe recipe) {
        return recipe != null && isActivationDynamic(recipe);
    }

    public static Optional<DynamicPatternProfile> findDynamicPatternProfile(
            @Nullable Level level, List<ItemStack> patternInputs, List<ItemStack> patternOutputs) {
        return findDynamicPatternProfileLong(level, PatternStackView.fromLegacy(patternInputs, patternOutputs));
    }

    static Optional<DynamicPatternProfile> findDynamicPatternProfile(
            Iterable<RecipeHolder<RitualRecipe>> recipes,
            List<ItemStack> patternInputs,
            List<ItemStack> patternOutputs) {
        return findDynamicPatternProfileLong(
                recipes, PatternStackView.fromLegacy(patternInputs, patternOutputs));
    }

    public static Optional<DynamicPatternProfile> findDynamicPatternProfileLong(
            @Nullable Level level, PatternStackView pattern) {
        if (level == null) {
            return Optional.empty();
        }
        return findDynamicPatternProfileLong(RecipeManagerAccess.getAllRecipesFor(
                level.getRecipeManager(), OccultismRecipes.RITUAL_TYPE.get()), pattern);
    }

    static Optional<DynamicPatternProfile> findDynamicPatternProfileLong(
            Iterable<RecipeHolder<RitualRecipe>> recipes, PatternStackView pattern) {
        if (recipes == null || pattern == null || pattern.inputs().isEmpty()
                || pattern.outputs().isEmpty()) {
            return Optional.empty();
        }
        if (pattern.inputRepresentatives().size() != pattern.inputs().size()
                || pattern.outputRepresentatives().size() != pattern.outputs().size()) {
            return Optional.empty();
        }

        List<DynamicPatternProfile> matches = new ArrayList<>();
        for (RecipeHolder<RitualRecipe> holder : recipes) {
            if (!isSupported(holder)) {
                continue;
            }
            RitualRecipe source = holder.value();
            Set<Integer> boundBookSlots = boundBookActivationSlots(source, pattern);
            if (isActivationDynamic(source)) {
                if (pattern.outputs().size() == 1) {
                    addDynamicOutputPatternMatch(source, pattern, boundBookSlots, matches);
                }
            } else if (!boundBookSlots.isEmpty()) {
                addBoundBookPatternMatch(source, pattern, boundBookSlots, matches);
            }
            if (matches.size() > 1) {
                return Optional.empty();
            }
        }
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }

    /**
     * Matches a ritual whose output embeds data copied from the activation stack: the miner spirit
     * configuration/name, a {@code craft_with_spirit_name} name, or a summoned spirit's custom name.
     * The initial output must exactly equal the native result for the pattern's activation NBT,
     * at the same multiplier as its inputs. Only after that check is its output slot marked dynamic
     * so runtime execution can bind another valid activation stack without inventing static NBT.
     */
    private static void addDynamicOutputPatternMatch(
            RitualRecipe source, PatternStackView pattern, Set<Integer> boundBookSlots,
            List<DynamicPatternProfile> matches) {
        ItemStack activation = null;
        for (ItemStack input : pattern.inputRepresentatives()) {
            if (!source.getActivationItem().test(input)) continue;
            if (activation != null) return; // ambiguous activation NBT must not be guessed
            activation = input;
        }
        if (activation == null) return;
        Converted data = convert(source, activation, null);
        List<ItemStack> patternOutputs = pattern.outputRepresentatives();
        if (data == null || data.outputs().size() != 1 || patternOutputs.size() != 1
                || pattern.outputs().size() != 1) {
            return;
        }
        if (!matchesStaticOutput(data.outputs().get(0), pattern.outputs().get(0),
                patternOutputs.get(0), patternScale(data.inputs(), pattern))) {
            return;
        }
        if (!matchesPatternInputs(data.inputs(), pattern)) {
            return;
        }
        matches.add(new DynamicPatternProfile(boundBookSlots, Set.of(0)));
    }

    private static void addBoundBookPatternMatch(
            RitualRecipe source, PatternStackView pattern,
            Set<Integer> boundBookSlots, List<DynamicPatternProfile> matches) {
        Converted data = convert(source, null, null);
        if (data == null || !matchesStaticOutputs(data.outputs(), pattern.outputs(),
                pattern.outputRepresentatives(), patternScale(data.inputs(), pattern))
                || !matchesPatternInputs(data.inputs(), pattern)) {
            return;
        }
        matches.add(new DynamicPatternProfile(boundBookSlots, Set.of()));
    }

    /**
     * Bound books always carry a {@code spiritName} in normal gameplay. The recipe activation
     * ingredient itself is item-only, so this marker mainly protects KubeJS recipes that used a
     * stricter NBT ingredient while still relying on the native copy-over behaviour.
     */
    private static Set<Integer> boundBookActivationSlots(
            RitualRecipe source, PatternStackView pattern) {
        Set<Integer> slots = new LinkedHashSet<>();
        Ingredient activation = source.getActivationItem();
        if (activation == null || activation.isEmpty() || !activation.isSimple()) {
            return slots;
        }
        List<ItemStack> patternInputs = pattern.inputRepresentatives();
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

    private static long patternScale(List<CountedIngredient> requirements, PatternStackView pattern) {
        long required = totalRequiredItems(requirements);
        long actual = totalPatternItems(pattern.inputs());
        return required > 0L && actual > 0L && actual % required == 0L ? actual / required : 0L;
    }

    private static boolean matchesPatternInputs(
            List<CountedIngredient> requirements, PatternStackView pattern) {
        long scale = patternScale(requirements, pattern);
        return scale > 0L && ItemIngredientAllocator.matches(
                requirements, List.of(), pattern.inputs(), scale);
    }

    private static boolean matchesOutputItem(
            ItemStack expected, GenericStack patternOutput, ItemStack patternOutputRepresentative, long scale) {
        return scale > 0L && expected != null && !expected.isEmpty()
                && expected.getCount() > 0
                && patternOutput != null && patternOutput.amount() > 0L
                && patternOutputRepresentative != null && !patternOutputRepresentative.isEmpty()
                && expected.is(patternOutputRepresentative.getItem())
                && patternOutput.amount() % expected.getCount() == 0L
                && patternOutput.amount() / expected.getCount() == scale;
    }

    private static boolean matchesStaticOutput(
            ItemStack expected, GenericStack patternOutput, ItemStack patternOutputRepresentative, long scale) {
        return matchesOutputItem(expected, patternOutput, patternOutputRepresentative, scale)
                && ItemStack.isSameItemSameTags(expected, patternOutputRepresentative);
    }

    private static boolean matchesStaticOutputs(
            List<ItemStack> expected, List<GenericStack> patternOutputs,
            List<ItemStack> patternOutputRepresentatives, long scale) {
        if (expected.size() != patternOutputs.size()) {
            return false;
        }
        boolean[] matched = new boolean[patternOutputs.size()];
        for (ItemStack expectedOutput : expected) {
            boolean found = false;
            for (int slot = 0; slot < patternOutputs.size(); slot++) {
                if (!matched[slot] && matchesStaticOutput(
                        expectedOutput, patternOutputs.get(slot), patternOutputRepresentatives.get(slot), scale)) {
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
                return 0L;
            }
            total += requirement.count();
        }
        return total;
    }

    private static long totalPatternItems(List<GenericStack> patternInputs) {
        long total = 0L;
        for (GenericStack input : patternInputs) {
            if (input == null || input.amount() <= 0L) {
                continue;
            }
            if (total > Long.MAX_VALUE - input.amount()) return 0L;
            total += input.amount();
        }
        return total;
    }

    @Nullable
    private static Converted convert(
            RitualRecipe source, @Nullable ItemStack activation, @Nullable Level level) {
        if (source == null || source.getDuration() < 0 || source.getPentacleId() == null) {
            return null;
        }
        Map<Ingredient, Long> requirements = requirements(source, activation);
        if (requirements == null) {
            return null;
        }

        List<ItemStack> outputs = outputs(source, activation, level);
        if (outputs.isEmpty()) {
            return null;
        }
        List<CountedIngredient> inputs = requirements.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
        return new Converted(inputs, requirements, outputs);
    }

    private static List<ItemStack> outputs(
            RitualRecipe source, @Nullable ItemStack activation, @Nullable Level level) {
        ResourceLocation type = source.getRitualType();
        if (isMinerSpiritRecipe(source)) {
            ItemStack output = craftMinerSpirit(source, activation, level);
            return output.isEmpty() ? List.of() : List.of(output);
        }
        if (CRAFT_WITH_SPIRIT_NAME.equals(type)) {
            ItemStack output = craftWithSpiritName(source, activation);
            return output.isEmpty() ? List.of() : List.of(output);
        }
        if (CRAFT.equals(type)) {
            ItemStack output = resultItem(source).copy();
            return output.isEmpty() ? List.of() : List.of(output);
        }
        if (isEntitySummonRitual(source)) {
            ItemStack output = summonEgg(source, activation);
            return output.isEmpty() ? List.of() : List.of(output);
        }
        return List.of();
    }

    @Nullable
    private static Map<Ingredient, Long> requirements(
            RitualRecipe source, @Nullable ItemStack activation) {
        if (source == null || source.getDuration() < 0 || source.getPentacleId() == null) {
            return null;
        }
        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        Ingredient activationIngredient = activation != null
                ? DataComponentIngredient.of(true, activation.copyWithCount(1)) : source.getActivationItem();
        if (!add(requirements, activationIngredient)) {
            return null;
        }
        for (Ingredient ingredient : source.getIngredients()) {
            if (!add(requirements, ingredient)) {
                return null;
            }
        }
        // Native item-use rituals need a player interaction during the ritual; those recipes are
        // rejected in isSupported and must not be turned into a silently consumed ingredient.
        if (source.requiresItemUse()) {
            return null;
        }
        return requirements;
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

    /**
     * Native {@code CraftWithSpiritNameRitual} consumes a copy of the activation stack and copies
     * its {@code spiritName} onto the result. The static display recipe deliberately has no name;
     * the runtime conversion uses the real activation stack.
     */
    private static ItemStack craftWithSpiritName(RitualRecipe source, @Nullable ItemStack activation) {
        ItemStack output = resultItem(source).copy();
        if (activation != null) {
            ItemNBTUtil.setBoundSpiritName(output,
                    ItemNBTUtil.getBoundSpiritName(activation.copy()));
        }
        return output;
    }

    /**
     * Native {@code CraftMinerSpiritRitual} calls {@code onCraftedBy} on the result (which writes
     * the miner configuration NBT in 1.20.1) and copies the activation {@code spiritName} over.
     */
    private static ItemStack craftMinerSpirit(
            RitualRecipe source, @Nullable ItemStack activation, @Nullable Level level) {
        ItemStack output = resultItem(source).copy();
        output.getItem().onCraftedBy(output, level, null);
        if (activation != null) {
            ItemNBTUtil.setBoundSpiritName(output,
                    ItemNBTUtil.getBoundSpiritName(activation.copy()));
        } else if (output.hasTag()) {
            // The static JEI representative must not expose a randomly generated name.
            output.getTag().remove(ItemNBTUtil.SPIRIT_NAME_TAG);
        }
        return output;
    }

    /**
     * Builds a spawn egg for a summoned entity. Native 1.20.1 reads entity data from the item's
     * {@code EntityTag} compound; job spirits store {@code spiritJob.factoryId} and their max age
     * in the same shape as {@link SpiritEntity#addAdditionalSaveData(CompoundTag)}.
     */
    private static ItemStack summonEgg(RitualRecipe source, @Nullable ItemStack activation) {
        EntityType<?> entityType = source.getEntityToSummon();
        if (entityType == null || !isNativeEntitySummon(source)) {
            return ItemStack.EMPTY;
        }
        SpawnEggItem eggItem = spawnEggItem(entityType);
        if (eggItem == null) {
            return ItemStack.EMPTY;
        }

        ItemStack output = new ItemStack(eggItem);
        copySpawnEggPresentation(resultItem(source), output);

        CompoundTag entityData = source.getEntityNbt() == null
                ? new CompoundTag() : source.getEntityNbt().copy();
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
        if (entityId != null) {
            entityData.putString("id", entityId.toString());
        }

        boolean spiritEntity = isSpiritEntityType(entityType);
        if (spiritEntity) {
            entityData.putInt("spiritMaxAge", source.getSpiritMaxAge());
        }
        if (spiritEntity && SUMMON_SPIRIT_WITH_JOB.equals(source.getRitualType())
                && source.getSpiritJobType() != null) {
            CompoundTag job = entityData.contains("spiritJob", Tag.TAG_COMPOUND)
                    ? entityData.getCompound("spiritJob").copy() : new CompoundTag();
            job.putString("factoryId", source.getSpiritJobType().toString());
            entityData.put("spiritJob", job);
        }

        // FamiliarRitual assigns the activation spirit name *after* applying recipe NBT; all
        // SummonRitual subclasses apply recipe NBT after the name and therefore keep an explicit
        // CustomName from the recipe. Reproduce that ordering instead of overwriting either value.
        boolean recipeOverridesName = !FAMILIAR.equals(source.getRitualType())
                && source.getEntityNbt() != null
                && source.getEntityNbt().contains("CustomName", Tag.TAG_STRING);
        if (activation != null) {
            String spiritName = ItemNBTUtil.getBoundSpiritName(activation.copy());
            if (!spiritName.isEmpty()) {
                entityData.putString("CustomName",
                        Component.Serializer.toJson(Component.literal(spiritName)));
            }
        }
        if (recipeOverridesName) {
            entityData.putString("CustomName", source.getEntityNbt().getString("CustomName"));
        }

        output.getOrCreateTag().put("EntityTag", entityData);
        if (isTamedSummon(source)) {
            output.getOrCreateTag().putBoolean(AUTO_TAME_MARKER, true);
        }
        return output;
    }

    /**
     * Package-visible regression helper: builds plain entity eggs using only native 1.20.1 NBT.
     */
    static List<ItemStack> spawnEggOutputs(
            Iterable<EntityType<?>> entityTypes, @Nullable CompoundTag sourceEntityData) {
        List<ItemStack> outputs = new ArrayList<>();
        if (entityTypes == null) {
            return List.of();
        }
        for (EntityType<?> entityType : entityTypes) {
            if (entityType == null) {
                continue;
            }
            SpawnEggItem eggItem = spawnEggItem(entityType);
            if (eggItem == null) {
                continue;
            }
            ItemStack output = new ItemStack(eggItem);
            CompoundTag entityData = sourceEntityData == null
                    ? new CompoundTag() : sourceEntityData.copy();
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            if (entityId != null) {
                entityData.putString("id", entityId.toString());
            }
            output.getOrCreateTag().put("EntityTag", entityData);
            outputs.add(output);
        }
        return List.copyOf(outputs);
    }

    /** Package-visible regression helper for the native job-spirit NBT shape. */
    static void applyJobEntityData(RitualRecipe source, ItemStack result) {
        CompoundTag entityData = source.getEntityNbt() == null
                ? new CompoundTag() : source.getEntityNbt().copy();
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(source.getEntityToSummon());
        if (entityId != null) {
            entityData.putString("id", entityId.toString());
        }
        entityData.putInt("spiritMaxAge", source.getSpiritMaxAge());
        CompoundTag job = entityData.contains("spiritJob", Tag.TAG_COMPOUND)
                ? entityData.getCompound("spiritJob").copy() : new CompoundTag();
        if (source.getSpiritJobType() != null) {
            job.putString("factoryId", source.getSpiritJobType().toString());
        }
        entityData.put("spiritJob", job);
        result.getOrCreateTag().put("EntityTag", entityData);
    }

    @Nullable
    private static SpawnEggItem spawnEggItem(EntityType<?> entityType) {
        if (entityType == null) {
            return null;
        }
        SpawnEggItem item = ForgeSpawnEggItem.fromEntityType(entityType);
        return item != null ? item : SpawnEggItem.byId(entityType);
    }

    private static void copySpawnEggPresentation(@Nullable ItemStack source, ItemStack target) {
        if (source == null || source.isEmpty() || !(source.getItem() instanceof SpawnEggItem)) {
            return;
        }
        CompoundTag display = source.getTagElement("display");
        if (display != null) {
            target.getOrCreateTag().put("display", display.copy());
        }
    }

    private static boolean isSpiritEntityType(EntityType<?> entityType) {
        try {
            return SpiritEntity.class.isAssignableFrom(entityType.getBaseClass());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isCraftRitual(RitualRecipe source) {
        ResourceLocation type = source == null ? null : source.getRitualType();
        return CRAFT.equals(type) || CRAFT_WITH_SPIRIT_NAME.equals(type)
                || CRAFT_MINER_SPIRIT.equals(type);
    }

    private static boolean isEntitySummonRitual(RitualRecipe source) {
        ResourceLocation type = source == null ? null : source.getRitualType();
        return SUMMON.equals(type) || SUMMON_TAMED.equals(type)
                || SUMMON_SPIRIT_WITH_JOB.equals(type) || FAMILIAR.equals(type);
    }

    private static boolean isNativeEntitySummon(RitualRecipe source) {
        return isEntitySummonRitual(source) && source.getEntityToSummon() != null;
    }

    private static boolean isTamedSummon(RitualRecipe source) {
        ResourceLocation type = source == null ? null : source.getRitualType();
        return SUMMON_TAMED.equals(type) || SUMMON_SPIRIT_WITH_JOB.equals(type)
                || FAMILIAR.equals(type);
    }

    /** True for rituals whose output item data is derived from the activation stack. */
    private static boolean isActivationDynamic(RitualRecipe source) {
        if (source == null) {
            return false;
        }
        ResourceLocation type = source.getRitualType();
        return CRAFT_WITH_SPIRIT_NAME.equals(type)
                || isMinerSpiritRecipe(source)
                || isEntitySummonRitual(source);
    }

    private static boolean isSupported(@Nullable RecipeHolder<RitualRecipe> holder) {
        if (holder == null || holder.value() == null) {
            return false;
        }
        RitualRecipe source = holder.value();
        ResourceLocation type = source.getRitualType();
        if (type == null || !SUPPORTED_TYPES.contains(type)) {
            warnSkipped(holder, EXPLICITLY_REJECTED_TYPES.contains(type)
                    ? "requires a native sacrifice/random/command/resurrection condition"
                    : "unknown ritual type " + type);
            return false;
        }
        if (source.getPentacleId() == null) {
            warnSkipped(holder, "has no pentacle id");
            return false;
        }
        if (source.getActivationItem() == null || source.getActivationItem().isEmpty()) {
            warnSkipped(holder, "has no enumerable activation ingredient");
            return false;
        }
        // These are real ritual progress conditions, not extra ingredients. The alloy furnace
        // cannot sacrifice an entity or observe a player's right-click mid-process.
        if (source.requiresSacrifice()) {
            warnSkipped(holder, "requires an entity sacrifice");
            return false;
        }
        if (source.requiresItemUse()) {
            warnSkipped(holder, "requires a player item use");
            return false;
        }

        if (isCraftRitual(source)) {
            ItemStack result = resultItem(source);
            if (result.isEmpty()) {
                warnSkipped(holder, "has no item result");
                return false;
            }
            if (isMinerSpiritRecipe(source) && !(result.getItem() instanceof MinerSpiritItem)) {
                warnSkipped(holder, "miner ritual result is not a native MinerSpiritItem");
                return false;
            }
            return true;
        }

        if (isEntitySummonRitual(source)) {
            if (source.getEntityToSummon() == null) {
                warnSkipped(holder, "summons no entity");
                return false;
            }
            if (SUMMON_SPIRIT_WITH_JOB.equals(type)) {
                if (source.getSpiritJobType() == null) {
                    warnSkipped(holder, "summon_spirit_with_job has no spirit job type");
                    return false;
                }
                if (!isSpiritEntityType(source.getEntityToSummon())) {
                    warnSkipped(holder, "summon_spirit_with_job does not summon a SpiritEntity");
                    return false;
                }
            }
            if (spawnEggItem(source.getEntityToSummon()) == null) {
                warnSkipped(holder, "summoned entity has no native spawn egg item");
                return false;
            }
            return true;
        }
        return false;
    }

    private static void warnSkipped(RecipeHolder<RitualRecipe> holder, String reason) {
        if (WARNED_SKIPPED_RECIPES.add(holder.id())) {
            RateLimitedLogger.warn(LOGGER, "Skipping unsupported Occultism ritual recipe {}: {}",
                    holder.id(), reason);
        }
    }

    /** Static item result, or {@link ItemStack#EMPTY} when the recipe exposes none. */
    private static ItemStack resultItem(RitualRecipe source) {
        try {
            ItemStack result = source.getResultItem(null);
            return result == null ? ItemStack.EMPTY : result;
        } catch (RuntimeException ignored) {
            // getResultItem may need a registry lookup that is not available at this stage.
            return ItemStack.EMPTY;
        }
    }

    private static boolean isMinerSpiritRecipe(RitualRecipe source) {
        if (source == null) {
            return false;
        }
        ResourceLocation type = source.getRitualType();
        if (CRAFT_MINER_SPIRIT.equals(type)) {
            return true;
        }
        // KubeJS re-registers miner rituals under the schema default occultism:craft while the
        // result item still is Occultism's native MinerSpiritItem.
        return CRAFT.equals(type) && isMinerSpiritResult(resultItem(source));
    }

    private static boolean isMinerSpiritResult(ItemStack result) {
        if (result == null || result.isEmpty()) {
            return false;
        }
        try {
            return result.getItem() instanceof MinerSpiritItem;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean add(Map<Ingredient, Long> requirements, @Nullable Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return false;
        }
        AdapterUtils.mergeIngredient(requirements, ingredient, 1L);
        return true;
    }

    static Ingredient blueprintMold(ResourceLocation pentacle) {
        if (ModItems.RITUAL_BLUEPRINT == null) {
            return Ingredient.EMPTY;
        }
        ItemStack blueprint = new ItemStack(ModItems.RITUAL_BLUEPRINT.get());
        com.sorrowmist.useless.compat.forge1201.component.ComponentAccess.set(
                blueprint, UComponents.RITUAL_BLUEPRINT_PENTACLE.get(),
                RitualBlueprintPentacles.of(pentacle));
        return DataComponentIngredient.of(true, blueprint);
    }

    private static List<ItemStack> distinctMatches(List<ItemStack> stacks, Ingredient ingredient) {
        List<ItemStack> matches = new ArrayList<>();
        if (stacks == null || ingredient == null || ingredient.isEmpty()) {
            return matches;
        }
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty() || !ingredient.test(stack)
                    || matches.stream().anyMatch(existing -> ItemStack.isSameItemSameTags(existing, stack))) {
                continue;
            }
            matches.add(stack.copyWithCount(1));
        }
        return matches;
    }

    private static ResourceLocation variantId(ResourceLocation source, ItemStack... stacks) {
        StringBuilder suffix = new StringBuilder();
        for (ItemStack stack : stacks) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            suffix.append('_');
            if (key == null) {
                suffix.append("unregistered");
            } else {
                suffix.append(key.getNamespace()).append('_').append(key.getPath().replace('/', '_'));
            }
            suffix.append('_').append(AdapterUtils.stableHash(stack.hasTag() ? stack.getTag().toString() : ""));
        }
        return new ResourceLocation(source.getNamespace(), source.getPath() + "_converted" + suffix);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(OCCULTISM, path);
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
