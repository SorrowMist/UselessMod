package com.sorrowmist.useless.content.recipe.adapters.occultism;

import com.klikli_dev.occultism.common.entity.spirit.SpiritEntity;
import com.klikli_dev.occultism.common.entity.job.SpiritJob;
import com.klikli_dev.occultism.common.entity.job.SpiritJobFactory;
import com.klikli_dev.occultism.registry.OccultismSpiritJobs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Runtime-only handling for the component-bearing Occultism job spirit eggs. */
public final class OccultismSpiritEggHandler {
    private OccultismSpiritEggHandler() {
    }

    public static InteractionResult trySpawn(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SpawnEggItem egg)) {
            return InteractionResult.PASS;
        }
        CustomData marker = stack.get(DataComponents.CUSTOM_DATA);
        if (marker == null || !marker.copyTag().getBoolean(OccultismRitualRecipeAdapter.AUTO_TAME_MARKER)) {
            return InteractionResult.PASS;
        }
        if (event.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        Entity entity = egg.getType(stack).create(event.getLevel());
        CustomData entityComponent = stack.get(DataComponents.ENTITY_DATA);
        if (entity == null) {
            return InteractionResult.FAIL;
        }

        CompoundTag jobData = new CompoundTag();
        if (entityComponent != null) {
            CompoundTag entityData = entityComponent.copyTag();
            if (entityData.contains("spiritJob")) {
                jobData = entityData.getCompound("spiritJob").copy();
                entityData.remove("spiritJob");
            }
            entity.load(entityData);
        }
        if (entity instanceof TamableAnimal tamable) {
            tamable.tame(event.getEntity());
        }

        BlockPos pos = event.getPos();
        Direction face = event.getFace() == null ? Direction.UP : event.getFace();
        if (!event.getLevel().getBlockState(pos).getCollisionShape(event.getLevel(), pos).isEmpty()) {
            pos = pos.relative(face);
        }
        entity.moveTo(pos.getX() + 0.5D, pos.getY() + 0.0D, pos.getZ() + 0.5D,
                event.getEntity().getYRot(), 0.0F);

        if (entity instanceof SpiritEntity spirit) {
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

        SpiritJobFactory factory = OccultismSpiritJobs.REGISTRY.get(ResourceLocation.parse(factoryIdValue));
        if (factory == null) {
            return;
        }

        SpiritJob job = factory.create(spirit);
        // A generated egg intentionally stores only factoryId. Recreating the job from
        // its factory preserves configured defaults such as TraderJob's operation count.
        // Full saved job state is still restored when additional fields are present.
        if (jobData.size() > 1) {
            job.deserializeNBT(spirit.level().registryAccess(), jobData);
        }
        spirit.setJob(job);
    }
}
