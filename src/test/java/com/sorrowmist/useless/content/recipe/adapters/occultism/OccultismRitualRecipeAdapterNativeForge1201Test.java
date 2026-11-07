package com.sorrowmist.useless.content.recipe.adapters.occultism;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.klikli_dev.occultism.common.blockentity.DimensionalMineshaftBlockEntity;
import com.klikli_dev.occultism.common.item.spirit.MinerSpiritItem;
import com.klikli_dev.occultism.crafting.recipe.RitualRecipe;
import com.klikli_dev.occultism.util.ItemNBTUtil;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PatternStackView;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Forge 1.20.1 native-shape tests for Occultism 1.158.0.
 *
 * <p>The fixtures use the real {@link RitualRecipe} constructor and the real
 * {@link ItemNBTUtil} / spawn-egg NBT conventions. No 1.21 data-component classes are
 * referenced by the adapter or by this regression.</p>
 */
class OccultismRitualRecipeAdapterNativeForge1201Test {
    private static final ResourceLocation ID = id("test_ritual");
    private static final ResourceLocation PENTACLE = id("craft_foliot");
    private MockedStatic<OccultismRitualRecipeAdapter> moldFixture;

    @BeforeEach void substituteOnlyTheUnregisteredUselessModBlueprint() {
        moldFixture = Mockito.mockStatic(OccultismRitualRecipeAdapter.class, call ->
                call.getMethod().getName().equals("blueprintMold")
                        ? Ingredient.of(Items.PAPER) : call.callRealMethod());
    }

    @AfterEach void closeMoldFixture() { moldFixture.close(); }

    @BeforeAll
    static void bootstrapMinecraft() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void staticCraftKeepsResultCountAndNbtAndRequiresEveryNativeIngredient() {
        ItemStack result = tagged(Items.DIAMOND, 3, "marker", "native");
        RitualRecipe source = recipe("craft", result,
                Ingredient.of(Items.BOOK),
                NonNullList.of(Ingredient.EMPTY,
                        Ingredient.of(Items.GOLD_INGOT),
                        Ingredient.of(Items.IRON_INGOT)),
                null, null, -1, null, null, null);

        AdvancedAlloyFurnaceRecipe converted = new OccultismRitualRecipeAdapter()
                .convertAll(new RecipeHolder<>(ID, source), null).get(0);

        assertEquals(1L, required(converted, new ItemStack(Items.BOOK)));
        assertEquals(1L, required(converted, new ItemStack(Items.GOLD_INGOT)));
        assertEquals(1L, required(converted, new ItemStack(Items.IRON_INGOT)));
        assertEquals(1, converted.outputs().size());
        assertEquals(3, converted.outputs().get(0).getCount());
        assertEquals("native", tag(converted.outputs().get(0)).getString("marker"));
        assertEquals(600, converted.processTime());

        // The recipe owns a detached copy; mutating a caller's result must not leak into it.
        result.getTag().putString("marker", "changed");
        assertEquals("native", tag(converted.outputs().get(0)).getString("marker"));
    }

