package com.sorrowmist.useless.content.blockentities;

import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class HighStackInventoryPortTest {
    @BeforeAll static void bootstrap() {RecipeTestBootstrap.initialize();}
    private static HighStackItemStackHandler inventory(int slots,AtomicInteger changes) {
        return new HighStackItemStackHandler(slots,0,1,2,slots-2,slots,slots-1,changes::incrementAndGet,null,null,null);
    }
    private static ItemStack stack(int count) {
        var stack=new ItemStack(Items.DIAMOND,count);
        stack.getOrCreateTag().putString("OtherModState","retained");
        var inner=new CompoundTag(); inner.putLong("counter",Long.MAX_VALUE);
        stack.getOrCreateTag().put("ComplexState",inner); return stack;
    }
    @Test void nativeForgeSaveLoadPreservesBillionItemCountsNbtAndSlotsAbove255() {
        var changes=new AtomicInteger(); var inventory=inventory(512,changes);
        inventory.setStackInSlot(399,stack(2_000_000_000));
        CompoundTag saved=inventory.serializeNBT();
        assertEquals(399,saved.getList("Items",10).getCompound(0).getInt("Slot"));
        var restored=inventory(512,new AtomicInteger()); restored.deserializeNBT(saved);
        assertEquals(2_000_000_000,restored.getStackInSlot(399).getCount());
        assertEquals(inventory.getStackInSlot(399).getTag(),restored.getStackInSlot(399).getTag());
        assertTrue(restored.getStackInSlot(143).isEmpty());
        assertEquals(512,restored.getSlots());
    }
    @Test void machineConsumesActualHighCountBatchInsteadOfVanilla64ItemClamp() {
        var changes=new AtomicInteger(); var inventory=inventory(4,changes);
        assertTrue(inventory.insertItem(2,stack(500_000),false).isEmpty());
        changes.set(0);
        assertEquals(250_000,inventory.extractItem(2,250_000,true).getCount());
        assertEquals(500_000,inventory.getStackInSlot(2).getCount()); assertEquals(0,changes.get());
        var consumed=inventory.extractItem(2,250_000,false);
        assertEquals(250_000,consumed.getCount()); assertEquals(stack(1).getTag(),consumed.getTag());
        assertEquals(250_000,inventory.getStackInSlot(2).getCount()); assertEquals(1,changes.get());
        assertEquals(250_000,inventory.extractItem(2,Integer.MAX_VALUE,false).getCount());
        assertTrue(inventory.getStackInSlot(2).isEmpty()); assertEquals(2,changes.get());
    }
    @Test void insertionAtIntLimitReturnsExactRemainderWithoutOverflowOrMutationDuringSimulation() {
        var inventory=inventory(4,new AtomicInteger());
        inventory.setStackInSlot(2,stack(Integer.MAX_VALUE-10));
        assertEquals(90,inventory.insertItem(2,stack(100),true).getCount());
        assertEquals(Integer.MAX_VALUE-10,inventory.getStackInSlot(2).getCount());
        assertEquals(90,inventory.insertItem(2,stack(100),false).getCount());
        assertEquals(Integer.MAX_VALUE,inventory.getStackInSlot(2).getCount());
    }
    @Test void nativeLegacySaveAndUnsignedByteSlotAreRestoredWithoutLosingForeignNbt() {
        var inventory=inventory(256,new AtomicInteger());
        var entry=stack(1).save(new CompoundTag()); entry.putByte("Slot",(byte)200); entry.putInt("RealCount",5000);
        var items=new ListTag(); items.add(entry); var tag=new CompoundTag(); tag.put("Items",items);
        inventory.deserializeNBT(tag);
        assertEquals(5000,inventory.getStackInSlot(200).getCount());
        assertEquals(stack(1).getTag(),inventory.getStackInSlot(200).getTag());
        inventory.deserializeNBT(new CompoundTag());
        assertTrue(inventory.getStackInSlot(200).isEmpty());
    }
    @Test void savedNegativeCountsAndOutOfBoundsSlotsCannotCreateGhostItems() {
        var inventory=inventory(4,new AtomicInteger());
        var items=new ListTag(); var negative=stack(1).save(new CompoundTag());
        negative.putInt("Slot",2); negative.putInt("RealCount",-1); items.add(negative);
        var outside=stack(1).save(new CompoundTag()); outside.putInt("Slot",3000); outside.putInt("RealCount",10); items.add(outside);
        var tag=new CompoundTag(); tag.put("Items",items); inventory.deserializeNBT(tag);
        for(int slot=0;slot<inventory.getSlots();slot++) assertTrue(inventory.getStackInSlot(slot).isEmpty());
    }
}
