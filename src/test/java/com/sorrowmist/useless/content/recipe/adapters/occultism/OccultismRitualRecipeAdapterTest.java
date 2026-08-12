package com.sorrowmist.useless.content.recipe.adapters.occultism;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import com.klikli_dev.occultism.crafting.recipe.RitualRecipe;
import com.klikli_dev.occultism.registry.OccultismBlocks;
import com.klikli_dev.occultism.registry.OccultismDataComponents;
import com.klikli_dev.occultism.registry.OccultismEntities;
import com.klikli_dev.occultism.registry.OccultismItems;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPatternDetails;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OccultismRitualRecipeAdapterTest {
    private static final ResourceLocation PENTACLE = ResourceLocation.fromNamespaceAndPath("occultism", "craft_afrit");

    @Test
    void acceptsAnyBoundBookNameForTheSameTier() {
        List<Item> tiers = List.of(
                OccultismItems.BOOK_OF_BINDING_BOUND_FOLIOT.get(),
                OccultismItems.BOOK_OF_BINDING_BOUND_DJINNI.get(),
                OccultismItems.BOOK_OF_BINDING_BOUND_AFRIT.get(),
                OccultismItems.BOOK_OF_BINDING_BOUND_MARID.get());

        for (int tier = 0; tier < tiers.size(); tier++) {
            Item book = tiers.get(tier);
            ItemStack jeiBook = boundBook(book, "jei-random-" + tier);
            ItemStack craftedBook = boundBook(book, "crafted-random-" + tier);
            RitualRecipe source = recipe("craft", Ingredient.of(book),
                    NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIAMOND)), new ItemStack(Items.PAPER));

            var profile = OccultismRitualRecipeAdapter.findDynamicPatternProfile(
                    List.of(holder("bound_book_" + tier, source)),
                    List.of(jeiBook, new ItemStack(Items.DIAMOND)),
                    List.of(new ItemStack(Items.PAPER))).orElseThrow();

            assertEquals(Set.of(0), profile.idOnlyInputSlots());
            assertTrue(profile.idOnlyOutputSlots().isEmpty());
            assertFalse(ItemStack.isSameItemSameComponents(jeiBook, craftedBook));

            DynamicComponentPatternDetails dynamic = dynamicPattern(jeiBook, new ItemStack(Items.PAPER), profile);
            assertTrue(dynamic.getInputs()[0].isValid(AEItemKey.of(craftedBook), null));
        }
    }

    @Test
    void keepsDifferentBoundBookTiersStrict() {
        ItemStack displayed = boundBook(OccultismItems.BOOK_OF_BINDING_BOUND_FOLIOT.get(), "jei");
        ItemStack wrongTier = boundBook(OccultismItems.BOOK_OF_BINDING_BOUND_DJINNI.get(), "crafted");
        RitualRecipe source = recipe("craft", Ingredient.of(OccultismItems.BOOK_OF_BINDING_BOUND_FOLIOT.get()),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIAMOND)), new ItemStack(Items.PAPER));

        var profile = OccultismRitualRecipeAdapter.findDynamicPatternProfile(
                List.of(holder("foliot_only", source)),
                List.of(displayed, new ItemStack(Items.DIAMOND)),
                List.of(new ItemStack(Items.PAPER))).orElseThrow();
        DynamicComponentPatternDetails dynamic = dynamicPattern(displayed, new ItemStack(Items.PAPER), profile);

        assertFalse(dynamic.getInputs()[0].isValid(AEItemKey.of(wrongTier), null));
        assertTrue(OccultismRitualRecipeAdapter.findDynamicPatternProfile(
                List.of(holder("foliot_only", source)),
                List.of(wrongTier, new ItemStack(Items.DIAMOND)),
                List.of(new ItemStack(Items.PAPER))).isEmpty());
    }

    @Test
    void mergesBoundBookInputWithUpgradeDynamicSlots() {
        ItemStack boundBook = boundBook(OccultismItems.BOOK_OF_BINDING_BOUND_FOLIOT.get(), "jei");
        ItemStack base = named(new ItemStack(Items.DIAMOND_SWORD), "component sword");
        RitualRecipe source = recipe("upgrade", Ingredient.of(OccultismItems.BOOK_OF_BINDING_BOUND_FOLIOT.get()),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIAMOND_SWORD), Ingredient.of(Items.NETHER_STAR)),
                new ItemStack(Items.NETHERITE_SWORD));

        var profile = OccultismRitualRecipeAdapter.findDynamicPatternProfile(
                List.of(holder("bound_book_upgrade", source)),
                List.of(boundBook, base, new ItemStack(Items.NETHER_STAR)),
                List.of(new ItemStack(Items.NETHERITE_SWORD))).orElseThrow();

        assertEquals(Set.of(0, 1), profile.idOnlyInputSlots());
        assertEquals(Set.of(0), profile.idOnlyOutputSlots());
    }

    @Test
    void keepsProfilesStrictForAmbiguousOrUnrelatedPatterns() {
        RitualRecipe boundRecipe = recipe("craft", Ingredient.of(OccultismItems.BOOK_OF_BINDING_BOUND_FOLIOT.get()),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIAMOND)), new ItemStack(Items.PAPER));
        ItemStack boundBook = boundBook(OccultismItems.BOOK_OF_BINDING_BOUND_FOLIOT.get(), "jei");
        List<ItemStack> inputs = List.of(boundBook, new ItemStack(Items.DIAMOND));
        List<ItemStack> outputs = List.of(new ItemStack(Items.PAPER));

        assertTrue(OccultismRitualRecipeAdapter.findDynamicPatternProfile(
                List.of(holder("first", boundRecipe), holder("second", boundRecipe)), inputs, outputs).isEmpty());
        assertTrue(OccultismRitualRecipeAdapter.findDynamicPatternProfile(
                List.of(holder("bound", boundRecipe)),
                List.of(boundBook, new ItemStack(Items.DIAMOND), new ItemStack(Items.STICK)), outputs).isEmpty());

        RitualRecipe unrelated = recipe("craft", Ingredient.of(Items.BOOK),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIAMOND)), new ItemStack(Items.PAPER));
        assertTrue(OccultismRitualRecipeAdapter.findDynamicPatternProfile(
                List.of(holder("unrelated", unrelated)),
                List.of(named(new ItemStack(Items.BOOK), "named"), new ItemStack(Items.DIAMOND)), outputs).isEmpty());

        ItemStack requiredBook = boundBook(OccultismItems.BOOK_OF_BINDING_BOUND_FOLIOT.get(), "required");
        RitualRecipe componentSensitive = recipe("craft", DataComponentIngredient.of(true, requiredBook),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIAMOND)), new ItemStack(Items.PAPER));
        assertTrue(OccultismRitualRecipeAdapter.findDynamicPatternProfile(
                List.of(holder("component_sensitive", componentSensitive)),
                List.of(requiredBook, new ItemStack(Items.DIAMOND)), outputs).isEmpty());
    }

    @Test
    void repairsActualActivationWithoutDroppingComponents() {
        ItemStack damaged = named(new ItemStack(Items.DIAMOND_SWORD), "component sword");
        damaged.setDamageValue(900);
        CompoundTag custom = new CompoundTag();
        custom.putString("owner", "test");
        damaged.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));

        OccultismRitualRecipeAdapter adapter = new OccultismRitualRecipeAdapter();
        AdvancedAlloyFurnaceRecipe converted = adapter.convertAll(
                        holder("repair", recipe("repair", Ingredient.of(Items.DIAMOND_SWORD),
                                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.GHAST_TEAR)),
                                new ItemStack(Items.PAPER))),
                        null,
                        List.of(damaged, new ItemStack(Items.GHAST_TEAR)))
                .getFirst();

        ItemStack output = converted.outputs().getFirst();
        assertEquals(0, output.getDamageValue());
        assertEquals("component sword", output.get(DataComponents.CUSTOM_NAME).getString());
        assertEquals("test", output.get(DataComponents.CUSTOM_DATA).copyTag().getString("owner"));
        assertTrue(ItemIngredientAllocator.matches(
                converted.inputs(), List.of(damaged, new ItemStack(Items.GHAST_TEAR)), 1));

        ItemStack other = named(new ItemStack(Items.DIAMOND_SWORD), "other sword");
        other.setDamageValue(900);
        assertFalse(ItemIngredientAllocator.matches(
                converted.inputs(), List.of(other, new ItemStack(Items.GHAST_TEAR)), 1));
    }

    @Test
    void upgradesActualBaseAndKeepsItsComponents() {
        ItemStack base = named(new ItemStack(Items.DIAMOND_SWORD), "upgraded sword");
        base.set(DataComponents.MAX_DAMAGE, 600);
        base.setDamageValue(450);
        CompoundTag custom = new CompoundTag();
        custom.putInt("upgrade_marker", 42);
        base.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));

        RitualRecipe source = recipe("upgrade", Ingredient.of(Items.BOOK),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DIAMOND_SWORD), Ingredient.of(Items.NETHER_STAR)),
                new ItemStack(Items.NETHERITE_SWORD));
        AdvancedAlloyFurnaceRecipe converted = new OccultismRitualRecipeAdapter().convertAll(
                        holder("upgrade", source), null,
                        List.of(new ItemStack(Items.BOOK), base, new ItemStack(Items.NETHER_STAR)))
                .getFirst();

        ItemStack output = converted.outputs().getFirst();
        assertTrue(output.is(Items.NETHERITE_SWORD));
        assertEquals("upgraded sword", output.get(DataComponents.CUSTOM_NAME).getString());
        assertEquals(42, output.get(DataComponents.CUSTOM_DATA).copyTag().getInt("upgrade_marker"));
        assertEquals(base.getDamageValue(), output.getDamageValue());
        assertEquals(new ItemStack(Items.NETHERITE_SWORD).getMaxDamage(), output.getMaxDamage());
        assertTrue(ItemIngredientAllocator.matches(converted.inputs(),
                List.of(new ItemStack(Items.BOOK), base, new ItemStack(Items.NETHER_STAR)), 1));
    }

    @Test
    void writesSpiritJobAndSourceEntityDataToTheEggComponent() {
        CompoundTag sourceEntityData = new CompoundTag();
        sourceEntityData.putString("CustomName", "job spirit");
        RitualRecipe source = new RitualRecipe(
                PENTACLE,
                ResourceLocation.fromNamespaceAndPath("occultism", "summon_spirit_with_job"),
                new ItemStack(Items.PAPER),
                new ItemStack(Items.PIG_SPAWN_EGG),
                net.minecraft.world.entity.EntityType.PIG,
                null,
                sourceEntityData,
                Ingredient.of(Items.BOOK),
                NonNullList.create(),
                80,
                600,
                1,
                ResourceLocation.fromNamespaceAndPath("occultism", "crush_tier4"),
                null,
                null,
                null);
        ItemStack egg = new ItemStack(Items.PIG_SPAWN_EGG);

        OccultismRitualRecipeAdapter.applyJobEntityData(source, egg);

        CompoundTag entityData = egg.get(DataComponents.ENTITY_DATA).copyTag();
        assertEquals("minecraft:pig", entityData.getString("id"));
        assertEquals("job spirit", entityData.getString("CustomName"));
        assertEquals(600, entityData.getInt("spiritMaxAge"));
        assertEquals("occultism:crush_tier4", entityData.getCompound("spiritJob").getString("factoryId"));
    }

    @Test
    void fixedEntityWithOrdinaryResultAlwaysProducesTheDeclaredEntityEgg() {
        ItemStack result = named(new ItemStack(Items.BOOK), "ordinary ritual result");
        result.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("book lore"))));
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putString("CustomName", "summoned cow");
        RitualRecipe source = entityRecipe(
                "fixed_ordinary_result", result, EntityType.COW, entityNbt, null, -1);

        List<ItemStack> outputs = new OccultismRitualRecipeAdapter()
                .convertAll(holder("fixed_ordinary_result", source), null)
                .getFirst().outputs();
        assertEquals(2, outputs.size());
        ItemStack output = outputs.getFirst();

        assertSpawnEgg(output, EntityType.COW);
        assertTrue(output.is(Items.COW_SPAWN_EGG));
        assertFalse(output.has(DataComponents.CUSTOM_NAME));
        assertNotEquals(result.get(DataComponents.LORE), output.get(DataComponents.LORE));
        assertEquals("minecraft:cow", output.get(DataComponents.ENTITY_DATA)
                .copyTag().getString("id"));
        assertEquals("summoned cow", output.get(DataComponents.ENTITY_DATA)
                .copyTag().getString("CustomName"));
        assertTrue(ItemStack.isSameItemSameComponents(result, outputs.get(1)));
    }

    @Test
    void callingBookJobRitualsProduceTamedWorkEggs() {
        List<JobSpec> jobs = List.of(
                new JobSpec("lumberjack", OccultismEntities.FOLIOT.get(),
                        OccultismItems.BOOK_OF_CALLING_FOLIOT_LUMBERJACK.get()),
                new JobSpec("farmer", OccultismEntities.FOLIOT.get(),
                        OccultismItems.BOOK_OF_CALLING_FOLIOT_FARMER.get()),
                new JobSpec("cleaner", OccultismEntities.FOLIOT.get(),
                        OccultismItems.BOOK_OF_CALLING_FOLIOT_CLEANER.get()),
                new JobSpec("transport_items", OccultismEntities.FOLIOT.get(),
                        OccultismItems.BOOK_OF_CALLING_FOLIOT_TRANSPORT_ITEMS.get()),
                new JobSpec("manage_machine", OccultismEntities.DJINNI.get(),
                        OccultismItems.BOOK_OF_CALLING_DJINNI_MANAGE_MACHINE.get()));

        for (JobSpec spec : jobs) {
            RitualRecipe source = entityRecipe(
                    "job_" + spec.jobId(), new ItemStack(spec.result()), spec.entityType(), null,
                    ResourceLocation.fromNamespaceAndPath("occultism", spec.jobId()), 600);
            List<ItemStack> outputs = new OccultismRitualRecipeAdapter()
                    .convertAll(holder("job_" + spec.jobId(), source), null)
                    .getFirst().outputs();
            assertEquals(2, outputs.size(), spec.jobId());
            ItemStack output = outputs.getFirst();

            assertSpawnEgg(output, spec.entityType());
            assertTrue(output.has(DataComponents.ENTITY_DATA), spec.jobId());
            CompoundTag entityData = output.get(DataComponents.ENTITY_DATA).copyTag();
            assertEquals(600, entityData.getInt("spiritMaxAge"), spec.jobId());
            assertEquals("occultism:" + spec.jobId(),
                    entityData.getCompound("spiritJob").getString("factoryId"), spec.jobId());
            assertTrue(output.get(DataComponents.CUSTOM_DATA).copyTag()
                    .getBoolean(OccultismRitualRecipeAdapter.AUTO_TAME_MARKER), spec.jobId());
            assertFalse(output.has(OccultismDataComponents.SPIRIT_NAME), spec.jobId());
            assertTrue(ItemStack.isSameItemSameComponents(
                    new ItemStack(spec.result()), outputs.get(1)), spec.jobId());
        }
    }

    @Test
    void copiesOnlyNameAndLoreFromAnExistingSpawnEggResult() {
        ItemStack result = new ItemStack(Items.COW_SPAWN_EGG);
        result.set(DataComponents.CUSTOM_NAME, Component.literal("named cow"));
        result.set(DataComponents.ITEM_NAME, Component.literal("ritual cow"));
        result.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("special lore"))));
        CompoundTag resultCustomData = new CompoundTag();
        resultCustomData.putString("should_not_copy", "yes");
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(resultCustomData));

        RitualRecipe source = entityRecipe(
                "named_egg_result", result, EntityType.COW, null, null, -1);
        List<ItemStack> outputs = new OccultismRitualRecipeAdapter()
                .convertAll(holder("named_egg_result", source), null)
                .getFirst().outputs();
        assertEquals(2, outputs.size());
        ItemStack output = outputs.getFirst();

        assertTrue(output.is(Items.COW_SPAWN_EGG));
        assertEquals(result.get(DataComponents.CUSTOM_NAME), output.get(DataComponents.CUSTOM_NAME));
        assertEquals(result.get(DataComponents.ITEM_NAME), output.get(DataComponents.ITEM_NAME));
        assertEquals(result.get(DataComponents.LORE), output.get(DataComponents.LORE));
        assertFalse(output.has(DataComponents.CUSTOM_DATA));
        assertTrue(ItemStack.isSameItemSameComponents(result, outputs.get(1)));
    }

    @Test
    void doesNotDuplicateAnExistingSpawnEggResult() {
        RitualRecipe source = entityRecipe(
                "duplicate_egg_result", new ItemStack(Items.COW_SPAWN_EGG), EntityType.COW, null, null, -1);

        List<ItemStack> outputs = new OccultismRitualRecipeAdapter()
                .convertAll(holder("duplicate_egg_result", source), null)
                .getFirst().outputs();

        assertEquals(1, outputs.size());
        assertEquals(1, outputs.getFirst().getCount());
        assertTrue(ItemStack.isSameItemSameComponents(
                new ItemStack(Items.COW_SPAWN_EGG), outputs.getFirst()));
    }

    @Test
    void createsAUsableSpawnEggForEveryRandomEntity() {
        CompoundTag sourceEntityData = new CompoundTag();
        sourceEntityData.putString("CustomName", "ritual animal");

        List<ItemStack> outputs = OccultismRitualRecipeAdapter.spawnEggOutputs(
                List.of(EntityType.COW, EntityType.IRON_GOLEM), sourceEntityData);

        assertEquals(2, outputs.size());
        assertSpawnEgg(outputs.get(0), EntityType.COW);
        assertSpawnEgg(outputs.get(1), EntityType.IRON_GOLEM);
        for (ItemStack output : outputs) {
            assertEquals(1, output.getCount());
            assertEquals("ritual animal",
                    output.get(DataComponents.ENTITY_DATA).copyTag().getString("CustomName"));
        }
    }

    @Test
    void emptyEntityTagsStayEmptyAndMissingEggsUsePigWithTheTargetId() {
        assertTrue(OccultismRitualRecipeAdapter.spawnEggOutputs(List.of(), null).isEmpty());

        ItemStack output = OccultismRitualRecipeAdapter.spawnEggOutputs(
                List.of(EntityType.PLAYER), null).getFirst();
        assertTrue(output.is(Items.PIG_SPAWN_EGG));
        assertSpawnEgg(output, EntityType.PLAYER);
        assertEquals("minecraft:player", output.get(DataComponents.ENTITY_DATA)
                .copyTag().getString("id"));
    }

    @Test
    void convertsAllMinerSpiritRitualsWithConfiguredStaticOutputs() {
        List<MinerSpec> specs = List.of(
                new MinerSpec(
                        "craft_miner_foliot_unspecialized",
                        OccultismItems.BOOK_OF_BINDING_BOUND_FOLIOT.get(),
                        OccultismItems.MINER_FOLIOT_UNSPECIALIZED.get(),
                        List.of(OccultismItems.MAGIC_LAMP_EMPTY.get(), Items.IRON_INGOT, Items.GRAVEL)),
                new MinerSpec(
                        "craft_miner_djinni_ores",
                        OccultismItems.BOOK_OF_BINDING_BOUND_DJINNI.get(),
                        OccultismItems.MINER_DJINNI_ORES.get(),
                        List.of(OccultismItems.MINER_FOLIOT_UNSPECIALIZED.get(), Items.IRON_PICKAXE,
                                Items.GOLD_INGOT, Items.LAPIS_LAZULI,
                                OccultismBlocks.SPIRIT_ATTUNED_CRYSTAL.get().asItem())),
                new MinerSpec(
                        "craft_miner_afrit_deeps",
                        OccultismItems.BOOK_OF_BINDING_BOUND_AFRIT.get(),
                        OccultismItems.MINER_AFRIT_DEEPS.get(),
                        List.of(OccultismItems.MINER_DJINNI_ORES.get(), Items.IRON_PICKAXE,
                                OccultismBlocks.SPIRIT_ATTUNED_CRYSTAL.get().asItem(),
                                OccultismItems.AFRIT_ESSENCE.get(),
                                Items.ECHO_SHARD, Items.CRYING_OBSIDIAN)),
                new MinerSpec(
                        "craft_miner_marid_master",
                        OccultismItems.BOOK_OF_BINDING_BOUND_MARID.get(),
                        OccultismItems.MINER_MARID_MASTER.get(),
                        List.of(OccultismItems.MINER_AFRIT_DEEPS.get(), Items.IRON_PICKAXE,
                                OccultismBlocks.SPIRIT_ATTUNED_CRYSTAL.get().asItem(), Items.NETHERITE_PICKAXE,
                                Items.DRAGON_BREATH, Items.TOTEM_OF_UNDYING, Items.NETHER_STAR,
                                OccultismItems.MARID_ESSENCE.get())),
                new MinerSpec(
                        "misc_miner_ancient_eldritch",
                        OccultismItems.MINING_DIMENSION_CORE_PIECE.get(),
                        OccultismItems.MINER_ANCIENT_ELDRITCH.get(),
                        List.of(OccultismItems.MINER_MARID_MASTER.get(), OccultismItems.MINER_MARID_MASTER.get(),
                                OccultismItems.MINER_MARID_MASTER.get(), OccultismItems.MINER_MARID_MASTER.get(),
                                OccultismItems.MINER_MARID_MASTER.get(), OccultismItems.MINER_MARID_MASTER.get(),
                                OccultismItems.MINER_MARID_MASTER.get(), OccultismItems.MINER_MARID_MASTER.get())));

        OccultismRitualRecipeAdapter adapter = new OccultismRitualRecipeAdapter();
        for (MinerSpec spec : specs) {
            ItemStack activation = new ItemStack(spec.activation());
            RitualRecipe source = minerRecipe(spec.activation(), spec.result(), spec.ingredients());
            AdvancedAlloyFurnaceRecipe converted = adapter.convertAll(
                            holder(spec.id(), source), null)
                    .getFirst();

            ItemStack output = converted.outputs().getFirst();
            assertTrue(output.is(spec.result()));
            assertTrue(output.has(OccultismDataComponents.MAX_MINING_TIME));
            assertTrue(output.has(OccultismDataComponents.ROLLS_PER_OPERATION));
            assertFalse(output.has(OccultismDataComponents.SPIRIT_NAME), spec.id());
            assertEquals(spec.ingredients().size() + 1,
                    converted.inputs().stream().mapToLong(input -> input.count()).sum());
            assertTrue(ItemIngredientAllocator.matches(
                    converted.inputs(), withInputs(activation, spec.ingredients()), 1), spec.id());
            assertFalse(converted.mold().isEmpty(), spec.id());
            assertEquals(PENTACLE, converted.mold().getItems()[0]
                    .get(com.sorrowmist.useless.core.component.UComponents.RITUAL_BLUEPRINT_PENTACLE.get())
                    .pentacles().getFirst());
        }
    }

    @Test
    void copiesMinerSpiritNameAtRuntimeWithoutMutatingActivation() {
        ItemStack activation = new ItemStack(OccultismItems.BOOK_OF_BINDING_BOUND_AFRIT.get());
        activation.set(OccultismDataComponents.SPIRIT_NAME, "bound-afrit");
        ItemStack activationBefore = activation.copy();
        List<ItemStack> ingredients = List.of(
                new ItemStack(OccultismItems.MINER_DJINNI_ORES.get()),
                new ItemStack(Items.IRON_PICKAXE),
                new ItemStack(OccultismBlocks.SPIRIT_ATTUNED_CRYSTAL.get().asItem()),
                new ItemStack(OccultismItems.AFRIT_ESSENCE.get()),
                new ItemStack(Items.ECHO_SHARD),
                new ItemStack(Items.CRYING_OBSIDIAN));
        RitualRecipe source = minerRecipe(
                OccultismItems.BOOK_OF_BINDING_BOUND_AFRIT.get(),
                OccultismItems.MINER_AFRIT_DEEPS.get(),
                ingredients.stream().map(ItemStack::getItem).toList());

        AdvancedAlloyFurnaceRecipe converted = new OccultismRitualRecipeAdapter().convertAll(
                        holder("craft_miner_afrit_deeps", source), null,
                        withInputs(activation, ingredients))
                .getFirst();

        ItemStack output = converted.outputs().getFirst();
        assertEquals("bound-afrit", output.get(OccultismDataComponents.SPIRIT_NAME));
        assertTrue(output.has(OccultismDataComponents.MAX_MINING_TIME));
        assertTrue(output.has(OccultismDataComponents.ROLLS_PER_OPERATION));
        assertTrue(ItemStack.isSameItemSameComponents(activationBefore, activation));
        assertTrue(converted.inputs().getFirst().ingredient().test(activation));
        assertFalse(converted.inputs().getFirst().ingredient().test(
                new ItemStack(OccultismItems.BOOK_OF_BINDING_BOUND_DJINNI.get())));
    }

    @Test
    void recognizesMinerPatternsWithDynamicActivationAndOutputSlots() {
        ItemStack displayedActivation = boundBook(
                OccultismItems.BOOK_OF_BINDING_BOUND_FOLIOT.get(), "displayed-spirit");
        RitualRecipe source = minerRecipe(
                OccultismItems.BOOK_OF_BINDING_BOUND_FOLIOT.get(),
                OccultismItems.MINER_FOLIOT_UNSPECIALIZED.get(),
                List.of(OccultismItems.MAGIC_LAMP_EMPTY.get(), Items.IRON_INGOT, Items.GRAVEL));
        AdvancedAlloyFurnaceRecipe staticRecipe = new OccultismRitualRecipeAdapter().convertAll(
                        holder("craft_miner_foliot_unspecialized", source), null)
                .getFirst();

        var profile = OccultismRitualRecipeAdapter.findDynamicPatternProfile(
                List.of(holder("craft_miner_foliot_unspecialized", source)),
                withInputs(displayedActivation, List.of(
                        new ItemStack(OccultismItems.MAGIC_LAMP_EMPTY.get()), new ItemStack(Items.IRON_INGOT),
                        new ItemStack(Items.GRAVEL))),
                List.of(staticRecipe.outputs().getFirst()))
                .orElseThrow();

        assertEquals(Set.of(0), profile.idOnlyInputSlots());
        assertEquals(Set.of(0), profile.idOnlyOutputSlots());

        ItemStack differentSpirit = boundBook(
                OccultismItems.BOOK_OF_BINDING_BOUND_FOLIOT.get(), "different-spirit");
        DynamicComponentPatternDetails dynamic = dynamicPattern(
                withInputs(displayedActivation, List.of(
                        new ItemStack(OccultismItems.MAGIC_LAMP_EMPTY.get()), new ItemStack(Items.IRON_INGOT),
                        new ItemStack(Items.GRAVEL))),
                List.of(staticRecipe.outputs().getFirst()), profile);
        assertTrue(dynamic.getInputs()[0].isValid(AEItemKey.of(differentSpirit), null));
        assertTrue(dynamic.getOutputs().getFirst().what() instanceof AEItemKey key
                && key.getItem() == OccultismItems.MINER_FOLIOT_UNSPECIALIZED.get());
        assertTrue(OccultismRitualRecipeAdapter.findDynamicPatternProfile(
                List.of(holder("craft_miner_foliot_unspecialized", source)),
                withInputs(new ItemStack(OccultismItems.BOOK_OF_BINDING_BOUND_DJINNI.get()), List.of(
                        new ItemStack(OccultismItems.MAGIC_LAMP_EMPTY.get()), new ItemStack(Items.IRON_INGOT),
                        new ItemStack(Items.GRAVEL))),
                List.of(staticRecipe.outputs().getFirst())).isEmpty());
        assertTrue(OccultismRitualRecipeAdapter.findDynamicPatternProfile(
                List.of(holder("craft_miner_foliot_unspecialized", source)),
                withInputs(displayedActivation, List.of(
                        new ItemStack(OccultismItems.MAGIC_LAMP_EMPTY.get()), new ItemStack(Items.IRON_INGOT),
                        new ItemStack(Items.GRAVEL))),
                List.of(new ItemStack(OccultismItems.MINER_DJINNI_ORES.get()))).isEmpty());
    }

    @Test
    void recognizesAncientMinerWithComponentBearingMiningCore() {
        ItemStack displayedCore = new ItemStack(OccultismItems.MINING_DIMENSION_CORE_PIECE.get());
        displayedCore.set(OccultismDataComponents.SPIRIT_NAME, "displayed-ancient");
        List<Item> ingredients = List.of(
                OccultismItems.MINER_MARID_MASTER.get(), OccultismItems.MINER_MARID_MASTER.get(),
                OccultismItems.MINER_MARID_MASTER.get(), OccultismItems.MINER_MARID_MASTER.get(),
                OccultismItems.MINER_MARID_MASTER.get(), OccultismItems.MINER_MARID_MASTER.get(),
                OccultismItems.MINER_MARID_MASTER.get(), OccultismItems.MINER_MARID_MASTER.get());
        RitualRecipe source = minerRecipe(
                OccultismItems.MINING_DIMENSION_CORE_PIECE.get(),
                OccultismItems.MINER_ANCIENT_ELDRITCH.get(), ingredients);
        AdvancedAlloyFurnaceRecipe staticRecipe = new OccultismRitualRecipeAdapter().convertAll(
                        holder("misc_miner_ancient_eldritch", source), null)
                .getFirst();
        var profile = OccultismRitualRecipeAdapter.findDynamicPatternProfile(
                List.of(holder("misc_miner_ancient_eldritch", source)),
                List.of(displayedCore, new ItemStack(OccultismItems.MINER_MARID_MASTER.get(), 8)),
                List.of(staticRecipe.outputs().getFirst()))
                .orElseThrow();

        assertEquals(Set.of(0), profile.idOnlyInputSlots());
        assertEquals(Set.of(0), profile.idOnlyOutputSlots());

        ItemStack actualCore = new ItemStack(OccultismItems.MINING_DIMENSION_CORE_PIECE.get());
        actualCore.set(OccultismDataComponents.SPIRIT_NAME, "actual-ancient");
        AdvancedAlloyFurnaceRecipe runtime = new OccultismRitualRecipeAdapter().convertAll(
                        holder("misc_miner_ancient_eldritch", source), null,
                        List.of(actualCore, new ItemStack(OccultismItems.MINER_MARID_MASTER.get(), 8)))
                .getFirst();
        assertEquals("actual-ancient", runtime.outputs().getFirst()
                .get(OccultismDataComponents.SPIRIT_NAME));
    }

    private static RitualRecipe recipe(
            String type, Ingredient activation, NonNullList<Ingredient> ingredients, ItemStack result) {
        return new RitualRecipe(
                PENTACLE,
                ResourceLocation.fromNamespaceAndPath("occultism", type),
                new ItemStack(Items.PAPER),
                result,
                null,
                null,
                null,
                activation,
                ingredients,
                80,
                -1,
                1,
                null,
                null,
                null,
                null);
    }

    private static RitualRecipe entityRecipe(
            String type, ItemStack result, EntityType<?> entityType, CompoundTag entityNbt,
            ResourceLocation spiritJobType, int spiritMaxAge) {
        return new RitualRecipe(
                PENTACLE,
                ResourceLocation.fromNamespaceAndPath("occultism", type),
                new ItemStack(Items.PAPER),
                result,
                entityType,
                null,
                entityNbt,
                Ingredient.of(Items.BOOK),
                NonNullList.create(),
                80,
                spiritMaxAge,
                1,
                spiritJobType,
                null,
                null,
                null);
    }

    private static RitualRecipe minerRecipe(Item activation, Item result, List<Item> ingredients) {
        NonNullList<Ingredient> requirements = NonNullList.create();
        ingredients.forEach(item -> requirements.add(Ingredient.of(item)));
        return new RitualRecipe(
                PENTACLE,
                ResourceLocation.fromNamespaceAndPath("occultism", "craft_miner_spirit"),
                new ItemStack(Items.PAPER),
                new ItemStack(result),
                null,
                null,
                null,
                Ingredient.of(activation),
                requirements,
                80,
                -1,
                1,
                null,
                null,
                null,
                null);
    }

    private static List<ItemStack> withInputs(ItemStack activation, List<?> ingredients) {
        List<ItemStack> result = new java.util.ArrayList<>();
        result.add(activation);
        for (Object ingredient : ingredients) {
            if (ingredient instanceof Item item) {
                result.add(new ItemStack(item));
            } else if (ingredient instanceof ItemStack stack) {
                result.add(stack);
            }
        }
        return result;
    }

    private static RecipeHolder<RitualRecipe> holder(String id, RitualRecipe recipe) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("occultism", id), recipe);
    }

    private static void assertSpawnEgg(ItemStack stack, EntityType<?> expectedType) {
        assertTrue(stack.getItem() instanceof SpawnEggItem);
        assertEquals(expectedType, ((SpawnEggItem) stack.getItem()).getType(stack));
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static ItemStack boundBook(Item item, String spiritName) {
        ItemStack stack = new ItemStack(item);
        stack.set(OccultismDataComponents.SPIRIT_NAME, spiritName);
        return stack;
    }

    private record MinerSpec(String id, Item activation, Item result, List<Item> ingredients) {
    }

    private record JobSpec(String jobId, EntityType<?> entityType, Item result) {
    }

    private static DynamicComponentPatternDetails dynamicPattern(
            ItemStack input, ItemStack output,
            OccultismRitualRecipeAdapter.DynamicPatternProfile profile) {
        return dynamicPattern(List.of(input), List.of(output), profile);
    }

    private static DynamicComponentPatternDetails dynamicPattern(
            List<ItemStack> inputs, List<ItemStack> outputs,
            OccultismRitualRecipeAdapter.DynamicPatternProfile profile) {
        List<GenericStack> encodedInputs = inputs.stream()
                .map(stack -> Objects.requireNonNull(GenericStack.fromItemStack(stack)))
                .toList();
        List<GenericStack> encodedOutputs = outputs.stream()
                .map(stack -> Objects.requireNonNull(GenericStack.fromItemStack(stack)))
                .toList();
        ItemStack encodedPattern = PatternDetailsHelper.encodeProcessingPattern(
                encodedInputs, encodedOutputs);
        AEProcessingPattern source = new AEProcessingPattern(
                Objects.requireNonNull(AEItemKey.of(encodedPattern)));
        return new DynamicComponentPatternDetails(
                source, profile.idOnlyInputSlots(), profile.idOnlyOutputSlots(), RegistryAccess.EMPTY);
    }
}
