package com.sorrowmist.useless.content.recipe.adapters.occultism;

import com.klikli_dev.occultism.common.entity.spirit.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
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
        if (entity == null || entityComponent == null) {
            return InteractionResult.FAIL;
        }
        entity.load(entityComponent.copyTag());
        if (entity instanceof TamableAnimal tamable) {
            tamable.tame(event.getEntity());
        }
        if (entity instanceof SpiritEntity spirit) {
            spirit.init();
        }

        BlockPos pos = event.getPos();
        Direction face = event.getFace() == null ? Direction.UP : event.getFace();
        if (!event.getLevel().getBlockState(pos).getCollisionShape(event.getLevel(), pos).isEmpty()) {
            pos = pos.relative(face);
        }
        entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, event.getEntity().getYRot(), 0.0F);
        if (!event.getLevel().addFreshEntity(entity)) {
            return InteractionResult.FAIL;
        }
        if (!event.getEntity().getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
