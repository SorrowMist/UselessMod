package com.sorrowmist.useless.compat.jei;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.CatalystParallelManager;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipeSerializer;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient;
import io.netty.buffer.Unpooled;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Layout checks also run with the pack's older JEI jar substituted on the test runtime classpath. */
class UsefulIngotJeiTest {
    private static final ResourceLocation ID = new ResourceLocation("useless_mod", "advanced_alloy/ingot/useful_ingot");

    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private static AdvancedAlloyFurnaceRecipe generatedRecipe() throws Exception {
        JsonObject json = JsonParser.parseString(Files.readString(Path.of(
                "src/generated/resources/data/useless_mod/recipes/advanced_alloy/ingot/useful_ingot.json")))
                .getAsJsonObject();
        assertEquals("useless_mod:advanced_alloy_furnace", json.get("type").getAsString());
        assertEquals(ID.toString(), json.get("id").getAsString());
        var inputs = json.getAsJsonArray("ingredients");
        assertEquals(6, inputs.size());
        assertEquals("useless_mod:possible_useful_ingot", inputs.get(0).getAsJsonObject()
                .getAsJsonObject("ingredient").get("item").getAsString());
        assertEquals("useless_mod:useful_ingot", json.getAsJsonArray("outputs").get(0)
                .getAsJsonObject().get("id").getAsString());
        // Plain JUnit has no mod registry events. Only these two mod items use vanilla stand-ins;
        // all counts, block ingredients, fluid JSON and serializer code remain the shipped data.
        inputs.get(0).getAsJsonObject().getAsJsonObject("ingredient").addProperty("item", "minecraft:copper_ingot");
        json.getAsJsonArray("outputs").get(0).getAsJsonObject().addProperty("id", "minecraft:netherite_ingot");
        return AdvancedAlloyFurnaceRecipeSerializer.INSTANCE.fromJson(ID, json);
    }

