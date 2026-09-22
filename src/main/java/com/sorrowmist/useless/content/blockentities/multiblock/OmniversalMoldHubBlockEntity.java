package com.sorrowmist.useless.content.blockentities.multiblock;

import com.sorrowmist.useless.content.blockentities.PagedMenuPageMemory;
import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.MoldMatcher;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.core.component.ExternalInventoryKind;
import com.sorrowmist.useless.core.component.ExternalInventoryReference;
import com.sorrowmist.useless.init.ModBlockEntities;
import com.sorrowmist.useless.init.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.network.chat.Component;
import com.sorrowmist.useless.content.menus.OmniversalMoldHubMenu;
import com.sorrowmist.useless.core.component.MultiblockPartData;
import com.sorrowmist.useless.world.inventory.ExternalInventoryStore;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OmniversalMoldHubBlockEntity extends BlockEntity implements MenuProvider {
    private final RecoverableItemStackHandler molds = new RecoverableItemStackHandler(
            ConfigManager::getOmniversalMoldSlots,
            this::isValidMold,
            this::moldInventoryChanged);
    private final PagedMenuPageMemory pageMemory = new PagedMenuPageMemory(this::setChanged);
    private final Map<List<Ingredient>, Boolean> moldMatchCache = new HashMap<>();
    @Nullable
    private Map<Integer, ItemStack> cachedAvailableMolds;
    @Nullable
    private MoldMatcher.PreparedMolds cachedPreparedMolds;
    private int cachedActiveSlots = -1;
    private long cachedRecipeCatalogGeneration = -1L;
    @Nullable
    private BlockPos controllerPos;
    private long structureGeneration;
    private boolean unloading;
    @Nullable
    private ExternalInventoryReference inventoryReference;
    @Nullable
    private CompoundTag pendingLegacyInventory;
    private boolean externalInventoryLoaded;

    public OmniversalMoldHubBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OMNIVERSAL_MOLD_HUB.get(), pos, state);
    }

    public RecoverableItemStackHandler getMolds() {
        ensureExternalInventory();
        return molds;
    }

    public PagedMenuPageMemory getPageMemory() {
        return pageMemory;
    }

    public MultiblockPartData createItemData(HolderLookup.Provider registries) {
        return MultiblockPartData.inventory(molds, registries);
    }

    public void restoreItemData(MultiblockPartData data, HolderLookup.Provider registries) {
        if (data == null) return;
        data.restoreInventory(molds, registries);
        moldInventoryChanged();
    }

    public void bindExternalInventory(@Nullable ExternalInventoryReference requested,
                                      @Nullable CompoundTag legacyInventory,
                                      HolderLookup.Provider registries) {
        if (level == null || level.isClientSide) return;
        if (externalInventoryLoaded) {
            // 放置方块时 vanilla 会先调 clearRemoved()、后调 setPlacedBy()，前者可能在物品上的引用
            // 尚未写入 BE 组件字段时抢先绑定了临时占位。此处必须用物品携带的引用覆盖它，
            // 否则原数据不会被加载（拆除后重新放置将表现为内容物丢失）。
            if (requested == null || requested.equals(inventoryReference)) return;
            ExternalInventoryStore.release(level, worldPosition, inventoryReference);
        }
        inventoryReference = ExternalInventoryStore.bindAt(
                level, ExternalInventoryKind.MOLD_HUB, requested, worldPosition,
                molds, legacyInventory, registries);
        ExternalInventoryStore.setReference(this, inventoryReference);
        pendingLegacyInventory = null;
        externalInventoryLoaded = true;
        moldInventoryChanged();
    }

    private void ensureExternalInventory() {
        if (externalInventoryLoaded || level == null || level.isClientSide) return;
        bindExternalInventory(
                ExternalInventoryStore.getReference(this),
                pendingLegacyInventory,
                level.registryAccess());
    }

    @Nullable
    public ExternalInventoryReference getExternalInventoryReference() {
        ensureExternalInventory();
        return inventoryReference;
    }

    public void releaseExternalInventory() {
        if (level != null) {
            ExternalInventoryStore.save(level, inventoryReference, molds, level.registryAccess());
            ExternalInventoryStore.release(level, worldPosition, inventoryReference);
        }
    }

    private boolean isValidMold(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ModTags.MOLDS)
                || AlloyFurnaceRecipeCatalog.isKnownMold(level, stack));
    }

    public boolean containsMold(Ingredient ingredient) {
        return containsMolds(ingredient == null ? List.of() : List.of(ingredient));
    }

    /** Returns whether the active mold slots can satisfy every independent requirement. */
    public boolean containsMolds(List<Ingredient> requirements) {
        List<Ingredient> normalized = MoldMatcher.normalizeRequirements(requirements);
        if (normalized.isEmpty()) return true;
        refreshMoldMatchCache();
        Boolean cached = moldMatchCache.get(normalized);
        if (cached != null) return cached;

        boolean matched = getPreparedMolds().matches(normalized);
        moldMatchCache.put(normalized, matched);
        return matched;
    }

    private Map<Integer, ItemStack> getPreparedMoldMap() {
        if (cachedAvailableMolds == null) {
            int activeSlots = molds.getActiveSlots();
            Map<Integer, ItemStack> available = new LinkedHashMap<>();
            for (int slot = 0; slot < activeSlots; slot++) {
                ItemStack mold = molds.getStackInSlot(slot);
                if (!mold.isEmpty()) available.put(slot, mold.copy());
            }
            cachedAvailableMolds = Collections.unmodifiableMap(available);
        }
        return cachedAvailableMolds;
    }

    private MoldMatcher.PreparedMolds getPreparedMolds() {
        if (cachedPreparedMolds == null) {
            cachedPreparedMolds = MoldMatcher.prepare(getPreparedMoldMap());
        }
        return cachedPreparedMolds;
    }

    /**
     * Tests the one-to-one assignment independently of inventory storage. Each available stack is
     * one device slot, regardless of its item count; augmenting paths handle overlapping ingredients.
     */
    public static boolean matchesMolds(List<Ingredient> requirements, List<ItemStack> available) {
        return MoldMatcher.matches(requirements, available);
    }

    public static boolean matchesMolds(
            List<Ingredient> requirements, Map<Integer, ItemStack> available) {
        return MoldMatcher.matches(requirements, available);
    }

    private void refreshMoldMatchCache() {
        int activeSlots = molds.getActiveSlots();
        long catalogGeneration = AlloyFurnaceRecipeCatalog.generation();
        if (cachedActiveSlots != activeSlots || cachedRecipeCatalogGeneration != catalogGeneration) {
            moldMatchCache.clear();
            cachedAvailableMolds = null;
            cachedPreparedMolds = null;
            cachedActiveSlots = activeSlots;
            cachedRecipeCatalogGeneration = catalogGeneration;
        }
    }

    private void moldInventoryChanged() {
        moldMatchCache.clear();
        cachedAvailableMolds = null;
        cachedPreparedMolds = null;
        if (externalInventoryLoaded && level != null && !level.isClientSide) {
            ExternalInventoryStore.save(level, inventoryReference, molds, level.registryAccess());
        }
        setChanged();
    }

    public void linkController(@Nullable BlockPos controllerPos, long generation) {
        if (unloading || isRemoved()) return;
        if (!java.util.Objects.equals(this.controllerPos, controllerPos) || structureGeneration != generation) {
            this.controllerPos = controllerPos == null ? null : controllerPos.immutable();
            this.structureGeneration = generation;
            setChanged();
        }
    }

    @Nullable
    public MultiblockAlloyFurnaceCoreBlockEntity getController() {
        if (unloading || isRemoved() || level == null || controllerPos == null
                || !level.isLoaded(controllerPos)) return null;
        if (level.getBlockEntity(controllerPos) instanceof MultiblockAlloyFurnaceCoreBlockEntity core
                && core.isMoldHubLinked(worldPosition, structureGeneration)) {
            return core;
        }
        return null;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.useless_mod.omniversal_mold_hub");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new OmniversalMoldHubMenu(containerId, inventory, worldPosition);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pendingLegacyInventory = tag.contains("Molds")
                ? tag.getCompound("Molds").copy() : null;
        moldMatchCache.clear();
        cachedAvailableMolds = null;
        cachedPreparedMolds = null;
        cachedActiveSlots = -1;
        cachedRecipeCatalogGeneration = -1L;
        controllerPos = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        structureGeneration = tag.getLong("StructureGeneration");
        pageMemory.load(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) tag.putLong("Controller", controllerPos.asLong());
        tag.putLong("StructureGeneration", structureGeneration);
        pageMemory.save(tag);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        unloading = false;
        // 这里**不要**调 ensureExternalInventory()：放置方块时 vanilla 的调用顺序是
        // Level.setBlock → clearRemoved() → updateBlockEntityComponents → setPlacedBy()，
        // 本方法执行时 BE 组件还是空的（物品上的引用要等 updateBlockEntityComponents 才搬进来），
        // 提前 bindAt(null) 会抢一个空 UUID 并置 externalInventoryLoaded，让 setPlacedBy 的
        // adopt 早退 —— 结果就是拆掉重放后内容物丢失。绑定交给 setPlacedBy / onLoad 即可。
    }

    @Override
    public void onLoad() {
        super.onLoad();
        ensureExternalInventory();
    }

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        unloading = true;
        super.setRemoved();
    }
}
