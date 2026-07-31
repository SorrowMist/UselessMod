package com.sorrowmist.useless.content.blockentities.multiblock;

import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.core.config.ConfigManager;
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
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

public final class OmniversalMoldHubBlockEntity extends BlockEntity implements MenuProvider {
    private final RecoverableItemStackHandler molds = new RecoverableItemStackHandler(
            ConfigManager::getOmniversalMoldSlots,
            this::isValidMold,
            this::moldInventoryChanged);
    private final Map<Ingredient, Boolean> moldMatchCache = new IdentityHashMap<>();
    private int cachedActiveSlots = -1;
    private long cachedRecipeCatalogGeneration = -1L;
    @Nullable
    private BlockPos controllerPos;
    private long structureGeneration;
    private boolean unloading;

    public OmniversalMoldHubBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OMNIVERSAL_MOLD_HUB.get(), pos, state);
    }

    public RecoverableItemStackHandler getMolds() {
        return molds;
    }

    public MultiblockPartData createItemData(HolderLookup.Provider registries) {
        return MultiblockPartData.inventory(molds, registries);
    }

    public void restoreItemData(MultiblockPartData data, HolderLookup.Provider registries) {
        if (data == null) return;
        data.restoreInventory(molds, registries);
        moldInventoryChanged();
    }

    private boolean isValidMold(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ModTags.MOLDS)
                || AlloyFurnaceRecipeCatalog.isKnownMold(level, stack));
    }

    public boolean containsMold(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return true;
        refreshMoldMatchCache();
        Boolean cached = moldMatchCache.get(ingredient);
        if (cached != null) return cached;
        int activeSlots = molds.getActiveSlots();
        for (int slot = 0; slot < activeSlots; slot++) {
            ItemStack stack = molds.getStackInSlot(slot);
            if (AdapterUtils.matchesMold(ingredient, stack)) {
                moldMatchCache.put(ingredient, true);
                return true;
            }
        }
        moldMatchCache.put(ingredient, false);
        return false;
    }

    private void refreshMoldMatchCache() {
        int activeSlots = molds.getActiveSlots();
        long catalogGeneration = AlloyFurnaceRecipeCatalog.generation();
        if (cachedActiveSlots != activeSlots || cachedRecipeCatalogGeneration != catalogGeneration) {
            moldMatchCache.clear();
            cachedActiveSlots = activeSlots;
            cachedRecipeCatalogGeneration = catalogGeneration;
        }
    }

    private void moldInventoryChanged() {
        moldMatchCache.clear();
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
        molds.deserializeNBT(registries, tag.getCompound("Molds"));
        moldMatchCache.clear();
        cachedActiveSlots = -1;
        cachedRecipeCatalogGeneration = -1L;
        controllerPos = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        structureGeneration = tag.getLong("StructureGeneration");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Molds", molds.serializeNBT(registries));
        if (controllerPos != null) tag.putLong("Controller", controllerPos.asLong());
        tag.putLong("StructureGeneration", structureGeneration);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        unloading = false;
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
