package com.sorrowmist.useless.compat.forge1201.nbt;

import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeStackNbtTest {
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }
    @Test void largeStacksKeepFullCountsAndNestedForeignTagsWithoutSharing() {
        var stack = new ItemStack(Items.IRON_INGOT, 2_000_000_000);
        var foreign = new CompoundTag(); foreign.putIntArray("vector", new int[]{1, 2, 3});
        stack.getOrCreateTag().put("othermod", foreign);
        var saved = NativeStackNbt.saveItem(stack, RegistryAccess.EMPTY);
        var restored = NativeStackNbt.readItem(RegistryAccess.EMPTY, saved);
        assertEquals(2_000_000_000, restored.getCount());
        assertEquals(stack.getTag(), restored.getTag());
        restored.getTag().getCompound("othermod").putInt("changed", 1);
        assertFalse(saved.getCompound("tag").getCompound("othermod").contains("changed"));
        assertFalse(stack.getTag().getCompound("othermod").contains("changed"));
        var normal = NativeStackNbt.saveItem(new ItemStack(Items.IRON_INGOT, 64), RegistryAccess.EMPTY);
        assertFalse(normal.contains("RealCount"));
        assertEquals(64, ItemStack.of(normal).getCount());
    }
    @Test void fluidIdentityAmountAndCompoundTagsUseNativeForgeFormat() {
        var fluid = new FluidStack(Fluids.WATER, 2_000_000_000);
        var tag = new CompoundTag(); tag.putString("grade", "ultrapure"); fluid.setTag(tag);
        var saved = NativeStackNbt.saveFluid(fluid, RegistryAccess.EMPTY);
        assertEquals("minecraft:water", saved.getString("FluidName"));
        var restored = NativeStackNbt.readFluid(RegistryAccess.EMPTY, saved);
        assertEquals(2_000_000_000, restored.getAmount());
        assertTrue(fluid.isFluidEqual(restored));
        restored.getTag().putString("grade", "changed");
        assertEquals("ultrapure", saved.getCompound("Tag").getString("grade"));
    }
    @Test void nonemptyNewerComponentPayloadCannotBeSilentlyDiscarded() {
        var saved = NativeStackNbt.saveItem(new ItemStack(Items.DIAMOND), RegistryAccess.EMPTY);
        var unsupported = new CompoundTag(); unsupported.putInt("foreign:data", 2);
        saved.put("components", unsupported);
        assertThrows(IllegalArgumentException.class, () -> NativeStackNbt.readItem(RegistryAccess.EMPTY, saved));
        assertThrows(IllegalArgumentException.class, () -> NativeStackNbt.readFluid(RegistryAccess.EMPTY, saved));
    }
}
