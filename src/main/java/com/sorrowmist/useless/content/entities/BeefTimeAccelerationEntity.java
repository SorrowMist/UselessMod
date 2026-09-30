package com.sorrowmist.useless.content.entities;

import com.sorrowmist.useless.content.items.BeefTimeAcceleration;
import com.sorrowmist.useless.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * 「时间加速」的隐形载体实体：既是计时器，也是效果施加者与悬浮文字锚点。
 *
 * <p>支持两类目标：
 * <ul>
 *   <li><b>方块目标</b>（{@code targetPos}）：每 tick 额外执行 {@code 2^tickSpeed} 次
 *       方块 tick / 随机刻。</li>
 *   <li><b>生物目标</b>（{@code TARGET_ENTITY_ID}）：每 tick 额外推进目标的「计时器」
 *       数值（生长 / 繁殖冷却 / 下蛋 / 补货 / 长毛…），<b>不跑 AI、寻路、移动与物理</b>。
 *       真正的推进逻辑在 {@link BeefTimeAcceleration#accelerateEntityTimers}。</li>
 * </ul>
 *
 * <p>生物目标时本实体会跟随目标（保证同区块被加载、悬浮文字位置正确）。
 */
public class BeefTimeAccelerationEntity extends Entity {
    public static final int DEFAULT_TOTAL_TIME = 600;
    public static final int MAX_TICK_SPEED = 8;

    /**
     * 生物目标的叠加上限：{@code 1 << 15 = 32768}。
     *
     * <p>生物加速是<b>纯整数加减</b>（不循环），所以倍率<b>不产生任何额外 CPU 开销</b>
     * —— 这与方块版每 tick 真跑 {@code 2^tickSpeed} 次 tick 完全不同，那边必须压在小值。
     * 这里放开到 15 的理由是「原版计时器的实际天花板」：{@code AgeableMob.age} 最大 24000、
     * {@code Chicken.eggTime} 最大 12000，{@code extra >= 24000} 之后所有计时器都已在 1 tick 内
     * 走完，再大只是数字好看。上限不能超过 30，否则 {@code 1 << 31} 会变成负数。</p>
     */
    public static final int MAX_ENTITY_TICK_SPEED = 15;

    /** 村民补货的节流：每 20 tick（1 游戏秒）最多补一次，避免刷屏交易界面。 */
    public static final int ENTITY_RESTOCK_COOLDOWN_TICKS = 20;

    /** 生物目标用：-1 表示这是一个方块目标。 */
    private static final int NO_ENTITY_TARGET = -1;

    private static final EntityDataAccessor<Integer> TICK_SPEED =
            SynchedEntityData.defineId(BeefTimeAccelerationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> REMAINING_TIME =
            SynchedEntityData.defineId(BeefTimeAccelerationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TOTAL_TIME =
            SynchedEntityData.defineId(BeefTimeAccelerationEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET_ENTITY_ID =
            SynchedEntityData.defineId(BeefTimeAccelerationEntity.class, EntityDataSerializers.INT);

    private BlockPos targetPos;
    /** 目标实体的 UUID：getId 在目标死亡后可能被新实体复用，UUID 作为兜底校验。 */
    private UUID targetEntityUuid;
    /** 村民补货节流计数，仅服务端使用，不进 NBT。 */
    private int restockCooldown;

    public BeefTimeAccelerationEntity(EntityType<? extends BeefTimeAccelerationEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvisible(true);
    }

    public BeefTimeAccelerationEntity(Level level, BlockPos targetPos) {
        this(ModEntities.BEEF_TIME_ACCELERATION.get(), level);
        this.targetPos = targetPos.immutable();
        this.setPos(targetPos.getX() + 0.5D, targetPos.getY() + 0.5D, targetPos.getZ() + 0.5D);
        this.setTickSpeed(1);
        this.setTotalTime(DEFAULT_TOTAL_TIME);
        this.setRemainingTime(DEFAULT_TOTAL_TIME);
    }

    public BeefTimeAccelerationEntity(Level level, LivingEntity target) {
        this(ModEntities.BEEF_TIME_ACCELERATION.get(), level);
        this.targetPos = null;
        this.setTargetEntityId(target.getId());
        this.targetEntityUuid = target.getUUID();
        this.snapToTarget(target);
        this.setTickSpeed(1);
        this.setTotalTime(DEFAULT_TOTAL_TIME);
        this.setRemainingTime(DEFAULT_TOTAL_TIME);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }

        if (!(this.level() instanceof ServerLevel level)) {
            this.discard();
            return;
        }
        if (this.getRemainingTime() <= 0) {
            this.discard();
            return;
        }

        if (this.isEntityTarget()) {
            tickEntityTarget(level);
        } else {
            tickBlockTarget(level);
        }

        this.setRemainingTime(this.getRemainingTime() - 1);
        if (this.getRemainingTime() <= 0) {
            this.discard();
        }
    }

    /** 方块目标：额外跑方块 tick / 随机刻。 */
    private void tickBlockTarget(ServerLevel level) {
        if (this.targetPos == null) {
            this.discard();
            return;
        }
        if (!level.isLoaded(this.targetPos)) {
            return;
        }

        BlockState state = level.getBlockState(this.targetPos);
        BlockEntity blockEntity = level.getBlockEntity(this.targetPos);
        if (!BeefTimeAcceleration.isValidTarget(level, state, blockEntity)) {
            this.discard();
            return;
        }

        BeefTimeAcceleration.tickTarget(level, this.targetPos, state, blockEntity, this.getTickSpeed());
    }

    /** 生物目标：跟随目标并推进其计时器；目标失效（死亡 / 换维度 / 区块卸载）即自毁。 */
    private void tickEntityTarget(ServerLevel level) {
        Entity found = level.getEntity(this.getTargetEntityId());
        if (!(found instanceof LivingEntity target)
                || target.isRemoved()
                || !target.isAlive()
                || target instanceof Player
                || (this.targetEntityUuid != null && !this.targetEntityUuid.equals(target.getUUID()))) {
            this.discard();
            return;
        }

        // 跟随目标：既保证本实体与目标同区块被加载，也让悬浮文字贴着目标渲染
        this.snapToTarget(target);

        BeefTimeAcceleration.accelerateEntityTimers(target, this.getTickSpeed());

        // 村民补货特判：原版门槛绑在 gameTime 上，单纯推计时器无效，必须直接调 restock()。
        // 只在主 tick 调一次（不在计时器循环里），并用 needsRestock() 做闸门 + 节流，避免空刷。
        if (target instanceof Villager villager) {
            if (this.restockCooldown > 0) {
                this.restockCooldown--;
            } else if (villager.getOffers().stream().anyMatch(MerchantOffer::needsRestock)) {
                villager.restock();
                this.restockCooldown = ENTITY_RESTOCK_COOLDOWN_TICKS;
            }
        }
    }

    private void snapToTarget(LivingEntity target) {
        this.setPos(target.getX(), target.getY() + target.getBbHeight() + 0.4D, target.getZ());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TICK_SPEED, 1);
        builder.define(REMAINING_TIME, DEFAULT_TOTAL_TIME);
        builder.define(TOTAL_TIME, DEFAULT_TOTAL_TIME);
        builder.define(TARGET_ENTITY_ID, NO_ENTITY_TARGET);
    }

    /** 是否为「生物目标」（否则是方块目标）。 */
    public boolean isEntityTarget() {
        return this.getTargetEntityId() != NO_ENTITY_TARGET;
    }

    public int getTargetEntityId() {
        return this.entityData.get(TARGET_ENTITY_ID);
    }

    public void setTargetEntityId(int entityId) {
        this.entityData.set(TARGET_ENTITY_ID, entityId);
    }

    public BlockPos getTargetPos() {
        return this.targetPos != null ? this.targetPos : this.blockPosition();
    }

    public void setTargetPos(BlockPos targetPos) {
        this.targetPos = targetPos == null ? null : targetPos.immutable();
    }

    public int getTickSpeed() {
        return this.entityData.get(TICK_SPEED);
    }

    public void setTickSpeed(int tickSpeed) {
        this.entityData.set(TICK_SPEED, tickSpeed);
    }

    public int getRemainingTime() {
        return this.entityData.get(REMAINING_TIME);
    }

    public void setRemainingTime(int remainingTime) {
        this.entityData.set(REMAINING_TIME, remainingTime);
    }

    public int getTotalTime() {
        return this.entityData.get(TOTAL_TIME);
    }

    public void setTotalTime(int totalTime) {
        this.entityData.set(TOTAL_TIME, totalTime);
    }

    public void addTime(int ticks) {
        this.setRemainingTime(this.getRemainingTime() + ticks);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("targetPos")) {
            this.targetPos = BlockPos.of(tag.getLong("targetPos"));
        }
        // 旧存档里的方块目标实体没有这个键，getInt 会返回 0（= 一个真实的实体 id），
        // 会被误判成「实体目标」而立刻自毁。必须显式回退到 NO_ENTITY_TARGET。
        this.setTargetEntityId(tag.contains("targetEntityId") ? tag.getInt("targetEntityId") : NO_ENTITY_TARGET);
        if (tag.hasUUID("targetEntityUuid")) {
            this.targetEntityUuid = tag.getUUID("targetEntityUuid");
        }
        this.setTickSpeed(tag.getInt("tickSpeed"));
        this.setRemainingTime(tag.getInt("remainingTime"));
        this.setTotalTime(tag.getInt("totalTime"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.targetPos != null) {
            tag.putLong("targetPos", this.targetPos.asLong());
        }
        tag.putInt("targetEntityId", this.getTargetEntityId());
        if (this.targetEntityUuid != null) {
            tag.putUUID("targetEntityUuid", this.targetEntityUuid);
        }
        tag.putInt("tickSpeed", this.getTickSpeed());
        tag.putInt("remainingTime", this.getRemainingTime());
        tag.putInt("totalTime", this.getTotalTime());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }
}
