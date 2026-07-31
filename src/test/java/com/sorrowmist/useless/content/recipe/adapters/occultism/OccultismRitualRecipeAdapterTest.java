package com.sorrowmist.useless.content.recipe.adapters.occultism;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import com.klikli_dev.occultism.crafting.recipe.RitualRecipe;
import com.klikli_dev.occultism.registry.OccultismDataComponents;
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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static DynamicComponentPatternDetails dynamicPattern(
            ItemStack input, ItemStack output, OccultismRitualRecipeAdapter.DynamicPatternProfile profile) {
        GenericStack encodedInput = Objects.requireNonNull(GenericStack.fromItemStack(input));
        GenericStack encodedOutput = Objects.requireNonNull(GenericStack.fromItemStack(output));
        ItemStack encodedPattern = PatternDetailsHelper.encodeProcessingPattern(
                List.of(encodedInput), List.of(encodedOutput));
        AEProcessingPattern source = new AEProcessingPattern(
                Objects.requireNonNull(AEItemKey.of(encodedPattern)));
        return new DynamicComponentPatternDetails(
                source, profile.idOnlyInputSlots(), profile.idOnlyOutputSlots(), RegistryAccess.EMPTY);
    }
}
