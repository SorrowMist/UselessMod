package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.sorrowmist.useless.client.gui.StaffLinkViewLayout.*;
import static org.junit.jupiter.api.Assertions.*;

class StaffLinkAnchorDragTest {
    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private final UUID network = UUID.randomUUID();
    private final GlobalPos a = anchor(0), b = anchor(1), c = anchor(2), d = anchor(3);
    private final List<GlobalPos> order = List.of(a, b, c, d);
    private final StaffLinkAnchorDrag drag = new StaffLinkAnchorDrag();

    private static GlobalPos anchor(int i) { return GlobalPos.of(Level.OVERWORLD, new BlockPos(i, 64, 0)); }
    private double y(int row, boolean after) { return anchorRowY(row) + (after ? 18 : 4); }

    @Test
    void ordinaryClickAndSmallPointerJitterNeverCreateAReorder() {
        drag.begin(network, a, 50, y(0, false));
        drag.update(52, y(0, false) + 1);
        assertFalse(drag.moved());
        assertNull(drag.drop(network, order, order, 0));
    }

    @Test
    void downwardDropAccountsForRemovalOfTheDraggedRow() {
        drag.begin(network, a, 50, y(0, false));
        drag.update(50, y(2, true));
        var drop = drag.drop(network, order, order, 0);
        assertNotNull(drop);
        assertEquals(2, drop.targetIndex()); // [b,c,a,d], not [b,c,d,a]
        assertEquals(anchorRowY(3), drop.lineY());
        assertEquals(List.of(a, b, c, d), order, "drag previews cannot mutate the server snapshot");
    }

    @Test
    void upwardDropAndVisibleNeighbourButtonsUseTheFullListOrder() {
        drag.begin(network, d, 50, y(1, false));
        drag.update(50, y(0, false));
        var drop = drag.drop(network, order, List.of(b, d), 0);
        assertNotNull(drop);
        assertEquals(1, drop.targetIndex(), "hidden A must stay ahead of the visible target B");
        assertEquals(anchorRowY(0), drop.lineY());
    }

    @Test
    void filteredDownwardDropMovesAcrossHiddenAnchorsWithoutDroppingThem() {
        drag.begin(network, a, 50, y(0, false));
        drag.update(50, y(1, true));
        var drop = drag.drop(network, order, List.of(a, c), 0);
        assertNotNull(drop);
        assertEquals(2, drop.targetIndex());
        assertEquals(4, order.size());
    }

    @Test
    void droppingOutsideOrChangingNetworkCannotReorderAnotherList() {
        drag.begin(network, a, 50, y(0, false));
        drag.update(CONFIG_LEFT, y(2, true));
        assertNull(drag.drop(network, order, order, 0));
        drag.update(50, y(2, true));
        assertNull(drag.drop(UUID.randomUUID(), order, order, 0));
        assertNull(drag.drop(network, List.of(b, c, d), List.of(b, c, d), 0));
        assertNull(drag.drop(network, order, List.of(b, c, d), 0));
        drag.cancel();
        assertFalse(drag.pending());
        assertNull(drag.drop(network, order, order, 0));
    }

    @Test
    void blankAreaAfterLastRowMeansAfterTheLastVisibleContainer() {
        drag.begin(network, a, 50, y(0, false));
        drag.update(50, y(8, true));
        var drop = drag.drop(network, order, order, 0);
        assertNotNull(drop);
        assertEquals(3, drop.targetIndex());
        assertEquals(anchorRowY(4), drop.lineY());
    }

    @Test
    void scrollOffsetAndEdgeScrollingShareTheCurrentRowGeometry() {
        List<GlobalPos> many = IntStream.range(0, 30).mapToObj(StaffLinkAnchorDragTest::anchor).toList();
        drag.begin(network, many.get(5), 50, y(0, false));
        drag.update(50, LIST_BOTTOM - 3);
        assertEquals(1, drag.scrollDirection());
        var drop = drag.drop(network, many, many, 5);
        assertNotNull(drop);
        assertEquals(5 + LIST_VISIBLE_ROWS - 1, drop.targetIndex());
        assertTrue(drop.lineY() < LIST_BOTTOM);
        drag.update(50, LIST_FIRST_ROW_Y + 1);
        assertEquals(-1, drag.scrollDirection());
        drag.update(50, LIST_BOTTOM + 1);
        assertEquals(0, drag.scrollDirection());
    }

    @Test
    void everyDrawnRowHasExactlyTheSameHitRectangle() {
        for (int row = 0; row < LIST_VISIBLE_ROWS; row++) {
            assertEquals(row, anchorRowAt(CONTENT_LEFT, anchorRowY(row)));
            assertEquals(row, anchorRowAt(LEFT_RIGHT - .01, anchorRowY(row) + LIST_ROW_HEIGHT - .01));
        }
        assertEquals(-1, anchorRowAt(50, LIST_TOP));
        assertEquals(-1, anchorRowAt(50, LIST_BOTTOM - 1));
        assertTrue(LIST_VISIBLE_ROWS > 8);
    }
}
