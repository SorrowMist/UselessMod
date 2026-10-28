package com.sorrowmist.useless.network;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.forge1201.network.IPayloadContext;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.content.menus.StaffLinkMenu;
import com.sorrowmist.useless.utils.UselessItemUtils;
import com.sorrowmist.useless.world.stafflink.StaffLinkManager;
import com.sorrowmist.useless.world.stafflink.StaffLinkNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffLinkCyclePacketTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void authorizedOpenMenuCyclesBothDirectionsWithoutRequiringAStaffInHand() {
        for (int delta : new int[]{-1, 1}) {
            ServerPlayer player = mock(ServerPlayer.class);
            StaffLinkMenu menu = mock(StaffLinkMenu.class);
            player.containerMenu = menu;
            UUID current = UUID.randomUUID(), owner = UUID.randomUUID();
            StaffLinkNetwork next = new StaffLinkNetwork(UUID.randomUUID());
            when(menu.getNetworkId()).thenReturn(current);
            try (var tools = mockStatic(UselessItemUtils.class);
                 var manager = mockStatic(StaffLinkManager.class);
                 var sending = mockStatic(PacketDistributor.class)) {
                tools.when(() -> UselessItemUtils.findTargetToolInHands(player)).thenReturn(Optional.empty());
                manager.when(() -> StaffLinkManager.canAccess(null, player, current)).thenReturn(true);
                manager.when(() -> StaffLinkManager.ownerIdOf(player)).thenReturn(owner);
                manager.when(() -> StaffLinkManager.cycleActive(null, owner, delta)).thenReturn(true);
                manager.when(() -> StaffLinkManager.networkIds(null, owner)).thenReturn(List.of(current, next.id()));
                manager.when(() -> StaffLinkManager.activeIndex(null, owner)).thenReturn(1);
                manager.when(() -> StaffLinkManager.activeNetwork(null, owner)).thenReturn(next);
                handle(player, delta);
                manager.verify(() -> StaffLinkManager.canAccess(null, player, current));
                manager.verify(() -> StaffLinkManager.cycleActive(null, owner, delta));
                verify(menu).setNetworkId(next.id());
                sending.verify(() -> PacketDistributor.sendToPlayer(player, new StaffLinkSyncPacket(next, 1, 2)));
                tools.verifyNoInteractions();
            }
        }
    }

    @Test
    void menuWhoseNetworkIsNoLongerAccessibleCannotCycleAnyOwnerNetwork() {
        ServerPlayer player = mock(ServerPlayer.class);
        StaffLinkMenu menu = mock(StaffLinkMenu.class);
        player.containerMenu = menu;
        UUID current = UUID.randomUUID();
        when(menu.getNetworkId()).thenReturn(current);
        try (var manager = mockStatic(StaffLinkManager.class);
             var sending = mockStatic(PacketDistributor.class)) {
            manager.when(() -> StaffLinkManager.canAccess(null, player, current)).thenReturn(false);
            handle(player, 1);
            manager.verify(() -> StaffLinkManager.canAccess(null, player, current));
            manager.verify(() -> StaffLinkManager.cycleActive(any(), any(), anyInt()), never());
            verify(menu, never()).setNetworkId(any());
            sending.verifyNoInteractions();
        }
    }

    @Test
    void failedCycleNeverRebindsTheMenuOrSendsAPhantomSuccess() {
        ServerPlayer player = mock(ServerPlayer.class);
        StaffLinkMenu menu = mock(StaffLinkMenu.class);
        player.containerMenu = menu;
        UUID current = UUID.randomUUID(), owner = UUID.randomUUID();
        when(menu.getNetworkId()).thenReturn(current);
        try (var manager = mockStatic(StaffLinkManager.class);
             var sending = mockStatic(PacketDistributor.class)) {
            manager.when(() -> StaffLinkManager.canAccess(null, player, current)).thenReturn(true);
            manager.when(() -> StaffLinkManager.ownerIdOf(player)).thenReturn(owner);
            manager.when(() -> StaffLinkManager.cycleActive(null, owner, -1)).thenReturn(false);
            handle(player, -1);
            verify(menu, never()).setNetworkId(any());
            verify(player, never()).displayClientMessage(any(), anyBoolean());
            sending.verifyNoInteractions();
        }
    }

    @Test
    void otherMenusDoNotGrantTheOpenWirelessMenuAuthorization() {
        ServerPlayer player = mock(ServerPlayer.class);
        player.containerMenu = mock(AbstractContainerMenu.class);
        try (var tools = mockStatic(UselessItemUtils.class);
             var manager = mockStatic(StaffLinkManager.class)) {
            tools.when(() -> UselessItemUtils.findTargetToolInHands(player)).thenReturn(Optional.empty());
            handle(player, 1);
            tools.verify(() -> UselessItemUtils.findTargetToolInHands(player));
            manager.verifyNoInteractions();
        }
    }

    @Test
    void outsideMenuStillRequiresTheEnabledStaffMode() {
        ServerPlayer player = mock(ServerPlayer.class);
        ItemStack staff = mock(ItemStack.class);
        when(staff.getItem()).thenReturn(mock(EndlessBeafItem.class));
        UUID owner = UUID.randomUUID();
        try (var tools = mockStatic(UselessItemUtils.class);
             var mode = mockStatic(EndlessBeafItem.class);
             var manager = mockStatic(StaffLinkManager.class)) {
            tools.when(() -> UselessItemUtils.findTargetToolInHands(player)).thenReturn(Optional.of(
                    new SimpleImmutableEntry<>(staff, InteractionHand.MAIN_HAND)));
            handle(player, 1);
            manager.verifyNoInteractions();
            mode.when(() -> EndlessBeafItem.isStaffLinkEnabled(staff)).thenReturn(true);
            manager.when(() -> StaffLinkManager.ownerIdOf(player)).thenReturn(owner);
            handle(player, -1);
            manager.verify(() -> StaffLinkManager.cycleActive(null, owner, -1));
        }
    }

    private static void handle(ServerPlayer player, int delta) {
        IPayloadContext context = mock(IPayloadContext.class);
        when(context.player()).thenReturn(player);
        when(context.enqueueWork(any())).thenAnswer(call -> {
            call.<Runnable>getArgument(0).run();
            return CompletableFuture.completedFuture(null);
        });
        assertDoesNotThrow(() -> StaffLinkCyclePacket.handle(new StaffLinkCyclePacket(delta), context));
    }
}
