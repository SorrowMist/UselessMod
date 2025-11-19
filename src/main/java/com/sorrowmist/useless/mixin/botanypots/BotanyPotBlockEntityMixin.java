package com.sorrowmist.useless.mixin.botanypots;


import com.sorrowmist.useless.config.ConfigManager;
import net.darkhax.bookshelf.common.api.data.enchantment.EnchantmentLevel;
import net.darkhax.bookshelf.common.api.service.Services;
import net.darkhax.bookshelf.common.api.util.TickAccumulator;
import net.darkhax.botanypots.common.api.context.BlockEntityContext;
import net.darkhax.botanypots.common.api.data.recipes.crop.Crop;
import net.darkhax.botanypots.common.api.data.recipes.soil.Soil;
import net.darkhax.botanypots.common.impl.BotanyPotsMod;
import net.darkhax.botanypots.common.impl.Helpers;
import net.darkhax.botanypots.common.impl.block.entity.BotanyPotBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.darkhax.botanypots.common.impl.block.entity.AbstractBotanyPotBlockEntity.STORAGE_SLOTS;

@Mixin(BotanyPotBlockEntity.class)
public abstract class BotanyPotBlockEntityMixin {
    @Inject(
            method = "tickPot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/darkhax/bookshelf/common/api/util/TickAccumulator;tickUp(Lnet/minecraft/world/level/Level;)V"
            ),
            cancellable = true
    )
    private static void fasterGrowthInt(
            Level level, BlockPos pos, BlockState state, BotanyPotBlockEntity pot,
            CallbackInfo ci) {

        // 蜡封花盆或还没开始生长直接走原逻辑
        if (pot.growthTime() < 0) return;

        int mul = ConfigManager.getBotanyPotGrowthMultiplier();
        if (mul <= 1) return;

        // 客户端 tickrate 补偿
        float tickRateCompensation = level.isClientSide ? level.tickRateManager().tickrate() / 20.0F : 1.0F;
        float effectiveAdd = tickRateCompensation * mul; // 本 tick 要加的生长量

        // 获取作物和土壤
        Crop crop = pot.getOrInvalidateCrop();
        Soil soil = pot.getOrInvalidateSoil();
        if (crop == null || soil == null) return;

        int requiredGrowthTicks = Helpers.getRequiredGrowthTicks(pot.getRecipeContext(), level, crop, soil);
        if (requiredGrowthTicks <= 0) return;

        // ==================== 核心判断：本 tick 增加量是否 >= 完整生长周期 ====================
        if (effectiveAdd >= requiredGrowthTicks) {
            ci.cancel(); // 阻止原版 tickUp 和后续逻辑

            // 计算倍率（向下取整）
            float multiplier = effectiveAdd / requiredGrowthTicks;
            int harvestTimes = (int) multiplier; // 完整收获次数

            if (harvestTimes > 0 && level instanceof ServerLevel serverLevel) {
                BlockEntityContext context = pot.getRecipeContext();
                int baseRolls = Helpers.getLootRolls(context, level, crop, soil);

                // ========== 增量插入，防止 stack overflow ==========
                for (int i = 0; i < baseRolls; i++) {
                    crop.onHarvest(context, level, singleStack -> {
                        // singleStack 是单次掉落（count 通常 1）
                        int totalCountNeeded = singleStack.getCount() * harvestTimes; // 比如 1 * 5 = 5

                        ItemStack toInsert = singleStack.copy();
                        while (totalCountNeeded > 0) {
                            // 每次只取安全数量（不超过 64 或物品 maxStackSize）
                            int thisBatch = Math.min(totalCountNeeded, 64); // 或者 singleStack.getMaxStackSize()
                            toInsert.setCount(thisBatch);

                            totalCountNeeded -= thisBatch;

                            // 优先向下插入容器
                            if (pot.isHopper() && !serverLevel.getBlockState(pos.below()).isAir()) {
                                ItemStack remainder = Services.GAMEPLAY.inventoryInsert(
                                        serverLevel, pos.below(), Direction.UP, toInsert.copy());
                                if (!remainder.isEmpty()) {
                                    // 插不下的放回花盆产出格
                                    Services.GAMEPLAY.addItem(remainder, ((AbstractBotanyPotBlockEntityAccessor) pot).items(), STORAGE_SLOTS);
                                }
                            } else {
                                // 不是漏斗花盆 → 放回产出格
                                Services.GAMEPLAY.addItem(toInsert.copy(), ((AbstractBotanyPotBlockEntityAccessor) pot).items(), STORAGE_SLOTS);
                            }
                        }
                    });
                }

                // 工具耐久：按完整收获次数扣
                if (BotanyPotsMod.CONFIG.get().gameplay.damage_harvest_tool
                        && EnchantmentLevel.FIRST.get(Helpers.NEGATE_HARVEST_DAMAGE_TAG, pot.getHarvestItem()) <= 0) {
                    pot.getHarvestItem().hurtAndBreak(harvestTimes, serverLevel, null, item -> {});
                }

                // 触发方块变化事件
                level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
            }

            // 重置生长时间
            pot.growthTime.reset();

            // 设置满信号 + 收获冷却
            pot.updateComparatorLevel(15);
            ((BotanyPotBlockEntityAccessor) pot).getGrowCooldown().setTicks(5.0F);

            pot.markUpdated();
            return;
        }

        // ==================== 不大于：正常加速累积 ====================
        // 直接累积加速后的生长时间
        pot.growthTime.tick(effectiveAdd);

        // 加速冷却
        TickAccumulator growCooldown = ((BotanyPotBlockEntityAccessor) pot).getGrowCooldown();
        if (growCooldown.getTicks() > 0) {
            growCooldown.tick(-effectiveAdd);
        }
    }
}