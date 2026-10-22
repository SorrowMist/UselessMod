package com.sorrowmist.useless.content.blockentities.multiblock;

import com.sorrowmist.useless.content.menus.MufflerHatchMenu;
import com.sorrowmist.useless.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 消声仓方块实体。
 *
 * <p>消声仓固定位于结构最上层中心，排烟口朝上。其上方 {@link #EXHAUST_HEIGHT} 格必须为空气，
 * 否则核心判定排烟受阻并暂停工作；核心处于工作状态时由核心驱动本仓向上喷出烟雾粒子。
 * 受阻判定与「仅在工作时冒烟」参考 GregTech Modern 的 {@code IMufflerMachine#isFrontFaceFree}
 * 与 {@code MufflerPartMachine#clientTick}，但不引入其污染扩散与副产物回收体系。</p>
 */
public final class MufflerHatchBlockEntity extends BlockEntity implements MenuProvider {
    /** 排烟空间高度：消声仓上方须保持为空气的格数。 */
    public static final int EXHAUST_HEIGHT = 2;
    /** 输出槽位数量：消声仓的界面只有一个槽位。 */
    private static final int OUTPUT_SLOTS = 1;
    /** 粒子发射间隔（tick）。过密会造成无谓的网络与渲染负担。 */
    private static final int EMIT_INTERVAL_TICKS = 4;
    /** 每次发射的粒子数量。 */
    private static final int PARTICLES_PER_EMIT = 3;
    /** 烟雾上升初速度，与原版营火烟雾一致。 */
    private static final double SMOKE_RISE_SPEED = 0.07;
    /** 烟雾出生点在顶面上的水平随机范围。 */
    private static final double SMOKE_SPREAD = 0.35;

    /**
     * 消声仓的输出库存。
     *
     * <p>只有界面能访问该库存，槽位在菜单层被限定为只出不进；库存变更时标记方块实体已修改，
     * 使取走物品后的状态随存档保存。</p>
     */
    private final ItemStackHandler output = new ItemStackHandler(OUTPUT_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    @Nullable
    private BlockPos controllerPos;
    private long structureGeneration;
    private boolean unloading;

    public MufflerHatchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MUFFLER_HATCH.get(), pos, state);
    }

    public void linkController(@Nullable BlockPos controllerPos, long generation) {
        if (unloading || isRemoved()) return;
        if (!Objects.equals(this.controllerPos, controllerPos) || structureGeneration != generation) {
            this.controllerPos = controllerPos == null ? null : controllerPos.immutable();
            this.structureGeneration = generation;
            setChanged();
            // 归属变化必须下发到客户端，否则客户端结构预览无法识别本仓已被相邻核心占用。
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    /** True when this hatch's persisted link belongs to the given core. */
    public boolean isLinkedToController(BlockPos controllerPos) {
        return Objects.equals(this.controllerPos, controllerPos);
    }

    @Nullable
    private MultiblockAlloyFurnaceCoreBlockEntity getController() {
        if (unloading || isRemoved() || level == null || controllerPos == null
                || !level.isLoaded(controllerPos)) return null;
        return level.getBlockEntity(controllerPos) instanceof MultiblockAlloyFurnaceCoreBlockEntity core
                && core.isMufflerHatchLinked(worldPosition, structureGeneration) ? core : null;
    }

    /**
     * 消声仓不可共用：只要仍归属于另一个成型核心即视为被占用。
     * 陈旧或已失效的链接不算占用，便于旧结构破坏后由新结构重新认领。
     */
    public boolean isClaimedByOtherController(BlockPos controllerPos) {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getController();
        return controller != null && !controller.getBlockPos().equals(controllerPos);
    }

    /** 已归属于成型核心且排烟空间受阻。供 Jade 在消声仓上给出提示。 */
    public boolean isObstructed() {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getController();
        return controller != null && controller.isMufflerObstructed();
    }

    /**
     * 消声仓上方的排烟空间是否畅通：自 {@code mufflerPos} 起向上 {@link #EXHAUST_HEIGHT} 格须全部为空气。
     */
    public static boolean isExhaustClear(BlockGetter level, BlockPos mufflerPos) {
        for (int offset = 1; offset <= EXHAUST_HEIGHT; offset++) {
            if (!level.getBlockState(mufflerPos.above(offset)).isAir()) return false;
        }
        return true;
    }

    /** 供容器菜单访问的输出库存，槽位在菜单层被限定为只出不进。 */
    public IItemHandler getOutput() {
        return output;
    }

    /**
     * 把输出库存中的物品掉落到世界中，供方块被拆除时调用。
     *
     * <p>只在服务端执行；掉落物从方块中心生成，避免库存随方块消失。</p>
     */
    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (int slot = 0; slot < output.getSlots(); slot++) {
            ItemStack stack = output.extractItem(slot, output.getSlotLimit(slot), false);
            if (stack.isEmpty()) continue;
            level.addFreshEntity(new ItemEntity(level,
                    worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                    stack));
        }
    }

    /**
     * 由核心在工作状态下逐 tick 调用，按固定间隔从顶面向上喷出烟雾。
     * 核心只有在排烟空间畅通时才能工作，因此此处无需重复判定阻挡。
     */
    public void emitSmokeFromController() {
        if (unloading || isRemoved() || !(level instanceof ServerLevel serverLevel)) return;
        if (serverLevel.getGameTime() % EMIT_INTERVAL_TICKS != 0) return;
        RandomSource random = serverLevel.getRandom();
        for (int i = 0; i < PARTICLES_PER_EMIT; i++) {
            double x = worldPosition.getX() + 0.5 + (random.nextDouble() - 0.5) * SMOKE_SPREAD;
            double y = worldPosition.getY() + 1.05;
            double z = worldPosition.getZ() + 0.5 + (random.nextDouble() - 0.5) * SMOKE_SPREAD;
            // 粒子数为 0 时偏移参数按方向向量解释、乘以速度作为初速度，以此令烟雾竖直上升。
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    x, y, z, 0, 0.0, 1.0, 0.0, SMOKE_RISE_SPEED);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.useless_mod.muffler_hatch");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MufflerHatchMenu(containerId, inventory, worldPosition);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerPos = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
        structureGeneration = tag.getLong("StructureGeneration");
        if (tag.contains("Output")) output.deserializeNBT(registries, tag.getCompound("Output"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) tag.putLong("Controller", controllerPos.asLong());
        tag.putLong("StructureGeneration", structureGeneration);
        tag.put("Output", output.serializeNBT(registries));
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

    /** 客户端结构预览需要归属信息，故方块更新与区块首包都要带上完整 NBT。 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
