package com.sorrowmist.useless.network;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.OptionalIntegration;
import com.sorrowmist.useless.compat.forge1201.component.ComponentAccess;
import com.sorrowmist.useless.compat.forge1201.network.IPayloadContext;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.core.component.UComponents;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RitualSatchelPlacePacketTest {
    private static final ResourceLocation ID = new ResourceLocation("occultism", "craft_foliot");
    private static final BlockPos ANCHOR = new BlockPos(12, 64, -8);
    private static Item toolItem;

    @BeforeAll static void bootstrap() {
        InventoryTestBootstrap.initialize();
        toolItem = InventoryTestBootstrap.registerItem("satchel_packet_tool", EndlessBeafItem::new);
    }

    @Test void codecRoundTripsEveryNativeRotationAndDetachesMutableAnchor() {
        for (Rotation rotation : Rotation.values()) {
            var mutable = new BlockPos.MutableBlockPos(12, 64, -8);
            var packet = new RitualSatchelPlacePacket(ID, mutable, rotation);
            mutable.set(200, 1, 200);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            var roundTrip = new FriendlyByteBuf(Unpooled.buffer());
            try {
                RitualSatchelPlacePacket.STREAM_CODEC.encode(buffer, packet);
                var decoded = RitualSatchelPlacePacket.STREAM_CODEC.decode(buffer);
                assertFalse(buffer.isReadable());
                RitualSatchelPlacePacket.STREAM_CODEC.encode(roundTrip, decoded);
                assertEquals(ID, roundTrip.readResourceLocation());
                assertEquals(ANCHOR, roundTrip.readBlockPos());
                assertEquals(rotation, roundTrip.readEnum(Rotation.class));
                assertFalse(roundTrip.isReadable());
            } finally { buffer.release(); roundTrip.release(); }
        }
    }

    @Test void missingDependenciesAndForbiddenPlayerStatesNeverResolveTheOptionalClass() {
        for (int failure = 0; failure < 7; failure++) {
            var s = new Scenario();
            var mods = mock(ModList.class);
            when(mods.isLoaded("occultism")).thenReturn(failure != 0);
            when(mods.isLoaded("modonomicon")).thenReturn(failure != 1);
            switch (failure) {
                case 2 -> when(s.player.isAlive()).thenReturn(false);
                case 3 -> when(s.player.isSpectator()).thenReturn(true);
                case 4 -> when(s.player.mayBuild()).thenReturn(false);
                case 5 -> when(s.player.isShiftKeyDown()).thenReturn(true);
                case 6 -> ComponentAccess.set(s.tool, UComponents.BeefRitualSatchelComponent.get(), false);
                default -> { }
            }
            try (var modList = mockStatic(ModList.class); var integration = mockStatic(OptionalIntegration.class)) {
                modList.when(ModList::get).thenReturn(mods);
                RitualSatchelPlacePacket.handle(packet(), s.context);
                integration.verifyNoInteractions();
                verify(s.player.containerMenu, never()).broadcastChanges();
            }
        }
    }

    @Test void staleHandEntryCannotAuthorizeUsingACopyOrInventoryOnlyTool() {
        var s = new Scenario();
        when(s.player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(s.tool.copy());
        try (var modList = mockStatic(ModList.class); var integration = mockStatic(OptionalIntegration.class)) {
            var mods = mock(ModList.class);
            when(mods.isLoaded(anyString())).thenReturn(true);
            modList.when(ModList::get).thenReturn(mods);
            RitualSatchelPlacePacket.handle(packet(), s.context);
            integration.verifyNoInteractions();
        }
    }

    @Test void authorizedPacketUsesTheExistingGuardedServerEntrypointAndBroadcastsInventoryChanges() {
        var s = new Scenario();
        try (var modList = mockStatic(ModList.class); var integration = mockStatic(OptionalIntegration.class)) {
            var mods = mock(ModList.class);
            when(mods.isLoaded(anyString())).thenReturn(true);
            modList.when(ModList::get).thenReturn(mods);
            RitualSatchelPlacePacket.handle(packet(), s.context);
            integration.verify(() -> OptionalIntegration.call("occultism",
                    "com.sorrowmist.useless.compat.occultism.RitualSatchelCompat", "placeFromAe", Void.class, null,
                    new Class<?>[]{ServerLevel.class, ServerPlayer.class, ItemStack.class, ResourceLocation.class, BlockPos.class, Rotation.class},
                    s.level, s.player, s.tool, ID, ANCHOR, Rotation.NONE));
            verify(s.player.containerMenu).broadcastChanges();
        }
    }

    private static RitualSatchelPlacePacket packet() { return new RitualSatchelPlacePacket(ID, ANCHOR, Rotation.NONE); }

    private static final class Scenario {
        final IPayloadContext context = mock(IPayloadContext.class);
        final ServerPlayer player = mock(ServerPlayer.class);
        final ServerLevel level = mock(ServerLevel.class);
        final ItemStack tool = new ItemStack(toolItem);

        Scenario() {
            ComponentAccess.set(tool, UComponents.BeefRitualSatchelComponent.get(), true);
            player.containerMenu = mock(AbstractContainerMenu.class);
            when(context.player()).thenReturn(player);
            when(context.enqueueWork(any())).thenAnswer(call -> {
                call.<Runnable>getArgument(0).run();
                return CompletableFuture.completedFuture(null);
            });
            when(player.level()).thenReturn(level);
            when(player.isAlive()).thenReturn(true);
            when(player.mayBuild()).thenReturn(true);
            when(player.getMainHandItem()).thenReturn(tool);
            when(player.getOffhandItem()).thenReturn(ItemStack.EMPTY);
            when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(tool);
        }
    }
}