    @Test
    void sacrificeAndPlayerItemUseAreRejectedInsteadOfBecomingIngredients() {
        TagKey<EntityType<?>> sacrifice = TagKey.create(Registries.ENTITY_TYPE, id("test_sacrifice"));
        RitualRecipe sacrificed = recipe("summon", new ItemStack(Items.PIG_SPAWN_EGG),
                Ingredient.of(Items.BOOK), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.GOLD_INGOT)),
                EntityType.PIG, null, -1, null, sacrifice, null);
        assertTrue(new OccultismRitualRecipeAdapter()
                .convertAll(new RecipeHolder<>(ID, sacrificed), null).isEmpty());

        RitualRecipe itemUse = recipe("summon", new ItemStack(Items.PIG_SPAWN_EGG),
                Ingredient.of(Items.BOOK), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.GOLD_INGOT)),
                EntityType.PIG, null, -1, null, null, Ingredient.of(Items.FLINT_AND_STEEL));
        assertTrue(new OccultismRitualRecipeAdapter()
                .convertAll(new RecipeHolder<>(ID, itemUse), null).isEmpty());
    }

    @Test
    void minerSpiritUsesNativeOnCraftedNbtAndCopiesOnlyTheActivationSpiritName() {
        MinerSpiritItem miner = InventoryTestBootstrap.registerItem("occultism_miner", () -> new MinerSpiritItem(
                new Item.Properties().durability(100), () -> 123, () -> 7, () -> 100));
        RitualRecipe source = recipe("craft_miner_spirit", new ItemStack(miner),
                Ingredient.of(Items.BOOK), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.GOLD_INGOT)),
                null, null, -1, id("miner_job"), null, null);
        RecipeHolder<RitualRecipe> holder = new RecipeHolder<>(ID, source);

        AdvancedAlloyFurnaceRecipe display = new OccultismRitualRecipeAdapter()
                .convertAll(holder, null).get(0);
        ItemStack displayOutput = display.outputs().get(0);
        assertEquals(123, displayOutput.getTag().getInt(DimensionalMineshaftBlockEntity.MAX_MINING_TIME_TAG));
        assertEquals(7, displayOutput.getTag().getInt(DimensionalMineshaftBlockEntity.ROLLS_PER_OPERATION_TAG));
        assertFalse(displayOutput.getTag().contains(ItemNBTUtil.SPIRIT_NAME_TAG));

        ItemStack activation = new ItemStack(Items.BOOK);
        ItemNBTUtil.setBoundSpiritName(activation, "A real miner name");
        AdvancedAlloyFurnaceRecipe runtime = new OccultismRitualRecipeAdapter()
                .convertAll(holder, null, List.of(activation)).get(0);
        ItemStack runtimeOutput = runtime.outputs().get(0);
        assertEquals("A real miner name",
                runtimeOutput.getTag().getString(ItemNBTUtil.SPIRIT_NAME_TAG));
        assertEquals(123, runtimeOutput.getTag().getInt(DimensionalMineshaftBlockEntity.MAX_MINING_TIME_TAG));
        // The input stack itself must remain untouched by the native copy-over operation.
        assertEquals("A real miner name", ItemNBTUtil.getBoundSpiritName(activation));
    }

    @Test
    void craftWithSpiritNameCopiesTheActivationNameOnlyInTheRuntimeVariant() {
        ItemStack result = new ItemStack(Items.DIAMOND);
        RitualRecipe source = recipe("craft_with_spirit_name", result,
                Ingredient.of(Items.BOOK), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.GOLD_INGOT)),
                null, null, -1, null, null, null);
        RecipeHolder<RitualRecipe> holder = new RecipeHolder<>(ID, source);

        AdvancedAlloyFurnaceRecipe display = new OccultismRitualRecipeAdapter()
                .convertAll(holder, null).get(0);
        assertFalse(display.outputs().get(0).hasTag());

        ItemStack activation = new ItemStack(Items.BOOK);
        ItemNBTUtil.setBoundSpiritName(activation, "Bound name");
        AdvancedAlloyFurnaceRecipe runtime = new OccultismRitualRecipeAdapter()
                .convertAll(holder, null, List.of(activation)).get(0);
        assertEquals("Bound name",
                runtime.outputs().get(0).getTag().getString(ItemNBTUtil.SPIRIT_NAME_TAG));
        assertEquals(1L, required(runtime, activation));
        assertEquals(0L, required(runtime, new ItemStack(Items.BOOK)));
        ItemStack other = activation.copy(); ItemNBTUtil.setBoundSpiritName(other, "Other name");
        assertEquals(0L, required(runtime, other));
        var otherVariant = new OccultismRitualRecipeAdapter().convertAll(holder, null, List.of(other)).get(0);
        org.junit.jupiter.api.Assertions.assertNotEquals(runtime.id(), otherVariant.id());
    }

    @Test
    void summonTamedEggUsesNativeEntityTagAndAutoTameMarker() {
        ItemStack result = new ItemStack(Items.PIG_SPAWN_EGG);
        RitualRecipe source = recipe("summon_tamed", result,
                Ingredient.of(Items.BOOK), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.GOLD_INGOT)),
                EntityType.PIG, null, -1, null, null, null);
        RecipeHolder<RitualRecipe> holder = new RecipeHolder<>(ID, source);

        AdvancedAlloyFurnaceRecipe display = new OccultismRitualRecipeAdapter()
                .convertAll(holder, null).get(0);
        ItemStack egg = display.outputs().get(0);
        CompoundTag entityTag = egg.getTag().getCompound("EntityTag");
        assertEquals("minecraft:pig", entityTag.getString("id"));
        assertTrue(egg.getTag().getBoolean(OccultismRitualRecipeAdapter.AUTO_TAME_MARKER));

        ItemStack activation = new ItemStack(Items.BOOK);
        ItemNBTUtil.setBoundSpiritName(activation, "Named pig");
        AdvancedAlloyFurnaceRecipe runtime = new OccultismRitualRecipeAdapter()
                .convertAll(holder, null, List.of(activation)).get(0);
        String customName = runtime.outputs().get(0).getTag()
                .getCompound("EntityTag").getString("CustomName");
        assertTrue(customName.contains("Named pig"));
    }

    @Test
    void jobEntityNbtKeepsMaxAgeAndFactoryIdInTheNativeCompoundShape() {
        RitualRecipe source = recipe("summon_spirit_with_job", new ItemStack(Items.DIAMOND),
                Ingredient.of(Items.BOOK), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.GOLD_INGOT)),
                EntityType.PIG, null, 60, id("cleaner"), null, null);
        ItemStack stack = new ItemStack(Items.PIG_SPAWN_EGG);
        OccultismRitualRecipeAdapter.applyJobEntityData(source, stack);

        CompoundTag entityTag = stack.getTag().getCompound("EntityTag");
        assertEquals("minecraft:pig", entityTag.getString("id"));
        assertEquals(60, entityTag.getInt("spiritMaxAge"));
        assertEquals("occultism:cleaner", entityTag.getCompound("spiritJob").getString("factoryId"));
    }

    @Test
    void plainSpawnEggHelperWritesNativeEntityTagWithoutMutatingSourceNbt() {
        CompoundTag sourceTag = new CompoundTag();
        sourceTag.putString("id", "minecraft:cow");
        sourceTag.putInt("Age", 3);

        List<ItemStack> eggs = OccultismRitualRecipeAdapter.spawnEggOutputs(
                List.of(EntityType.PIG), sourceTag);
        assertEquals(1, eggs.size());
        CompoundTag entityTag = eggs.get(0).getTag().getCompound("EntityTag");
        assertEquals("minecraft:pig", entityTag.getString("id"));
        assertEquals(3, entityTag.getInt("Age"));
        // The helper copied the source compound before overriding the entity id.
        assertEquals("minecraft:cow", sourceTag.getString("id"));
    }

    @Test
    void dynamicProfileMarksOnlyTheActivationDerivedOutputAsItemIdOnly() {
        RitualRecipe source = recipe("craft_with_spirit_name", new ItemStack(Items.DIAMOND),
                Ingredient.of(Items.BOOK), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.GOLD_INGOT)),
                null, null, -1, null, null, null);
        RecipeHolder<RitualRecipe> holder = new RecipeHolder<>(ID, source);

        ItemStack bookInput = new ItemStack(Items.BOOK);
        ItemNBTUtil.setBoundSpiritName(bookInput, "Pattern spirit");
        ItemStack namedOutput = new ItemStack(Items.DIAMOND);
        ItemNBTUtil.setBoundSpiritName(namedOutput, "Pattern spirit");

        GenericStack bookKey = GenericStack.fromItemStack(bookInput);
        GenericStack goldKey = GenericStack.fromItemStack(new ItemStack(Items.GOLD_INGOT));
        GenericStack outputKey = GenericStack.fromItemStack(namedOutput);
        assertTrue(bookKey != null && goldKey != null && outputKey != null);

        PatternStackView pattern = new PatternStackView(
                List.of(bookKey, goldKey), List.of(outputKey));
        Optional<OccultismRitualRecipeAdapter.DynamicPatternProfile> profile =
                OccultismRitualRecipeAdapter.findDynamicPatternProfileLong(List.of(holder), pattern);

        assertTrue(profile.isPresent());
        assertEquals(Set.of(0), profile.get().idOnlyOutputSlots());
        // The fixture uses a plain book, not Occultism's bound item, so no input slot is
        // marker-rewritten here; the output rewrite is the native spiritName behaviour.
        assertEquals(Set.of(), profile.get().idOnlyInputSlots());
    }

    @Test
    void dynamicProfileRejectsUnproducibleNbtAndRequiresOneConsistentLongMultiplier() {
        RitualRecipe source = recipe("craft_with_spirit_name", new ItemStack(Items.DIAMOND),
                Ingredient.of(Items.BOOK), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.GOLD_INGOT)),
                null, null, -1, null, null, null);
        var holder = new RecipeHolder<>(ID, source);
        var activation = new ItemStack(Items.BOOK); ItemNBTUtil.setBoundSpiritName(activation, "Exact spirit");
        var result = new ItemStack(Items.DIAMOND); ItemNBTUtil.setBoundSpiritName(result, "Exact spirit");
        var bookKey = AEItemKey.of(activation);
        var goldKey = AEItemKey.of(Items.GOLD_INGOT);
        var outputKey = AEItemKey.of(result);
        var multiplied = new PatternStackView(List.of(new GenericStack(bookKey, 2), new GenericStack(goldKey, 2)),
                List.of(new GenericStack(outputKey, 2)));
        assertTrue(OccultismRitualRecipeAdapter.findDynamicPatternProfileLong(List.of(holder), multiplied).isPresent());
        var wrongAmount = new PatternStackView(List.of(new GenericStack(bookKey, 1), new GenericStack(goldKey, 1)),
                List.of(new GenericStack(outputKey, 2)));
        assertTrue(OccultismRitualRecipeAdapter.findDynamicPatternProfileLong(List.of(holder), wrongAmount).isEmpty());
        var impossible = result.copy(); impossible.getOrCreateTag().putBoolean("Unbreakable", true);
        var wrongNbt = new PatternStackView(List.of(new GenericStack(bookKey, 1), new GenericStack(goldKey, 1)),
                List.of(new GenericStack(AEItemKey.of(impossible), 1)));
        assertTrue(OccultismRitualRecipeAdapter.findDynamicPatternProfileLong(List.of(holder), wrongNbt).isEmpty());
        var overflow = new PatternStackView(List.of(new GenericStack(bookKey, Long.MAX_VALUE), new GenericStack(goldKey, Long.MAX_VALUE)),
                List.of(new GenericStack(outputKey, Long.MAX_VALUE)));
        assertTrue(OccultismRitualRecipeAdapter.findDynamicPatternProfileLong(List.of(holder), overflow).isEmpty());
    }

    private static long required(AdvancedAlloyFurnaceRecipe recipe, ItemStack stack) {
        long total = 0L;
        for (var input : recipe.inputs()) {
            if (input.ingredient().test(stack)) {
                total += input.count();
            }
        }
        return total;
    }

    private static ItemStack tagged(Item item, int count, String key, String value) {
        ItemStack stack = new ItemStack(item, count);
        stack.getOrCreateTag().putString(key, value);
        return stack;
    }

    private static CompoundTag tag(ItemStack stack) {
        return stack.getTag() == null ? new CompoundTag() : stack.getTag();
    }

    private static RitualRecipe recipe(
            String ritualType, ItemStack result, Ingredient activation, NonNullList<Ingredient> ingredients,
            EntityType<?> entityToSummon, CompoundTag entityNbt, int spiritMaxAge,
            ResourceLocation spiritJobType, TagKey<EntityType<?>> entityToSacrifice,
            Ingredient itemToUse) {
        return new RitualRecipe(
                ID, PENTACLE, id(ritualType), new ItemStack(Items.PAPER), result,
                entityToSummon, entityNbt, activation, ingredients, 600, spiritMaxAge,
                spiritJobType, entityToSacrifice, "", itemToUse == null ? Ingredient.EMPTY : itemToUse, "");
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("occultism", path);
    }

}