    @Test void shippedUsefulIngotRecipeLoadsAndSynchronizesWithoutLosingQuantities() throws Exception {
        var recipe = generatedRecipe();
        assertEquals(List.of(64L, 1024L, 1024L, 1024L, 1024L, 1024L),
                recipe.inputs().stream().map(input -> input.count()).toList());
        var blocks = List.of(Items.IRON_BLOCK, Items.GOLD_BLOCK, Items.DIAMOND_BLOCK,
                Items.EMERALD_BLOCK, Items.NETHERITE_BLOCK);
        for (int i = 0; i < blocks.size(); i++) {
            assertTrue(recipe.inputs().get(i + 1).ingredient().test(blocks.get(i).getDefaultInstance()));
        }
        assertEquals(1, recipe.outputs().size());
        assertTrue(recipe.outputs().get(0).is(Items.NETHERITE_INGOT));
        assertEquals(1, recipe.outputs().get(0).getCount());
        assertEquals(1, recipe.inputFluids().size());
        assertEquals(10_000_000L, recipe.inputFluids().get(0).amount());
        assertTrue(recipe.inputFluids().get(0).ingredient().test(new FluidStack(Fluids.LAVA, 1)));
        assertEquals(10_000_000L, recipe.energy());
        assertEquals(100, recipe.processTime());
        assertEquals(AlloyFurnaceMode.NORMAL, recipe.mode());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AdvancedAlloyFurnaceRecipeSerializer.INSTANCE.toNetwork(buffer, recipe);
            var synced = AdvancedAlloyFurnaceRecipeSerializer.INSTANCE.fromNetwork(ID, buffer);
            assertEquals(ID, synced.id());
            assertEquals(recipe.inputs().stream().map(input -> input.count()).toList(),
                    synced.inputs().stream().map(input -> input.count()).toList());
            assertEquals(recipe.inputFluids(), synced.inputFluids());
            assertEquals(1, synced.outputs().get(0).getCount());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test void usefulIngotLayoutRetainsOutputAndTenMillionMbLava() throws Exception {
        var recipe = generatedRecipe();
        var slots = layout(recipe);
        var lava = slots.stream().filter(slot -> slot.y == 55 && slot.role == RecipeIngredientRole.INPUT)
                .findFirst().orElseThrow().builder;
        verify(lava).setFluidRenderer(10_000_000L, false, 69, 16);
        var fluid = ArgumentCaptor.forClass(FluidStack.class);
        verify(lava).addIngredient(eq(ForgeTypes.FLUID_STACK), fluid.capture());
        assertEquals(Fluids.LAVA, fluid.getValue().getFluid());
        assertEquals(10_000_000, fluid.getValue().getAmount());
        assertEquals(6, slots.stream().filter(slot -> slot.y < 55 && slot.role == RecipeIngredientRole.INPUT).count());
        var output = slots.stream().filter(slot -> slot.role == RecipeIngredientRole.OUTPUT).findFirst().orElseThrow();
        verify(output.builder).addItemStack(recipe.outputs().get(0));
    }

    @Test void fluidAlternativesAndOutputsKeepNbtAndLongAmountTooltip() {
        var result = new FluidStack(Fluids.WATER, 2000);
        result.getOrCreateTag().putInt("grade", 7);
        var input = new LongSizedFluidIngredient(FluidIngredient.any(List.of(
                FluidIngredient.single(Fluids.LAVA), FluidIngredient.nbt(true, result))), 5_000_000_000L);
        var recipe = new AdvancedAlloyFurnaceRecipe(ID, List.of(), List.of(input), List.of(),
                List.of(), List.of(result), List.of(), 10_000_000L, 100, Ingredient.EMPTY, 0,
                List.of(), AlloyFurnaceMode.NORMAL, AdvancedAlloyFurnaceRecipe.NO_EXPLICIT_TIER);
        var slots = layout(recipe);
        var inputSlot = slots.stream().filter(slot -> slot.role == RecipeIngredientRole.INPUT).findFirst().orElseThrow().builder;
        verify(inputSlot).setFluidRenderer((long) Integer.MAX_VALUE, false, 69, 16);
        var alternatives = ArgumentCaptor.forClass(FluidStack.class);
        verify(inputSlot, times(2)).addIngredient(eq(ForgeTypes.FLUID_STACK), alternatives.capture());
        assertEquals(List.of(Fluids.LAVA, Fluids.WATER), alternatives.getAllValues().stream().map(FluidStack::getFluid).toList());
        assertTrue(alternatives.getAllValues().stream().allMatch(stack -> stack.getAmount() == Integer.MAX_VALUE));
        assertEquals(7, alternatives.getAllValues().get(1).getTag().getInt("grade"));
        assertEquals(5_000_000_000L, recipe.inputFluids().get(0).amount());
        var callback = ArgumentCaptor.forClass(IRecipeSlotRichTooltipCallback.class);
        verify(inputSlot).addRichTooltipCallback(callback.capture());
        var tooltip = mock(ITooltipBuilder.class);
        callback.getValue().onRichTooltip(mock(IRecipeSlotView.class), tooltip);
        var text = ArgumentCaptor.forClass(Component.class);
        verify(tooltip).add(text.capture());
        var translation = assertInstanceOf(TranslatableContents.class, text.getValue().getContents());
        assertEquals("jei.useless_mod.tooltip.amount.mb", translation.getKey());
        assertArrayEquals(new Object[] {"5.00G"}, translation.getArgs());
        var outputSlot = slots.stream().filter(slot -> slot.role == RecipeIngredientRole.OUTPUT).findFirst().orElseThrow().builder;
        var displayed = ArgumentCaptor.forClass(FluidStack.class);
        verify(outputSlot).addIngredient(eq(ForgeTypes.FLUID_STACK), displayed.capture());
        assertEquals(2000, displayed.getValue().getAmount());
        assertEquals(7, displayed.getValue().getTag().getInt("grade"));
        assertNotSame(result, displayed.getValue());
        displayed.getValue().getOrCreateTag().putInt("grade", 9);
        assertEquals(7, result.getTag().getInt("grade"));
    }

    private static List<Slot> layout(AdvancedAlloyFurnaceRecipe recipe) {
        var slots = new ArrayList<Slot>();
        var builder = mock(IRecipeLayoutBuilder.class);
        when(builder.addSlot(any(), anyInt(), anyInt())).thenAnswer(call -> {
            var slot = mock(IRecipeSlotBuilder.class, RETURNS_SELF);
            slots.add(new Slot(call.getArgument(0), call.getArgument(2), slot));
            return slot;
        });
        // Exercise the real setRecipe without constructing a Minecraft window or mod block icon.
        var category = mock(AdvancedAlloyFurnaceRecipeCategory.class, CALLS_REAL_METHODS);
        var entry = new AlloyFurnaceRecipeCatalog.Entry(new AlloyFurnaceRecipeIdentity(ID, "layout-check"), recipe, "core");
        try (var catalyst = mockStatic(CatalystParallelManager.class)) {
            assertDoesNotThrow(() -> category.setRecipe(builder, entry, mock(IFocusGroup.class)));
        }
        return slots;
    }

    private record Slot(RecipeIngredientRole role, int y, IRecipeSlotBuilder builder) { }
}
