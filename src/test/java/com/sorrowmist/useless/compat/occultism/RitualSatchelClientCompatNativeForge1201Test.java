package com.sorrowmist.useless.compat.occultism;

import com.klikli_dev.modonomicon.client.render.MultiblockPreviewRenderer;
import com.klikli_dev.modonomicon.multiblock.DenseMultiblock;
import com.klikli_dev.modonomicon.multiblock.matcher.BlockMatcher;
import com.klikli_dev.occultism.crafting.recipe.RitualRecipe;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.component.ComponentAccess;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.network.RitualSatchelPlacePacket;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RitualSatchelClientCompatNativeForge1201Test {
    private static final ResourceLocation ID = new ResourceLocation("occultism", "client_satchel_test");
    private static Item toolItem;

    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        toolItem = InventoryTestBootstrap.registerItem("satchel_client_tool", EndlessBeafItem::new);
    }

    @Test void actual1793ViewOffsetAcceptsTheSupportBlockAndSendsOnlyIdAnchorRotation() throws ReflectiveOperationException {
        Level level = mock(Level.class);
        // Only the side flag needs a client fixture; no Minecraft singleton/render loop is initialized.
        var side = Level.class.getDeclaredField("isClientSide");
        side.setAccessible(true);
        side.setBoolean(level, true);
        Player player = player(level);
        RecipeManager recipes = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(recipes);
        when(recipes.getRecipes()).thenReturn(List.of(new RitualRecipe(new ResourceLocation("occultism", "client_satchel_recipe"), ID,
                new ResourceLocation("occultism", "craft"), new ItemStack(Items.PAPER), new ItemStack(Items.DIAMOND),
                null, null, Ingredient.of(Items.BOOK), NonNullList.create(), 20, -1, null, null, "", Ingredient.EMPTY, "")));
        // Nonzero offset and rotation make using false,false instead of the renderer's true,false observable.
        var multiblock = new DenseMultiblock(new String[][]{{"0M"}}, new java.util.HashMap<>(Map.of(
                '0', BlockMatcher.from(Blocks.STONE), 'M', BlockMatcher.from(Blocks.DIRT))));
        multiblock.setId(ID);
        multiblock.offsetView(2, 1, -3);
        BlockPos anchor = new BlockPos(10, 64, 10);
        Rotation rotation = Rotation.CLOCKWISE_90;
        BlockPos target = multiblock.simulate(level, anchor, rotation, true, false).getSecond().iterator().next().getWorldPosition();
        assertNotEquals(target, multiblock.simulate(level, anchor, rotation, false, false).getSecond().iterator().next().getWorldPosition());
        boolean previous = MultiblockPreviewRenderer.hasMultiblock;
        try (var preview = mockStatic(MultiblockPreviewRenderer.class); var packets = mockStatic(PacketDistributor.class)) {
            MultiblockPreviewRenderer.hasMultiblock = true;
            preview.when(MultiblockPreviewRenderer::isAnchored).thenReturn(true);
            preview.when(MultiblockPreviewRenderer::getMultiblock).thenReturn(multiblock);
            preview.when(MultiblockPreviewRenderer::getStartPos).thenReturn(anchor);
            preview.when(MultiblockPreviewRenderer::getFacingRotation).thenReturn(rotation);
            packets.when(() -> PacketDistributor.sendToServer(any(RitualSatchelPlacePacket.class))).thenAnswer(call -> {
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    RitualSatchelPlacePacket.STREAM_CODEC.encode(buffer, call.getArgument(0));
                    assertEquals(ID, buffer.readResourceLocation());
                    assertEquals(anchor, buffer.readBlockPos());
                    assertEquals(rotation, buffer.readEnum(Rotation.class));
                    assertFalse(buffer.isReadable());
                } finally { buffer.release(); }
                return null;
            });
            assertTrue(RitualSatchelClientCompat.trySendPlacement(level, player, target.below()));
            packets.verify(() -> PacketDistributor.sendToServer(any(RitualSatchelPlacePacket.class)));
            assertFalse(RitualSatchelClientCompat.trySendPlacement(level, player, anchor.east(100)));
            preview.when(MultiblockPreviewRenderer::isAnchored).thenReturn(false);
            assertFalse(RitualSatchelClientCompat.trySendPlacement(level, player, target));
            packets.verifyNoMoreInteractions();
            verify(level, never()).setBlock(any(), any(), anyInt());
        } finally { MultiblockPreviewRenderer.hasMultiblock = previous; }
    }

    @Test void serverSideInvocationNeverReadsClientPreviewOrSendsAPacket() {
        Level server = mock(Level.class);
        try (var preview = mockStatic(MultiblockPreviewRenderer.class); var packets = mockStatic(PacketDistributor.class)) {
            assertFalse(RitualSatchelClientCompat.trySendPlacement(server, player(server), BlockPos.ZERO));
            preview.verifyNoInteractions();
            packets.verifyNoInteractions();
        }
    }

    private static Player player(Level level) {
        Player player = mock(Player.class);
        ItemStack tool = new ItemStack(toolItem);
        ComponentAccess.set(tool, UComponents.BeefRitualSatchelComponent.get(), true);
        when(player.level()).thenReturn(level);
        when(player.isAlive()).thenReturn(true);
        when(player.mayBuild()).thenReturn(true);
        when(player.getMainHandItem()).thenReturn(tool);
        when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
        when(player.getCooldowns()).thenReturn(new ItemCooldowns());
        when(level.enabledFeatures()).thenReturn(FeatureFlags.VANILLA_SET);
        return player;
    }
}
