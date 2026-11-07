package com.sorrowmist.useless.content.blockentities.multiblock;

import com.sorrowmist.useless.content.menus.MufflerHatchMenu;
import com.sorrowmist.useless.content.multiblock.MufflerAshRules;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.init.ModBlockEntities;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/** Top-centre, upward exhaust. Runtime is driven by its formed controller, including while paused. */
public final class MufflerHatchBlockEntity extends OwnedFurnacePartBlockEntity implements MenuProvider {
    public static final int EXHAUST_HEIGHT = 2;
    private final ItemStackHandler output = new ItemStackHandler(1) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final IItemHandler ashExtractOnly = new IItemHandler() {
        @Override public int getSlots() { return output.getSlots(); }
        @Override public ItemStack getStackInSlot(int slot) { return output.getStackInSlot(slot).copy(); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
        @Override public ItemStack extractItem(int slot, int count, boolean simulate) {
            return output.extractItem(slot, count, simulate);
        }
        @Override public int getSlotLimit(int slot) { return output.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
    };
    private int ashProgress;

    public MufflerHatchBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.MUFFLER_HATCH.get(), pos, state);
    }

    MufflerHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override protected boolean isClaimedBy(MultiblockAlloyFurnaceCoreBlockEntity core) {
        return core.claimsMufflerHatch(worldPosition);
    }

    public static boolean isExhaustClear(BlockGetter level, BlockPos pos) {
        for (int dy = 1; dy <= EXHAUST_HEIGHT; dy++) {
            if (!level.getBlockState(pos.above(dy)).isAir()) return false;
        }
        return true;
    }

    public boolean isObstructed() {
        var core = getController();
        return core != null && core.isMufflerObstructed();
    }

    /** Menu-only mutable backing inventory; external capabilities MUST use getAshOutput instead. */
    public IItemHandler getOutput() { return output; }
    public IItemHandler getAshOutput() { return ashExtractOnly; }

    public boolean isAshFull() {
        return !output.insertItem(0, new ItemStack(ModItems.ASH.get()), true).isEmpty();
    }

    public void tickFromController(boolean furnaceWorking) {
        if (unloading || isRemoved() || !(level instanceof ServerLevel server)) return;
        boolean clear = isExhaustClear(server, worldPosition);
        if (clear && (furnaceWorking || !output.getStackInSlot(0).isEmpty())) emitSmoke(server);
        if (furnaceWorking && clear) accumulateAsh(server);
        tickToxicEffects(server);
    }

    private void emitSmoke(ServerLevel server) {
        if (server.getGameTime() % 4L != 0L) return;
        for (int i = 0; i < 3; i++) {
            double x = worldPosition.getX() + 0.5 + (server.random.nextDouble() - 0.5) * 0.35;
            double z = worldPosition.getZ() + 0.5 + (server.random.nextDouble() - 0.5) * 0.35;
            server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, worldPosition.getY() + 1.05,
                    z, 0, 0.0, 1.0, 0.0, 0.07);
        }
    }

    private void accumulateAsh(ServerLevel server) {
        if (isAshFull()) return;
        var core = getController();
        int suppression = core == null ? 0 : ConfigManager.getOmniversalCoilAshSuppression(core.getCoilTier());
        if (MufflerAshRules.isProductionSuppressed(suppression, server.random.nextInt(100))) return;
        if (++ashProgress >= MufflerAshRules.ASH_INTERVAL_TICKS) {
            ItemStack remainder = output.insertItem(0, new ItemStack(ModItems.ASH.get()), false);
            ashProgress = remainder.isEmpty() ? 0 : MufflerAshRules.ASH_INTERVAL_TICKS - 1;
        }
        setChanged(); // Persist partial progress too, not just completed ash stacks.
    }

    private void tickToxicEffects(ServerLevel server) {
        if (!ConfigManager.isAshToxicEnabled() || server.getGameTime() % 20L != 0L) return;
        int chance = MufflerAshRules.toxicChancePermille(ConfigManager.getAshToxicMaxChancePermille(),
                output.getStackInSlot(0).getCount(), output.getSlotLimit(0));
        if (chance <= 0 || server.random.nextInt(1000) >= chance) return;
        int duration = Math.max(1, Math.min(60, ConfigManager.getAshToxicDurationSeconds())) * 20;
        double radius = Math.max(0.5, Math.min(32.0, ConfigManager.getAshToxicRadius()));
        double x = worldPosition.getX() + 0.5, y = worldPosition.getY() + 0.5, z = worldPosition.getZ() + 0.5;
        AABB area = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        for (LivingEntity entity : server.getEntitiesOfClass(LivingEntity.class, area)) {
            entity.addEffect(new MobEffectInstance(MobEffects.POISON, duration, 0));
            entity.addEffect(new MobEffectInstance(MobEffects.WITHER, duration, 0));
        }
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        ItemStack stack = output.extractItem(0, output.getSlotLimit(0), false);
        if (!stack.isEmpty()) level.addFreshEntity(new ItemEntity(level, worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack));
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        ashProgress = Math.max(0, Math.min(MufflerAshRules.ASH_INTERVAL_TICKS - 1, tag.getInt("AshProgress")));
        output.deserializeNBT(tag.getCompound("Output"));
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("AshProgress", ashProgress);
        tag.put("Output", output.serializeNBT());
    }

    @Override public Component getDisplayName() { return Component.translatable("block.useless_mod.muffler_hatch"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MufflerHatchMenu(id, inventory, worldPosition);
    }
}
