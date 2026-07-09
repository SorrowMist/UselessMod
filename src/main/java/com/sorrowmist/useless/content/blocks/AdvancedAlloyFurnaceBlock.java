package com.sorrowmist.useless.content.blocks;

import appeng.items.tools.quartz.QuartzCuttingKnifeItem;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;
import com.glodblock.github.extendedae.container.ContainerRenamer;
import com.sorrowmist.useless.api.enums.FurnaceFace;
import com.sorrowmist.useless.api.enums.FurnaceFaceMode;
import com.sorrowmist.useless.api.enums.RedstoneControlMode;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout;
import com.sorrowmist.useless.core.component.FurnaceDataComponent;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.core.constants.NBTConstants;
import com.sorrowmist.useless.init.ModMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AdvancedAlloyFurnaceBlock extends Block implements EntityBlock {

    private static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    /**
     * 获取活跃状态属性（用于方块实体更新光照）
     *
     * @return 活跃状态属性
     */
    public static BooleanProperty getActiveProperty() {
        return ACTIVE;
    }

    public AdvancedAlloyFurnaceBlock() {
        super(BlockBehaviour.Properties.of()
                .noOcclusion()
                .strength(5.0F, 32768.0F)
                .requiresCorrectToolForDrops()
                .lightLevel(state -> state.getValue(ACTIVE) ? 15 : 7));

        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    /**
     * 方块被移除时的处理
     * <p>
     * 掉落方块实体中的所有物品
     *
     * @param state    当前状态
     * @param level    世界
     * @param pos      位置
     * @param newState 新状态
     * @param isMoving 是否在移动
     */
    @Override
    public void onRemove(BlockState state, @NotNull Level level, @NotNull BlockPos pos, BlockState newState,
                         boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
                // 掉落物品（不包括容器内的物品，它们会保存在方块物品中）
                // 注意：使用精准采集时，物品会通过 getDrops 保存到方块物品中
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /**
     * 获取方块掉落物
     * <p>
     * 将方块实体数据保存到物品的Data Component中
     * 如果熔炉全空（无物品、无能量、无流体、无模具、无进度、阶级为0），则不保存tag
     */
    @Override
    public @NotNull List<ItemStack> getDrops(@NotNull BlockState state, LootParams.@NotNull Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        
        // 获取方块实体
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
            // 为每个掉落物添加方块实体数据
            for (ItemStack drop : drops) {
                if (drop.is(this.asItem())) {
                    Component customName = furnace.getCustomName();
                    if (customName != null) {
                        drop.set(DataComponents.CUSTOM_NAME, customName);
                    }

                    // 检查是否全空
                    boolean isEmpty = isFurnaceEmpty(furnace);
                    
                    // 如果全空且不高于0阶，不保存任何数据
                    if (isEmpty && furnace.getFurnaceTier() <= 0) {
                        continue;
                    }
                    
                    CompoundTag blockEntityData = new CompoundTag();
                    // 保存所有数据到NBT
                    blockEntityData.putInt(NBTConstants.FURNACE_TIER, furnace.getFurnaceTier());
                    
                    // 保存物品栏（使用自定义序列化支持大堆叠）
                    IItemHandler itemHandler = furnace.getItemHandler();
                    if (itemHandler != null) {
                        CompoundTag inventoryTag = new CompoundTag();
                        ListTag itemsTag = new ListTag();
                        for (int i = 0; i < itemHandler.getSlots(); i++) {
                            ItemStack stack = itemHandler.getStackInSlot(i);
                            if (!stack.isEmpty()) {
                                CompoundTag itemTag = new CompoundTag();
                                itemTag.putByte("Slot", (byte) i);
                                // 保存物品ID
                                itemTag.put("id", StringTag.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
                                // 保存真实数量（支持超过99）
                                itemTag.putInt("RealCount", stack.getCount());
                                // 保存组件数据（如果有）
                                if (!stack.getComponentsPatch().isEmpty()) {
                                    CompoundTag saved = (CompoundTag) stack.save(params.getLevel().registryAccess());
                                    if (saved.contains("components")) {
                                        itemTag.put("components", saved.getCompound("components"));
                                    }
                                }
                                itemsTag.add(itemTag);
                            }
                        }
                        inventoryTag.put("Items", itemsTag);
                        blockEntityData.put(NBTConstants.INVENTORY, inventoryTag);
                    }
                    
                    // 保存能量
                    blockEntityData.putInt(NBTConstants.ENERGY, furnace.getEnergy());
                    
                    // 保存流体
                    for (int i = 0; i < AdvancedAlloyFurnaceLayout.FLUID_TANK_COUNT; i++) {
                        var inputTank = furnace.getInputFluidTank(i);
                        if (!inputTank.getFluid().isEmpty()) {
                            blockEntityData.put(NBTConstants.getInputFluidTag(i), 
                                inputTank.getFluid().save(params.getLevel().registryAccess()));
                        }
                        var outputTank = furnace.getOutputFluidTank(i);
                        if (!outputTank.getFluid().isEmpty()) {
                            blockEntityData.put(NBTConstants.getOutputFluidTag(i), 
                                outputTank.getFluid().save(params.getLevel().registryAccess()));
                        }
                    }
                    
                    // 保存其他状态
                    blockEntityData.putInt(NBTConstants.PROGRESS, furnace.getProgress());
                    blockEntityData.putInt(NBTConstants.MAX_PROGRESS, furnace.getMaxProgress());
                    blockEntityData.putInt(NBTConstants.CURRENT_PARALLEL, furnace.getData().get(0)); // 从data获取
                    blockEntityData.putBoolean(NBTConstants.HAS_MOLD, furnace.hasMold());

                    // 保存面模式
                    FurnaceFaceMode[] faceModes = furnace.getFaceModes();
                    blockEntityData.putInt("FaceModeTop", faceModes[FurnaceFace.TOP.ordinal()].ordinal());
                    blockEntityData.putInt("FaceModeBottom", faceModes[FurnaceFace.BOTTOM.ordinal()].ordinal());
                    blockEntityData.putInt("FaceModeFront", faceModes[FurnaceFace.FRONT.ordinal()].ordinal());
                    blockEntityData.putInt("FaceModeBack", faceModes[FurnaceFace.BACK.ordinal()].ordinal());
                    blockEntityData.putInt("FaceModeLeft", faceModes[FurnaceFace.LEFT.ordinal()].ordinal());
                    blockEntityData.putInt("FaceModeRight", faceModes[FurnaceFace.RIGHT.ordinal()].ordinal());

                    // 保存自动输入输出开关
                    blockEntityData.putBoolean("AutoInputEnabled", furnace.isAutoInputEnabled());
                    blockEntityData.putBoolean("AutoOutputEnabled", furnace.isAutoOutputEnabled());

                    // 保存红石控制模式
                    blockEntityData.putInt("RedstoneControlMode", furnace.getRedstoneControlMode().ordinal());
                    
                    // 使用Data Component存储数据
                    FurnaceDataComponent component = new FurnaceDataComponent(
                        furnace.getFurnaceTier(), 
                        blockEntityData
                    );
                    drop.set(UComponents.FURNACE_DATA.get(), component);
                }
            }
        }
        
        return drops;
    }
    
    /**
     * 检查熔炉是否全空
     * @param furnace 熔炉方块实体
     * @return 如果无物品、无能量、无流体、无模具、无进度返回true
     */
    private static boolean isFurnaceEmpty(AdvancedAlloyFurnaceBlockEntity furnace) {
        // 检查物品栏
        IItemHandler itemHandler = furnace.getItemHandler();
        if (itemHandler != null) {
            for (int i = 0; i < itemHandler.getSlots(); i++) {
                if (!itemHandler.getStackInSlot(i).isEmpty()) {
                    return false;
                }
            }
        }
        
        // 检查能量
        if (furnace.getEnergy() > 0) {
            return false;
        }
        
        // 检查流体
        for (int i = 0; i < AdvancedAlloyFurnaceLayout.FLUID_TANK_COUNT; i++) {
            if (!furnace.getInputFluidTank(i).getFluid().isEmpty()) {
                return false;
            }
            if (!furnace.getOutputFluidTank(i).getFluid().isEmpty()) {
                return false;
            }
        }
        
        // 检查模具
        if (furnace.hasMold()) {
            return false;
        }
        
        // 检查进度
        if (furnace.getProgress() > 0) {
            return false;
        }
        
        return true;
    }

    /**
     * 空手右键点击方块的处理
     * <p>
     * 打开方块 GUI
     *
     * @param state     方块状态
     * @param level     世界
     * @param pos       位置
     * @param player    玩家
     * @param hitResult 命中结果
     * @return 交互结果
     */
    @Override
    @SuppressWarnings("DataFlowIssue")
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state,
                                                        Level level,
                                                        @NotNull BlockPos pos,
                                                        @NotNull Player player,
                                                        @NotNull BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
            player.openMenu(furnace, buffer -> buffer.writeBlockPos(pos));
        }
        return InteractionResult.CONSUME;
    }

    /**
     * 手持物品右键点击方块的处理
     * <p>
     * 优先处理流体交互，然后打开 GUI
     *
     * @param stack     手持物品
     * @param state     方块状态
     * @param level     世界
     * @param pos       位置
     * @param player    玩家
     * @param hand      手
     * @param hitResult 命中结果
     * @return 物品交互结果
     */
    @Override
    @SuppressWarnings("DataFlowIssue")
    protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack stack,
                                                       @NotNull BlockState state,
                                                       Level level,
                                                       @NotNull BlockPos pos,
                                                       @NotNull Player player,
                                                       @NotNull InteractionHand hand,
                                                       @NotNull BlockHitResult hitResult) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof AdvancedAlloyFurnaceBlockEntity furnace)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!InteractionUtil.isInAlternateUseMode(player) && stack.getItem() instanceof QuartzCuttingKnifeItem) {
            if (!level.isClientSide) {
                MenuOpener.open(ContainerRenamer.TYPE, player, MenuLocators.forBlockEntity(furnace));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        // 流体交互
        IFluidHandler fluidHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (fluidHandler != null && isFluidContainer(stack)) {
            boolean success = FluidUtil.interactWithFluidHandler(player, hand, fluidHandler);
            if (success) {
                return ItemInteractionResult.SUCCESS;
            }
        }

        // 打开GUI
        player.openMenu(furnace, buffer -> buffer.writeBlockPos(pos));
        return ItemInteractionResult.CONSUME;
    }

    /**
     * 检查物品是否是流体容器
     *
     * @param stack 物品堆
     * @return 如果是流体容器返回true
     */
    private static boolean isFluidContainer(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getCapability(Capabilities.FluidHandler.ITEM) != null
                || FluidUtil.getFluidContained(stack).isPresent();
    }

    @Override
    protected @Nullable MenuProvider getMenuProvider(@NotNull BlockState state, Level level, @NotNull BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof MenuProvider ? (MenuProvider) blockEntity : null;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new AdvancedAlloyFurnaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,
                                                                  @NotNull BlockState state,
                                                                  @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, pos, st, be) -> {
            if (be instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
                AdvancedAlloyFurnaceBlockEntity.tick(lvl, furnace);
            }
        };
    }

    /**
     * 方块被放置时的处理
     * <p>
     * 从物品的Data Component中恢复方块实体数据
     */
    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, 
                            @Nullable net.minecraft.world.entity.LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        
        if (level.isClientSide) return;

        BlockEntity placedBlockEntity = level.getBlockEntity(pos);
        if (placedBlockEntity instanceof AdvancedAlloyFurnaceBlockEntity placedFurnace) {
            Component customName = stack.get(DataComponents.CUSTOM_NAME);
            if (customName != null) {
                placedFurnace.setName(customName.getString());
            }
        }
        
        // 从Data Component中恢复数据
        FurnaceDataComponent component = stack.get(UComponents.FURNACE_DATA.get());
        if (component != null) {
            CompoundTag blockEntityData = component.data();
            BlockEntity blockEntity = level.getBlockEntity(pos);
            
            if (blockEntity instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
                // 恢复阶级（这会触发容量更新）
                int tier = component.tier();
                if (tier > 0) {
                    furnace.tryUpgrade(tier);
                }
                
                // 恢复物品栏（使用自定义格式支持大堆叠）
                if (blockEntityData.contains(NBTConstants.INVENTORY)) {
                    CompoundTag inventoryTag = blockEntityData.getCompound(NBTConstants.INVENTORY);
                    if (inventoryTag.contains("Items")) {
                        ListTag itemsTag = inventoryTag.getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND);
                        for (int i = 0; i < itemsTag.size(); i++) {
                            CompoundTag itemTag = itemsTag.getCompound(i);
                            int slot = itemTag.getByte("Slot") & 255;
                            if (slot >= 0 && slot < AdvancedAlloyFurnaceLayout.TOTAL_SLOTS) {
                                // 读取物品ID
                                String itemId = itemTag.getString("id");
                                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
                                if (item != Items.AIR) {
                                    ItemStack itemStack = new ItemStack(item);
                                    // 读取真实数量
                                    if (itemTag.contains("RealCount")) {
                                        itemStack.setCount(itemTag.getInt("RealCount"));
                                    }
                                    // 读取组件数据
                                    if (itemTag.contains("components")) {
                                        ItemStack parsed = ItemStack.parseOptional(level.registryAccess(), itemTag);
                                        if (!parsed.isEmpty()) {
                                            itemStack.applyComponents(parsed.getComponentsPatch());
                                        }
                                    }
                                    furnace.setItemInSlot(slot, itemStack);
                                }
                            }
                        }
                    }
                }
                
                // 恢复能量
                if (blockEntityData.contains(NBTConstants.ENERGY)) {
                    furnace.setEnergy(blockEntityData.getInt(NBTConstants.ENERGY));
                }
                
                // 恢复流体
                for (int i = 0; i < AdvancedAlloyFurnaceLayout.FLUID_TANK_COUNT; i++) {
                    String inputTag = NBTConstants.getInputFluidTag(i);
                    if (blockEntityData.contains(inputTag)) {
                        FluidStack fluid = FluidStack.parseOptional(
                            level.registryAccess(), blockEntityData.getCompound(inputTag));
                        if (!fluid.isEmpty()) {
                            furnace.getInputFluidTank(i).setFluid(fluid);
                        }
                    }
                    
                    String outputTag = NBTConstants.getOutputFluidTag(i);
                    if (blockEntityData.contains(outputTag)) {
                        FluidStack fluid = FluidStack.parseOptional(
                            level.registryAccess(), blockEntityData.getCompound(outputTag));
                        if (!fluid.isEmpty()) {
                            furnace.getOutputFluidTank(i).setFluid(fluid);
                        }
                    }
                }
                
                // 恢复其他状态
                if (blockEntityData.contains(NBTConstants.PROGRESS)) {
                    furnace.setProgress(blockEntityData.getInt(NBTConstants.PROGRESS));
                }
                if (blockEntityData.contains(NBTConstants.MAX_PROGRESS)) {
                    furnace.setMaxProgress(blockEntityData.getInt(NBTConstants.MAX_PROGRESS));
                }
                if (blockEntityData.contains(NBTConstants.HAS_MOLD)) {
                    furnace.setHasMold(blockEntityData.getBoolean(NBTConstants.HAS_MOLD));
                }

                // 恢复面模式
                if (blockEntityData.contains("FaceModeTop")) {
                    furnace.setFaceMode(FurnaceFace.TOP, FurnaceFaceMode.byIndex(blockEntityData.getInt("FaceModeTop")));
                }
                if (blockEntityData.contains("FaceModeBottom")) {
                    furnace.setFaceMode(FurnaceFace.BOTTOM, FurnaceFaceMode.byIndex(blockEntityData.getInt("FaceModeBottom")));
                }
                if (blockEntityData.contains("FaceModeFront")) {
                    furnace.setFaceMode(FurnaceFace.FRONT, FurnaceFaceMode.byIndex(blockEntityData.getInt("FaceModeFront")));
                }
                if (blockEntityData.contains("FaceModeBack")) {
                    furnace.setFaceMode(FurnaceFace.BACK, FurnaceFaceMode.byIndex(blockEntityData.getInt("FaceModeBack")));
                }
                if (blockEntityData.contains("FaceModeLeft")) {
                    furnace.setFaceMode(FurnaceFace.LEFT, FurnaceFaceMode.byIndex(blockEntityData.getInt("FaceModeLeft")));
                }
                if (blockEntityData.contains("FaceModeRight")) {
                    furnace.setFaceMode(FurnaceFace.RIGHT, FurnaceFaceMode.byIndex(blockEntityData.getInt("FaceModeRight")));
                }

                // 恢复自动输入输出开关
                if (blockEntityData.contains("AutoInputEnabled")) {
                    furnace.setAutoInputEnabled(blockEntityData.getBoolean("AutoInputEnabled"));
                }
                if (blockEntityData.contains("AutoOutputEnabled")) {
                    furnace.setAutoOutputEnabled(blockEntityData.getBoolean("AutoOutputEnabled"));
                }

                // 恢复红石控制模式
                if (blockEntityData.contains("RedstoneControlMode")) {
                    furnace.setRedstoneControlMode(RedstoneControlMode.byIndex(blockEntityData.getInt("RedstoneControlMode")));
                }

                furnace.setChanged();
            }
        }
    }
}
