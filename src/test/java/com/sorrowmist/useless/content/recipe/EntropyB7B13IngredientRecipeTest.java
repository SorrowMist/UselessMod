package com.sorrowmist.useless.content.recipe;

import appeng.recipes.handlers.InscriberRecipeSerializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeCodecs;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.crafting.CraftingHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntropyB7B13IngredientRecipeTest {
    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        if (CraftingHelper.getID(EnchantedItemIngredient.TYPE) == null)
            CraftingHelper.register(EnchantedItemIngredient.ID, EnchantedItemIngredient.TYPE);
    }

    private static ItemStack pickaxe(int fortune) {
        ItemStack stack = new ItemStack(Items.NETHERITE_PICKAXE);
        stack.enchant(Enchantments.BLOCK_FORTUNE, fortune);
        return stack;
    }

    @Test void enchantmentsAreLowerBoundsNotStrictNbtAndTemplatesAreDefensive() {
        ItemStack template = pickaxe(3);
        var ingredient = new EnchantedItemIngredient(template);
        template.setTag(null);
        ItemStack higher = pickaxe(7);
        higher.enchant(Enchantments.UNBREAKING, 3);
        higher.setDamageValue(50);
        higher.getOrCreateTag().putString("custom_name", "extra NBT is allowed");
        assertTrue(ingredient.test(higher));
        assertFalse(ingredient.test(pickaxe(2)));
        assertFalse(ingredient.test(new ItemStack(Items.NETHERITE_PICKAXE)));
        assertFalse(ingredient.test(new ItemStack(Items.DIAMOND_PICKAXE)));
        assertFalse(ingredient.test(null));
        assertFalse(ingredient.isSimple());
        assertFalse(ingredient.isVanilla());
        ingredient.getItems()[0].setTag(null);
        assertTrue(ingredient.test(pickaxe(3)));
    }

    @Test void everyAxeEnchantmentMustMeetItsMinimum() {
        ItemStack required = new ItemStack(Items.NETHERITE_AXE);
        required.enchant(Enchantments.MOB_LOOTING, 3);
        required.enchant(Enchantments.SILK_TOUCH, 1);
        var ingredient = new EnchantedItemIngredient(required);
        ItemStack missing = new ItemStack(Items.NETHERITE_AXE);
        missing.enchant(Enchantments.MOB_LOOTING, 5);
        assertFalse(ingredient.test(missing));
        missing.enchant(Enchantments.SILK_TOUCH, 1);
        assertTrue(ingredient.test(missing));
    }

    @Test void forgeJsonAndNativeIngredientPacketsRetainTheCustomPredicate() {
        Ingredient source = EnchantedItemIngredient.of(pickaxe(3));
        Ingredient json = Ingredient.fromJson(source.toJson());
        assertInstanceOf(EnchantedItemIngredient.class, json);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            json.toNetwork(buffer);
            Ingredient decoded = Ingredient.fromNetwork(buffer);
            assertInstanceOf(EnchantedItemIngredient.class, decoded);
            assertTrue(decoded.test(pickaxe(9)));
            assertFalse(decoded.test(pickaxe(1)));
            assertEquals(source.toJson(), decoded.toJson());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void existingNativeStrictNbtBridgeRemainsStrict() {
        ItemStack sample = new ItemStack(Items.DIAMOND);
        sample.getOrCreateTag().putLong("exact", Long.MAX_VALUE);
        JsonObject json = new JsonObject();
        json.addProperty("type", "forge:nbt");
        json.addProperty("item", "minecraft:diamond");
        json.addProperty("nbt", sample.getTag().toString());
        Ingredient strict = Ingredient.fromJson(json);
        assertTrue(strict.test(sample));
        ItemStack different = sample.copy();
        different.getOrCreateTag().putBoolean("extra", true);
        assertFalse(strict.test(different));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            RecipeCodecs.INGREDIENT_STREAM.encode(buffer, strict);
            var decoded = RecipeCodecs.INGREDIENT_STREAM.decode(buffer);
            assertTrue(decoded.test(sample));
            assertFalse(decoded.test(different));
        } finally { buffer.release(); }
    }

    @Test void actualAe15InscriberJsonAndWirePreserveEnchantedPresses() throws Exception {
        var path = Path.of("src/generated/resources/data/useless_mod/recipes/endless_beaf_item.json");
        String text = Files.readString(path).replace("useless_mod:useless_ingot_tier_5", "minecraft:iron_ingot")
                .replace("useless_mod:endless_beaf_item", "minecraft:diamond");
        JsonObject json = JsonParser.parseString(text).getAsJsonObject();
        var id = new ResourceLocation("test", "entropy_b7b13_beef");
        var recipe = InscriberRecipeSerializer.INSTANCE.fromJson(id, json);
        assertInstanceOf(EnchantedItemIngredient.class, recipe.getTopOptional());
        assertTrue(recipe.getTopOptional().test(pickaxe(5)));
        assertFalse(recipe.getTopOptional().test(pickaxe(2)));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            InscriberRecipeSerializer.INSTANCE.toNetwork(buffer, recipe);
            var decoded = InscriberRecipeSerializer.INSTANCE.fromNetwork(id, buffer);
            assertInstanceOf(EnchantedItemIngredient.class, decoded.getBottomOptional());
            assertTrue(decoded.getTopOptional().test(pickaxe(5)));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void entropyRecipeWireKeepsLongAmountsNativeIngredientsAndRetentionFlag() {
        var id = new ResourceLocation("test", "entropy_b7b13_long");
        var recipe = new EntropyTransmutationRecipe(id,
                List.of(new CountedIngredient(EnchantedItemIngredient.of(pickaxe(3)), 3_000_000_000L, false)),
                new ItemStack(Items.FURNACE));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            EntropyTransmutationRecipeSerializer.INSTANCE.toNetwork(buffer, recipe);
            var decoded = EntropyTransmutationRecipeSerializer.INSTANCE.fromNetwork(id, buffer);
            assertEquals(3_000_000_000L, decoded.inputs().get(0).count());
            assertFalse(decoded.inputs().get(0).consumed());
            assertInstanceOf(EnchantedItemIngredient.class, decoded.inputs().get(0).ingredient());
            assertTrue(decoded.inputs().get(0).ingredient().test(pickaxe(4)));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void recipeManagerPathWinsAndOutputCopiesCannotMutateTheRecipe() {
        var id = new ResourceLocation("test", "entropy_b7b13_authority");
        var json = JsonParser.parseString("""
                {"id":"test:untrusted","inputs":[{"ingredient":{"item":"minecraft:iron_ingot"},"count":8}],
                 "output":{"item":"minecraft:furnace","count":1}}
                """).getAsJsonObject();
        var recipe = EntropyTransmutationRecipeSerializer.INSTANCE.fromJson(id, json);
        assertEquals(id, recipe.id());
        assertEquals("test:untrusted", json.get("id").getAsString());
        recipe.output().setCount(0);
        assertEquals(1, recipe.output().getCount());
        assertFalse(recipe.matches(null, null));
        assertFalse(recipe.canCraftInDimensions(3, 3));
    }

    @Test void invalidQuantitiesOutputsAndMismatchedPacketIdsAreRejected() {
        var id = new ResourceLocation("test", "entropy_b7b13_invalid");
        var input = new CountedIngredient(Ingredient.of(Items.IRON_INGOT), 1);
        assertThrows(IllegalArgumentException.class,
                () -> new EntropyTransmutationRecipe(id, List.of(), new ItemStack(Items.FURNACE)));
        assertThrows(IllegalArgumentException.class, () -> new EntropyTransmutationRecipe(id,
                List.of(new CountedIngredient(Ingredient.of(Items.IRON_INGOT), -1)), new ItemStack(Items.FURNACE)));
        assertThrows(IllegalArgumentException.class,
                () -> new EntropyTransmutationRecipe(id, List.of(input), new ItemStack(Items.DIAMOND)));
        assertThrows(IllegalArgumentException.class,
                () -> new EntropyTransmutationRecipe(id, List.of(input), new ItemStack(Items.FURNACE, 2)));
        ItemStack tagged = new ItemStack(Items.FURNACE);
        tagged.getOrCreateTag().putBoolean("BlockEntityTag", true);
        assertThrows(IllegalArgumentException.class, () -> new EntropyTransmutationRecipe(id, List.of(input), tagged));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            EntropyTransmutationRecipe.STREAM_CODEC.encode(buffer,
                    new EntropyTransmutationRecipe(id, List.of(input), new ItemStack(Items.FURNACE)));
            assertThrows(IllegalArgumentException.class, () -> EntropyTransmutationRecipeSerializer.INSTANCE.fromNetwork(
                    new ResourceLocation("test", "wrong"), buffer));
        } finally { buffer.release(); }
    }
}
