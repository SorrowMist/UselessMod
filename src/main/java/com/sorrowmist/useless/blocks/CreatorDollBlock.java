package com.sorrowmist.useless.blocks;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class CreatorDollBlock extends Block {

    // 注册器
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, UselessMod.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, UselessMod.MOD_ID);

    // 定义自定义碰撞箱，根据坐着的人形模型创建更精确的碰撞箱
    private static final VoxelShape COLLISION_SHAPE;
    
    static {
        // 头部碰撞箱（4.35-11.65 x 6-13.3 x 7.35-14.65）
        VoxelShape head = Shapes.box(
                4.35/16.0D, 6.0/16.0D, 7.35/16.0D,
                11.65/16.0D, 13.3/16.0D, 14.65/16.0D
        );
        
        // 身体碰撞箱（5.5-10.5 x 0-6 x 9.5-12.5）
        VoxelShape body = Shapes.box(
                5.5/16.0D, 0.0/16.0D, 9.5/16.0D,
                10.5/16.0D, 6.0/16.0D, 12.5/16.0D
        );
        
        // 右臂碰撞箱（简化版，不考虑旋转）
        VoxelShape rightArm = Shapes.box(
                10.65/16.0D, 0.34/16.0D, 10.0/16.0D,
                13.15/16.0D, 6.34/16.0D, 12.0/16.0D
        );
        
        // 左臂碰撞箱（简化版，不考虑旋转）
        VoxelShape leftArm = Shapes.box(
                2.67/16.0D, 0.42/16.0D, 10.0/16.0D,
                6.17/16.0D, 6.42/16.0D, 12.0/16.0D
        );
        
        // 右腿碰撞箱（简化版，不考虑旋转）
        VoxelShape rightLeg = Shapes.box(
                3.7/16.0D, 0.0/16.0D, 4.0/16.0D,
                8.0/16.0D, 2.0/16.0D, 10.0/16.0D
        );
        
        // 左腿碰撞箱（简化版，不考虑旋转）
        VoxelShape leftLeg = Shapes.box(
                8.0/16.0D, 0.0/16.0D, 4.0/16.0D,
                12.3/16.0D, 2.0/16.0D, 10.0/16.0D
        );
        
        // 组合所有碰撞箱
        COLLISION_SHAPE = Shapes.or(
                head, body, rightArm, leftArm, rightLeg, leftLeg
        );
    }

    // 注册方块和物品
    public static final RegistryObject<CreatorDollBlock> CREATOR_DOLL_BLOCK = BLOCKS.register("creator_doll_block",
            () -> new CreatorDollBlock(BlockBehaviour.Properties.of()
                    .strength(0.5F, 0.5F)
                    .dynamicShape()
                    .noOcclusion()));

    public static final RegistryObject<Item> CREATOR_DOLL_BLOCK_ITEM = ITEMS.register("creator_doll_block",
            () -> new BlockItem(CREATOR_DOLL_BLOCK.get(), new Item.Properties()));

    public CreatorDollBlock(Properties properties) {
        super(properties);
    }

    // 重写碰撞箱方法，使用自定义碰撞箱
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION_SHAPE;
    }

    // 重写选择箱方法，使用自定义碰撞箱
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION_SHAPE;
    }

    // 重写光线遮挡方法，确保光线能正确穿透
    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return COLLISION_SHAPE;
    }
}
