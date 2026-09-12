package com.sorrowmist.useless.content.menus;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.OmniversalMoldHubBlockEntity;
import com.sorrowmist.useless.content.items.OmniversalPatternConverterItem;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternEncoding;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternRecipeSelector;
import com.sorrowmist.useless.content.recipe.MoldMatcher;
import com.sorrowmist.useless.core.component.PatternConverterData;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.MOLD_SLOT;

/** Server-side state and conversion logic for the converter item menu. */
public final class HostPatternConverter extends ItemMenuHost<OmniversalPatternConverterItem>
        implements InternalInventoryHost {
    public static final int PATTERN_SLOTS = 27;

    private final AppEngInternalInventory patternInventory = new AppEngInternalInventory(this, PATTERN_SLOTS);

    public HostPatternConverter(
            OmniversalPatternConverterItem item, Player player, ItemMenuHostLocator locator) {
        super(item, player, locator);
        PatternConverterData data = getItemStack().get(UComponents.PATTERN_CONVERTER_DATA.get());
        if (data != null) {
            patternInventory.readFromNBT(data.data(), "Patterns", player.registryAccess());
        }
    }

    public AppEngInternalInventory getPatternInventory() {
        return patternInventory;
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
        saveInventory();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inventory, int slot) {
        saveInventory();
    }

    private void saveInventory() {
        CompoundTag data = new CompoundTag();
        patternInventory.writeToNBT(data, "Patterns", getPlayer().registryAccess());
        if (data.isEmpty()) {
            getItemStack().remove(UComponents.PATTERN_CONVERTER_DATA.get());
        } else {
            getItemStack().set(UComponents.PATTERN_CONVERTER_DATA.get(), new PatternConverterData(data));
        }
    }

    public ConversionResult convertPatterns() {
        if (!(getPlayer() instanceof ServerPlayer player)) {
            return new ConversionResult(0, 0, false, Failure.INVALID_CONTEXT);
        }

        ConversionTarget target = findTarget(player);
        if (target == null) {
            return new ConversionResult(0, 0, false, getItemStack()
                    .get(UComponents.PATTERN_CONVERTER_LINK_TARGET.get()) == null
                    ? Failure.UNBOUND : Failure.TARGET_UNAVAILABLE);
        }

        MoldMatcher.PreparedMolds preparedMolds = MoldMatcher.prepare(target.molds());
        int converted = 0;
        int skipped = 0;
        boolean hasPatterns = false;
        for (int slot = 0; slot < patternInventory.size(); slot++) {
            ItemStack source = patternInventory.getStackInSlot(slot);
            if (source.isEmpty()) continue;
            hasPatterns = true;

            try {
                IPatternDetails details = PatternDetailsHelper.decodePattern(source, target.level());
                if (!(details instanceof AEProcessingPattern)) {
                    skipped++;
                    continue;
                }

                var entry = OmniversalPatternRecipeSelector.select(
                        target.level(), details, preparedMolds);
                if (entry.isEmpty()) {
                    skipped++;
                    continue;
                }

                ItemStack convertedPattern = OmniversalPatternEncoding.encode(
                        source, details, entry.get(), target.level());
                if (convertedPattern.isEmpty()) {
                    skipped++;
                    continue;
                }

                patternInventory.setItemDirect(slot, convertedPattern);
                converted++;
            } catch (RuntimeException ignored) {
                skipped++;
            }
        }

        if (!hasPatterns) {
            return new ConversionResult(0, 0, false, Failure.NO_PATTERNS);
        }
        return new ConversionResult(converted, skipped, true, Failure.NONE);
    }

    private static Map<Integer, ItemStack> getAvailableMolds(OmniversalMoldHubBlockEntity hub) {
        var molds = hub.getMolds();
        int activeSlots = molds.getActiveSlots();
        Map<Integer, ItemStack> result = new LinkedHashMap<>();
        for (int slot = 0; slot < activeSlots; slot++) {
            ItemStack mold = molds.getStackInSlot(slot);
            if (!mold.isEmpty()) result.put(slot, mold.copy());
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<Integer, ItemStack> getAvailableMolds(AdvancedAlloyFurnaceBlockEntity furnace) {
        ItemStack mold = furnace.getItemHandler().getStackInSlot(MOLD_SLOT);
        return mold.isEmpty() ? Map.of() : Map.of(0, mold.copy());
    }

    private ConversionTarget findTarget(ServerPlayer player) {
        GlobalPos link = getItemStack().get(UComponents.PATTERN_CONVERTER_LINK_TARGET.get());
        if (link == null) return null;
        ServerLevel level = player.getServer() == null ? null : player.getServer().getLevel(link.dimension());
        if (level == null || !level.isLoaded(link.pos())) return null;
        BlockEntity blockEntity = level.getBlockEntity(link.pos());
        if (blockEntity instanceof OmniversalMoldHubBlockEntity hub) {
            return new ConversionTarget(level, getAvailableMolds(hub));
        }
        if (blockEntity instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
            return new ConversionTarget(level, getAvailableMolds(furnace));
        }
        if (blockEntity instanceof MultiblockAlloyFurnaceCoreBlockEntity core) {
            OmniversalMoldHubBlockEntity hub = core.getLinkedMoldHub();
            if (hub != null) return new ConversionTarget(level, getAvailableMolds(hub));
        }
        return null;
    }

    public record ConversionResult(int converted, int skipped, boolean hasPatterns, Failure failure) {
    }

    public enum Failure {
        NONE,
        UNBOUND,
        TARGET_UNAVAILABLE,
        NO_PATTERNS,
        INVALID_CONTEXT
    }

    private record ConversionTarget(ServerLevel level, Map<Integer, ItemStack> molds) {
    }
}
