package com.sorrowmist.useless.compat.neoecoae.compact.ui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CompactHostPanelStateTest {
    @Test
    void craftingArtworkShowsL9CoresWithoutLosingRuntimeOverlays() throws ReflectiveOperationException {
        Class<?> type = panelType("crafting.CraftingControllerPanelState");
        CompoundTag original = new CompoundTag();
        original.putBoolean("formed", true);
        original.putInt("host", 3);
        original.putInt("cells", (1 << 11) - 1);
        original.putBoolean("coolant", true);
        original.putBoolean("switch", true);
        original.putInt("queue", 573);
        original.putInt("energy", 821);

        Object result = CompactHostPanelState.levelNine(read(type, original), false);
        CompoundTag updated = write(result);

        assertEquals(original.getInt("cells"), updated.getInt("cells"));
        for (int cell = 0; cell < 11; cell++) {
            assertEquals(3, tier(result, "upperTier", cell));
            assertEquals(3, tier(result, "lowerTier", cell));
        }
        assertTrue(updated.getBoolean("coolant"));
        assertTrue(updated.getBoolean("switch"));
        assertEquals(573, updated.getInt("queue"));
        assertEquals(821, updated.getInt("energy"));
    }

    @Test
    void computationArtworkRestoresExplicitMembersAndPreservesMirroredCells() throws ReflectiveOperationException {
        Class<?> type = panelType("computation.ComputationControllerPanelState");
        CompoundTag original = new CompoundTag();
        original.putBoolean("formed", true);
        original.putInt("cells", (1 << 9) | (1 << 10));
        // The spatial lookup returns absent upper/lower members for co-located compact hardware.
        original.put("upperMembers", new ListTag());
        original.put("coreMembers", new ListTag());
        original.put("lowerMembers", new ListTag());

        Object result = CompactHostPanelState.levelNine(read(type, original), true);

        assertEquals(original.getInt("cells"), write(result).getInt("cells"));
        assertEquals(3, write(result).getInt("host"));
        for (int cell = 0; cell < 11; cell++) {
            boolean present = cell >= 9;
            for (String row : new String[]{"upper", "core", "lower"}) {
                assertEquals(present ? 3 : 0, tier(result, row + "Tier", cell));
                try {
                    Object member = type.getMethod(row + "Member", int.class).invoke(result, cell);
                    assertEquals(present, member.getClass().getMethod("present").invoke(member));
                    assertNull(member.getClass().getMethod("texture").invoke(member));
                } catch (NoSuchMethodException legacyPanel) {
                    // ECO versions before explicit members draw directly from the packed tiers.
                }
            }
        }
    }

    @Test
    void unformedPanelStaysUnformed() throws ReflectiveOperationException {
        Class<?> type = panelType("crafting.CraftingControllerPanelState");
        Object unformed = type.getField("UNFORMED").get(null);
        assertSame(unformed, CompactHostPanelState.levelNine(unformed, false));
    }

    private static Class<?> panelType(String suffix) throws ClassNotFoundException {
        try {
            return Class.forName("cn.dancingsnow.neoecoae.gui." + suffix);
        } catch (ClassNotFoundException olderEco) {
            assumeTrue(false, "This ECO release predates controller artwork");
            throw olderEco;
        }
    }

    private static Object read(Class<?> type, CompoundTag tag) throws ReflectiveOperationException {
        return type.getMethod("fromTag", CompoundTag.class).invoke(null, tag);
    }

    private static CompoundTag write(Object panel) throws ReflectiveOperationException {
        return (CompoundTag) panel.getClass().getMethod("toTag").invoke(panel);
    }

    private static int tier(Object panel, String getter, int cell) throws ReflectiveOperationException {
        return (int) panel.getClass().getMethod(getter, int.class).invoke(panel, cell);
    }
}
