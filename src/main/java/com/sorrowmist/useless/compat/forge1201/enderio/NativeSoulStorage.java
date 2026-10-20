package com.sorrowmist.useless.compat.forge1201.enderio;

import com.enderio.api.capability.IEntityStorage;
import com.enderio.base.common.init.EIOCapabilities;
import com.enderio.base.common.init.EIOItems;
import net.minecraft.world.item.ItemStack;

/** Uses the native ENTITY_STORAGE Forge capability; output NBT stays visible to Ender IO. */
public final class NativeSoulStorage {
    private NativeSoulStorage() {}
    private static IEntityStorage storage(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : stack.getCapability(EIOCapabilities.ENTITY_STORAGE).orElse(null);
    }
    public static boolean canBindSoul(ItemStack stack) { return storage(stack) != null; }
    public static NativeSoul getBoundSoul(ItemStack stack) {
        return read(storage(stack));
    }
    public static boolean tryBindSoul(ItemStack stack, NativeSoul soul) {
        return bind(storage(stack), soul);
    }
    // Delegates kept separate so the native storage format can also be regression-tested
    // without the Forge capability-token class transformer in a unit-test JVM.
    static NativeSoul read(IEntityStorage storage) {
        return storage == null || !storage.hasStoredEntity()
                ? NativeSoul.empty() : NativeSoul.from(storage.getStoredEntityData());
    }
    static boolean bind(IEntityStorage storage, NativeSoul soul) {
        if (storage == null || soul == null || soul.isEmpty()) return false;
        storage.setStoredEntityData(soul.storedData());
        return true;
    }
    public static ItemStack forSoul(NativeSoul soul) {
        ItemStack result = EIOItems.FILLED_SOUL_VIAL.asStack();
        return tryBindSoul(result, soul) ? result : ItemStack.EMPTY;
    }
}
