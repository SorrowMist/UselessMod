package com.sorrowmist.useless.network;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.content.stafflink.LinkFlow;
import com.sorrowmist.useless.content.stafflink.LinkMedium;
import com.sorrowmist.useless.content.stafflink.StaffLinkRoute;
import com.sorrowmist.useless.world.stafflink.StaffLinkNetwork;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StaffLinkCommandContextTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private final GlobalPos a = GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 64, 0));
    private final GlobalPos b = GlobalPos.of(Level.OVERWORLD, new BlockPos(2, 64, 0));
    private final GlobalPos c = GlobalPos.of(Level.OVERWORLD, new BlockPos(3, 64, 0));

    private StaffLinkNetwork network() {
        var network = new StaffLinkNetwork(UUID.randomUUID());
        for (GlobalPos anchor : List.of(a, b, c))
            network.putRoute(new StaffLinkRoute(anchor, 0, false, LinkFlow.RELEASE, LinkMedium.ITEM,
                    16, 5, null, 0, List.of()));
        return network;
    }

    private StaffLinkReorderPacket move(StaffLinkNetwork network, GlobalPos anchor, int index) {
        return new StaffLinkReorderPacket(network.id(), anchor, index,
                StaffLinkReorderPacket.orderToken(network.anchors()));
    }

    @Test void packetRoundTripsPreserveTheOriginatingNetworkAndOrder() {
        var network = network();
        var reorder = move(network, a, 2);
        var rename = new StaffLinkRenamePacket(network.id(), a, "已保存的容器");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            StaffLinkReorderPacket.STREAM_CODEC.encode(buffer, reorder);
            assertEquals(reorder, StaffLinkReorderPacket.STREAM_CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
            buffer.clear();
            StaffLinkRenamePacket.STREAM_CODEC.encode(buffer, rename);
            assertEquals(rename, StaffLinkRenamePacket.STREAM_CODEC.decode(buffer));
            assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }

    @Test void networkSwitchCannotRedirectCommandsEvenWhenBothNetworksBindTheSameBlock() {
        var first = network();
        var second = network();
        assertFalse(move(first, a, 2).applyTo(second));
        assertFalse(new StaffLinkRenamePacket(first.id(), a, "wrong network").applyTo(second));
        assertEquals(List.of(a, b, c), second.anchors());
        assertNull(second.anchorName(a));
    }

    @Test void staleAbsoluteIndicesAreRejectedRatherThanAppliedToATeammatesNewOrder() {
        var network = network();
        var request = move(network, c, 1);
        network.moveAnchorTo(b, 0);
        assertFalse(request.applyTo(network));
        assertEquals(List.of(b, a, c), network.anchors());
    }

    @Test void orderTokenIsIndependentOfJvmObjectIdentityAndIncludesDimensionPositionAndOrder() {
        assertEquals(StaffLinkReorderPacket.orderToken(List.of(a, b)),
                StaffLinkReorderPacket.orderToken(List.of(GlobalPos.of(Level.OVERWORLD, a.pos().immutable()), b)));
        assertNotEquals(StaffLinkReorderPacket.orderToken(List.of(a, b)), StaffLinkReorderPacket.orderToken(List.of(b, a)));
        assertNotEquals(StaffLinkReorderPacket.orderToken(List.of(a)), StaffLinkReorderPacket.orderToken(List.of(b)));
        assertNotEquals(StaffLinkReorderPacket.orderToken(List.of(a)),
                StaffLinkReorderPacket.orderToken(List.of(GlobalPos.of(Level.NETHER, a.pos()))));
    }

    @Test void validCommandsRetainServerClampingNamesAndRouteOwnership() {
        var network = network();
        assertTrue(new StaffLinkRenamePacket(network.id(), a, "  Chest  ").applyTo(network));
        assertTrue(move(network, a, Integer.MAX_VALUE).applyTo(network));
        assertEquals(List.of(b, c, a), network.anchors());
        assertEquals("Chest", network.anchorName(a));
        assertNotNull(network.routeAt(a, 0));
        assertTrue(new StaffLinkRenamePacket(network.id(), a, "").applyTo(network));
        assertNull(network.anchorName(a));
    }

    @Test void unboundSourcesAndNoOpMovesNeverChangeTheNetwork() {
        var network = network();
        var absent = GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO);
        assertFalse(move(network, absent, 0).applyTo(network));
        assertFalse(move(network, a, 0).applyTo(network));
        assertFalse(new StaffLinkRenamePacket(network.id(), absent, "not bound").applyTo(network));
        assertEquals(List.of(a, b, c), network.anchors());
        assertNull(network.anchorName(absent));
    }
}
