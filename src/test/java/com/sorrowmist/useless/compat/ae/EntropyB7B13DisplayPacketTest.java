package com.sorrowmist.useless.compat.ae;

import appeng.integration.modules.jei.widgets.View;
import appeng.recipes.transform.TransformCircumstance;
import appeng.recipes.transform.TransformRecipe;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.mixin.ae2.TransformCategoryMergeMixin;
import com.sorrowmist.useless.network.EntropyTransmutationEffectPacket;
import com.sorrowmist.useless.network.EntropyTransmutationPreviewPacket;
import com.sorrowmist.useless.network.EntropyTransmutationPreviewRequestPacket;
import io.netty.buffer.Unpooled;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EntropyB7B13DisplayPacketTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void allThreePacketsRoundTripRequestIdentityDimensionAndImmutablePosition() {
        var dimension = new ResourceLocation("test", "entropy_b7b13_dimension");
        var mutable = new BlockPos.MutableBlockPos(1, 80, -7);
        var request = new EntropyTransmutationPreviewRequestPacket(Integer.MAX_VALUE, dimension, mutable);
        var response = new EntropyTransmutationPreviewPacket(Integer.MAX_VALUE, dimension, mutable, -1);
        var effect = new EntropyTransmutationEffectPacket(dimension, mutable);
        mutable.set(99, 0, 99);
        assertEquals(new BlockPos(1, 80, -7), request.pos());
        assertEquals(request.pos(), response.pos());
        assertEquals(request.pos(), effect.pos());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            EntropyTransmutationPreviewRequestPacket.STREAM_CODEC.encode(buffer, request);
            EntropyTransmutationPreviewPacket.STREAM_CODEC.encode(buffer, response);
            EntropyTransmutationEffectPacket.STREAM_CODEC.encode(buffer, effect);
            assertEquals(request, EntropyTransmutationPreviewRequestPacket.STREAM_CODEC.decode(buffer));
            assertEquals(response, EntropyTransmutationPreviewPacket.STREAM_CODEC.decode(buffer));
            assertEquals(effect, EntropyTransmutationEffectPacket.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    private static View merge(TransformRecipe recipe, View original) throws Exception {
        var callback = new CallbackInfoReturnable<View>("getView", true, original);
        var hook = TransformCategoryMergeMixin.class.getDeclaredMethod("useless$mergeTransformInputs",
                TransformRecipe.class, CallbackInfoReturnable.class);
        hook.setAccessible(true);
        hook.invoke(new TransformCategoryMergeMixin() {}, recipe, callback);
        return callback.getReturnValue();
    }

    @Test void ae15ViewBuildsOneCountedInputPerUniqueMaterialInsteadOfNinetySixSlots() throws Exception {
        var inputs = NonNullList.<Ingredient>create();
        for (var item : new net.minecraft.world.item.Item[] {Items.MUTTON, Items.BEEF, Items.PORKCHOP,
                Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND}) {
            for (int i = 0; i < 16; i++) inputs.add(Ingredient.of(item));
        }
        var recipe = new TransformRecipe(new ResourceLocation("test", "entropy_b7b13_view"), inputs,
                new ItemStack(Items.IRON_BLOCK), TransformCircumstance.fluid(FluidTags.WATER));
        var builder = mock(IRecipeLayoutBuilder.class);
        var slot = mock(IRecipeSlotBuilder.class, RETURNS_SELF);
        when(builder.addSlot(any(), anyInt(), anyInt())).thenReturn(slot);
        View original = mock(View.class);
        View merged = merge(recipe, original);
        assertNotSame(original, merged);
        merged.buildSlots(builder);
        verify(builder, times(6)).addSlot(eq(RecipeIngredientRole.INPUT), anyInt(), anyInt());
        verify(builder, times(1)).addSlot(eq(RecipeIngredientRole.CATALYST), eq(56), eq(24));
        verify(builder, times(1)).addSlot(eq(RecipeIngredientRole.OUTPUT), eq(106), eq(24));
        verify(slot, times(6)).addItemStacks(argThat(stacks -> stacks.size() == 1 && stacks.get(0).getCount() == 16));
        verify(original, never()).buildSlots(any());
    }

    @Test void nonRepeatedAeRecipesKeepTheirOriginalView() throws Exception {
        var inputs = NonNullList.<Ingredient>create();
        inputs.add(Ingredient.of(Items.DIAMOND));
        inputs.add(Ingredient.of(Items.IRON_INGOT));
        var recipe = new TransformRecipe(new ResourceLocation("test", "entropy_b7b13_original_view"), inputs,
                new ItemStack(Items.GOLD_INGOT), TransformCircumstance.fluid(FluidTags.WATER));
        View original = mock(View.class);
        assertSame(original, merge(recipe, original));
    }
}
