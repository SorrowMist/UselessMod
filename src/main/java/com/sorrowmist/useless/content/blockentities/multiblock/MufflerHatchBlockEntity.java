package com.sorrowmist.useless.content.blockentities.multiblock;

import com.sorrowmist.useless.content.menus.MufflerHatchMenu;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.init.ModBlockEntities;
import com.sorrowmist.useless.init.ModItems;
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
import net.minecraft.world.MenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
     * 产出 1 份灰烬所需的运行 tick 数，仅在核心处于工作状态时累加。
     *
     * <p>节奏参照 GregTech Modern 的消声仓（{@code MufflerPartMachine}）：其
     * {@code recoveryChance = max(1, tier * 10)}，每次配方完成时按该百分比判定一次，
     * 即 LV 约 10%、UV 约 80%；按典型配方 10 秒折算，约 100 秒（LV）至 12.5 秒（UV）一份。
     * 本模组的并行与线程数远高于 GT，同一时间完成的配方量更大，不能直接沿用其「每次配方判定」的速率，
     * 因此改为按运行时间累积：基准周期 3 分钟（3600 tick）一份，再由线圈抑制按比例拉长实际周期。</p>
     */
    private static final int ASH_INTERVAL_TICKS = 3600;
    /** 毒性效果的判定间隔（tick），即每秒一次。 */
    private static final int TOXIC_INTERVAL_TICKS = 20;

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

    /** 只读视图：允许抽取、拒绝插入，供 {@link #getAshOutput()} 使用。 */
    private final IItemHandler ashExtractOnly = new IItemHandler() {
        @Override
        public int getSlots() {
            return output.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return output.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return output.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return output.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    };

    @Nullable
    private BlockPos controllerPos;
    private long structureGeneration;
    /** 灰烬产出进度，仅在核心处于工作状态时累加。 */
    private int ashProgress;
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
     * 对外暴露的灰烬库存，仅供自动化抽取。
     *
     * <p>漏斗与管道必须能取走灰烬，否则「灰烬堆满阻塞工作」会让全自动产线永久停机；
     * 同时不允许任何外部插入，避免该槽位被当作普通容器使用。因此返回只读视图而非
     * {@link #output} 本身，插入请求原样退回。</p>
     */
    public IItemHandler getAshOutput() {
        return ashExtractOnly;
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
     * 由核心在工作状态下逐 tick 调用：按固定间隔从顶面向上喷出烟雾，并累积灰烬产出进度。
     *
     * <p>核心只有在排烟空间畅通时才能工作，因此此处无需重复判定阻挡。灰烬进度只在工作状态下累加，
     * 停机期间保持不变；输出槽已满时暂停累加，待玩家取走产物后继续。</p>
     */
    public void tickFromController(boolean furnaceWorking) {
        if (unloading || isRemoved() || !(level instanceof ServerLevel serverLevel)) return;
        // 停机期间若仍有残留灰烬，排烟与毒性效果继续存在；产灰只在核心真正工作时推进。
        boolean ashPresent = !output.getStackInSlot(0).isEmpty();
        if (furnaceWorking || ashPresent) {
            emitSmoke(serverLevel);
        }
        if (furnaceWorking) {
            accumulateAsh(serverLevel);
        }
        tickToxicEffects(serverLevel);
    }

    /** 按固定间隔喷出烟雾粒子。 */
    private void emitSmoke(ServerLevel serverLevel) {
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

    /**
     * 累积灰烬产出进度，满 {@link #ASH_INTERVAL_TICKS} 后向输出槽放入一份灰烬。
     *
     * <p>输出槽已满时不累加，进度停留在满值之前，避免产物凭空丢失。</p>
     */
    private void accumulateAsh(ServerLevel serverLevel) {
        if (isAshFull()) return;
        if (isAshProductionSuppressed(serverLevel)) return;
        if (ashProgress + 1 < ASH_INTERVAL_TICKS) {
            ashProgress++;
            return;
        }
        ItemStack remainder = output.insertItem(0, new ItemStack(ModItems.ASH.get()), false);
        if (remainder.isEmpty()) {
            ashProgress = 0;
            setChanged();
        }
    }

    /**
     * 线圈净化：每 tick 按当前线圈的抑制百分比决定本 tick 是否推进灰烬进度。
     *
     * <p>抑制 100% 时完全停产灰烬，抑制 50% 时平均产出周期翻倍。未安装线圈或结构未成型时
     * 取不到核心，按不抑制处理。</p>
     */
    private boolean isAshProductionSuppressed(ServerLevel serverLevel) {
        MultiblockAlloyFurnaceCoreBlockEntity controller = getController();
        if (controller == null) return false;
        int suppression = ConfigManager.getOmniversalCoilAshSuppression(controller.getCoilTier());
        if (suppression <= 0) return false;
        if (suppression >= 100) return true;
        return serverLevel.getRandom().nextInt(100) < suppression;
    }

    /** 输出槽中灰烬的填充比例，千分比。用于确定毒性效果的触发概率。 */
    private int getAshFillPermille() {
        int limit = output.getSlotLimit(0);
        if (limit <= 0) return 0;
        return (int) ((long) output.getStackInSlot(0).getCount() * 1000L / limit);
    }

    /**
     * 灰烬毒性：每 {@link #TOXIC_INTERVAL_TICKS} tick 判定一次，触发概率随输出槽的灰烬填充比例
     * 线性升高至配置的最大值；命中时对作用半径内的所有生物施加中毒与凋零。
     */
    private void tickToxicEffects(ServerLevel serverLevel) {
        if (!ConfigManager.isAshToxicEnabled()) return;
        if (serverLevel.getGameTime() % TOXIC_INTERVAL_TICKS != 0) return;
        int maxChance = ConfigManager.getAshToxicMaxChancePermille();
        if (maxChance <= 0) return;
        int fillPermille = getAshFillPermille();
        if (fillPermille <= 0) return;
        int chance = (int) ((long) maxChance * fillPermille / 1000L);
        if (chance <= 0 || serverLevel.getRandom().nextInt(1000) >= chance) return;
        int duration = ConfigManager.getAshToxicDurationSeconds() * 20;
        double radius = ConfigManager.getAshToxicRadius();
        double centerX = worldPosition.getX() + 0.5;
        double centerY = worldPosition.getY() + 0.5;
        double centerZ = worldPosition.getZ() + 0.5;
        AABB area = new AABB(
                centerX - radius, centerY - radius, centerZ - radius,
                centerX + radius, centerY + radius, centerZ + radius);
        for (LivingEntity entity : serverLevel.getEntitiesOfClass(LivingEntity.class, area)) {
            entity.addEffect(new MobEffectInstance(MobEffects.POISON, duration, 0));
            entity.addEffect(new MobEffectInstance(MobEffects.WITHER, duration, 0));
        }
    }

    /** 输出槽是否已被灰烬堆满。供核心在配置开启时阻塞工作。 */
    public boolean isAshFull() {
        int limit = output.getSlotLimit(0);
        return limit > 0 && output.getStackInSlot(0).getCount() >= limit;
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
        ashProgress = tag.getInt("AshProgress");
        if (tag.contains("Output")) output.deserializeNBT(registries, tag.getCompound("Output"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) tag.putLong("Controller", controllerPos.asLong());
        tag.putLong("StructureGeneration", structureGeneration);
        tag.putInt("AshProgress", ashProgress);
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
