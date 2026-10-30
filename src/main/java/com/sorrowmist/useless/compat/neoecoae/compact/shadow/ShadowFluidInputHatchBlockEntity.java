package com.sorrowmist.useless.compat.neoecoae.compact.shadow;

import appeng.api.orientation.BlockOrientation;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.Set;

/**
 * 不落地的紧凑 F9 冷却液输入仓。
 *
 * <p>它只提供 ECO 合成集群识别的 {@code tank}，流体自动搬运和世界同步都由紧凑 F9
 * 自己接管，因此不能把它当作一个会在世界中扫描邻居的普通输入仓。</p>
 */
public class ShadowFluidInputHatchBlockEntity extends ECOFluidInputHatchBlockEntity {

    // ECO can change the field's declared tank subtype. Resolve it by name and keep
    // its native instance so long fluid counters and existing NBT remain intact.
    private static final Field TANK_FIELD = findTankField();
    private Runnable contentsChanged;

    private static Field findTankField() {
        try {
            return ECOFluidInputHatchBlockEntity.class.getField("tank");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("ECO fluid input hatch has no public tank field", e);
        }
    }

    public FluidTank sharedTank() {
        try {
            return (FluidTank) TANK_FIELD.get(this);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot read ECO fluid input tank", e);
        }
    }

    public void initializeSharedTank(int capacity, Runnable contentsChanged) {
        sharedTank().setCapacity(capacity);
        this.contentsChanged = contentsChanged;
    }

    public void shareTankWith(ShadowFluidInputHatchBlockEntity source) {
        try {
            TANK_FIELD.set(this, source.sharedTank());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot share ECO fluid input tank", e);
        }
    }

    @Override
    public void setChanged() {
        // The native tank keeps its callback bound to the source hatch shared by the fleet.
        if (contentsChanged != null) {
            contentsChanged.run();
        }
    }

    public ShadowFluidInputHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        getMainNode().setFlags();
        getMainNode().setInWorldNode(false);
    }

    @Override
    public void tick(Level level, BlockPos pos, BlockState state) {
        // The compact host exposes the tank through its own block capability.
    }

    @Override
    public void updateState(boolean updateExposed) {
    }

    @Override
    public void markForUpdate() {
    }

    @Override
    public void saveChanges() {
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.noneOf(Direction.class);
    }
}
