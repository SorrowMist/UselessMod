package com.sorrowmist.useless.content.recipe.adapters.occultism;

import com.klikli_dev.occultism.common.entity.familiar.FamiliarEntity;
import com.klikli_dev.occultism.common.entity.job.SpiritJob;
import com.klikli_dev.occultism.common.entity.job.SpiritJobFactory;
import com.klikli_dev.occultism.common.entity.spirit.SpiritEntity;
import com.klikli_dev.occultism.registry.OccultismSpiritJobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime-only handling for the NBT-bearing Occultism job-spirit spawn eggs produced by the
 * alloy furnace. Forge 1.20.1 spawn eggs read entity data from the root {@code EntityTag}
 * compound; there is no custom-data component in this version.
 */
public final class OccultismSpiritEggHandler {
    private OccultismSpiritEggHandler() {
    }

    public static InteractionResult trySpawn(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SpawnEggItem egg)) {
            return InteractionResult.PASS;
        }
        CompoundTag stackTag = stack.getTag();
        if (stackTag == null
                || !stackTag.getBoolean(OccultismRitualRecipeAdapter.AUTO_TAME_MARKER)) {
            return InteractionResult.PASS;
        }
        if (event.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        EntityType<?> entityType = egg.getType(stackTag);
        Entity entity = entityType == null ? null : entityType.create(event.getLevel());
        if (entity == null) {
            return InteractionResult.FAIL;
        }

        CompoundTag jobData = new CompoundTag();
        CompoundTag entityData = stackTag.getCompound("EntityTag");
        if (!entityData.isEmpty()) {
            CompoundTag loaded = entityData.copy();
            if (loaded.contains("spiritJob", CompoundTag.TAG_COMPOUND)) {
                jobData = loaded.getCompound("spiritJob").copy();
                loaded.remove("spiritJob");
            }
            entity.load(loaded);
        }

        if (entity instanceof TamableAnimal tamable) {
            tamable.tame(event.getEntity());
        }
        if (entity instanceof FamiliarEntity familiar) {
            // FamiliarRitual uses its own owner field instead of TamableAnimal#tame.
            familiar.setFamiliarOwner(event.getEntity());
        }

        BlockPos pos = event.getPos();
        Direction face = event.getFace() == null ? Direction.UP : event.getFace();
        if (!event.getLevel().getBlockState(pos).getCollisionShape(event.getLevel(), pos).isEmpty()) {
            pos = pos.relative(face);
        }
        entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                event.getEntity().getYRot(), 0.0F);

        if (entity instanceof SpiritEntity spirit) {
            // SpiritEntity#readAdditionalSaveData would try to rebuild the job through the
            // registry without the brain. Restore it explicitly after loading the rest of the
            // entity so a generated egg only needs the native factoryId shape.
            restoreSpiritJob(spirit, jobData);
            spirit.init();
        }

        if (!event.getLevel().addFreshEntity(entity)) {
            return InteractionResult.FAIL;
        }
        if (!event.getEntity().getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    private static void restoreSpiritJob(SpiritEntity spirit, CompoundTag jobData) {
        String factoryIdValue = jobData.getString("factoryId");
        if (factoryIdValue.isEmpty()) {
            return;
        }

        SpiritJobFactory factory = spiritJobFactory(new ResourceLocation(factoryIdValue));
        if (factory == null) {
            return;
        }

        SpiritJob job = factory.create(spirit);
        // A generated egg intentionally stores only factoryId. Recreating the job from its
        // factory preserves configured defaults such as TraderJob's operation count. Full saved
        // job state is still restored when additional fields are present.
        if (jobData.size() > 1) {
            job.deserializeNBT(jobData);
        }
        spirit.setJob(job);
    }

    @Nullable
    private static SpiritJobFactory spiritJobFactory(ResourceLocation id) {
        try {
            var registry = OccultismSpiritJobs.REGISTRY.get();
            return registry == null ? null : registry.getValue(id);
        } catch (RuntimeException exception) {
            // The registry is unavailable outside a live Forge game; leave the spirit job-less
            // rather than letting a malformed/unknown factory id break entity spawning.
            return null;
        }
    }
}
