package com.sorrowmist.useless.blocks.oregenerator;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

public class OreGeneratorBlock extends Block implements EntityBlock {
    // 在方块类内部直接定义注册器
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, UselessMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, UselessMod.MOD_ID);

    // 注册方块和物品
    public static final RegistryObject<Block> ORE_GENERATOR_BLOCK = BLOCKS.register("ore_generator_block",
            () -> new OreGeneratorBlock());

    public static final RegistryObject<Item> ORE_GENERATOR_BLOCK_ITEM = ITEMS.register("ore_generator_block",
            () -> new BlockItem(ORE_GENERATOR_BLOCK.get(), new Item.Properties()));


    // 方块构造函数
    public OreGeneratorBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(3.0F, 32768.0F)
                .requiresCorrectToolForDrops());
    }





    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof OreGeneratorBlockEntity oreGenerator) {

                ItemStack handItem = player.getItemInHand(hand);
                // 检查是否空手且按住Shift
                if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
                    // 取出物品
                    oreGenerator.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                        ItemStack stackInSlot = handler.getStackInSlot(0);
                        if (!stackInSlot.isEmpty()) {
                            // 将物品给玩家
                            if (!player.getInventory().add(stackInSlot)) {
                                // 如果玩家物品栏满了，掉落在地上
                                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stackInSlot);
                            }
                            // 清空槽位
                            handler.extractItem(0, stackInSlot.getCount(), false);
                        }
                    });
                    return InteractionResult.SUCCESS;

                }
                else if (!handItem.isEmpty() && !player.isShiftKeyDown()) {
                    oreGenerator.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                        ItemStack stackInSlot = handler.getStackInSlot(0);

                        // 如果槽位为空，可以放入物品
                        if (stackInSlot.isEmpty()) {
                            // 复制手持物品，但限制最大堆叠数为64
                            ItemStack toInsert = handItem.copy();
                            toInsert.setCount(Math.min(toInsert.getCount(), 64));

                            // 放入物品
                            handler.insertItem(0, toInsert, false);

                            // 减少玩家手持物品数量
                            handItem.shrink(toInsert.getCount());
                        }
                        // 如果槽位已有物品且是同一种类，可以合并
                        else if (ItemStack.isSameItemSameTags(stackInSlot, handItem) && stackInSlot.getCount() < 64) {
                            int spaceLeft = 64 - stackInSlot.getCount();
                            int toAdd = Math.min(handItem.getCount(), spaceLeft);

                            if (toAdd > 0) {
                                // 增加槽位物品数量
                                stackInSlot.grow(toAdd);
                                handler.extractItem(0, stackInSlot.getCount(), false); // 先取出
                                handler.insertItem(0, stackInSlot, false); // 再放入更新后的

                                // 减少玩家手持物品数量
                                handItem.shrink(toAdd);
                            }
                        }
                    });
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }


    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof OreGeneratorBlockEntity generator) {
                // 掉落方块实体中的物品
                generator.drops();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }


    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OreGeneratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, pos, st, be) -> {
            if (be instanceof OreGeneratorBlockEntity generator) {
                generator.tick();
            }
        };
    }
}