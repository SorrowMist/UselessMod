package com.sorrowmist.useless.content.blocks;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.EnumColor;
import com.sorrowmist.useless.content.blockentities.PlasticThermostatBlockEntity;
import com.sorrowmist.useless.content.blockentities.PlasticThermostatHeatHook;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.utils.mining.MiningUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GlowPlasticBlock extends Block implements IColoredBlock, EntityBlock {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(UselessMod.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(UselessMod.MODID);

    public static final Map<EnumColor, DeferredBlock<GlowPlasticBlock>> GLOW_PLASTIC_BLOCKS = new LinkedHashMap<>();
    public static final Map<EnumColor, DeferredItem<Item>> GLOW_PLASTIC_BLOCK_ITEMS = new LinkedHashMap<>();
    public static final Map<EnumColor, DeferredBlock<GlowPlasticBlock>> PLASTIC_BLOCKS = new LinkedHashMap<>();
    public static final Map<EnumColor, DeferredItem<Item>> PLASTIC_BLOCK_ITEMS = new LinkedHashMap<>();
    public static final Map<EnumColor, DeferredBlock<GlowPlasticBlock>> PLASTIC_CTM_BLOCKS = new LinkedHashMap<>();
    public static final Map<EnumColor, DeferredItem<Item>> PLASTIC_CTM_BLOCK_ITEMS = new LinkedHashMap<>();
    public static final Map<EnumColor, DeferredBlock<GlowPlasticBlock>> GLOW_PLASTIC_CTM_BLOCKS = new LinkedHashMap<>();
    public static final Map<EnumColor, DeferredItem<Item>> GLOW_PLASTIC_CTM_BLOCK_ITEMS = new LinkedHashMap<>();

    public static final List<Map<EnumColor, DeferredBlock<GlowPlasticBlock>>> ALL_BLOCK_MAPS = List.of(
            PLASTIC_BLOCKS, GLOW_PLASTIC_BLOCKS, PLASTIC_CTM_BLOCKS, GLOW_PLASTIC_CTM_BLOCKS);
    public static final List<Map<EnumColor, DeferredItem<Item>>> ALL_BLOCK_ITEM_MAPS = List.of(
            PLASTIC_BLOCK_ITEMS, GLOW_PLASTIC_BLOCK_ITEMS,
            PLASTIC_CTM_BLOCK_ITEMS, GLOW_PLASTIC_CTM_BLOCK_ITEMS);

    static {
        for (EnumColor color : EnumColor.valuesInOrder()) {
            registerVariant(color, color.getRegistryPrefix() + "_glow_plastic",
                    true, false, GLOW_PLASTIC_BLOCKS, GLOW_PLASTIC_BLOCK_ITEMS);
            registerVariant(color, color.getRegistryPrefix() + "_plastic",
                    false, false, PLASTIC_BLOCKS, PLASTIC_BLOCK_ITEMS);
            registerVariant(color, color.getRegistryPrefix() + "_plastic_ctm",
                    false, true, PLASTIC_CTM_BLOCKS, PLASTIC_CTM_BLOCK_ITEMS);
            registerVariant(color, color.getRegistryPrefix() + "_glow_plastic_ctm",
                    true, true, GLOW_PLASTIC_CTM_BLOCKS, GLOW_PLASTIC_CTM_BLOCK_ITEMS);
        }
    }

    private final EnumColor color;
    private final boolean glowing;
    private final boolean connectedTexture;

    private GlowPlasticBlock(EnumColor color, boolean glowing, boolean connectedTexture) {
        super(createProperties(color, glowing));
        this.color = color;
        this.glowing = glowing;
        this.connectedTexture = connectedTexture;
    }

    private static BlockBehaviour.Properties createProperties(EnumColor color, boolean glowing) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .mapColor(color.getMapColor())
                .strength(5F, 6F)
                .requiresCorrectToolForDrops();
        return glowing ? properties.lightLevel(state -> 15) : properties;
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack heldItem,
                                                       @NotNull BlockState state,
                                                       @NotNull Level level,
                                                       @NotNull BlockPos pos,
                                                       Player player,
                                                       @NotNull InteractionHand hand,
                                                       @NotNull BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                if (heldItem.getItem() instanceof EndlessBeafItem) {
                    MiningUtils.quickBreakBlock(level, pos, state, player, heldItem);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(heldItem, state, level, pos, player, hand, hit);
    }

    @Override
    public EnumColor getColor() {
        return this.color;
    }

    public boolean isGlowing() {
        return this.glowing;
    }

    public boolean hasConnectedTexture() {
        return this.connectedTexture;
    }

    private static void registerVariant(
            EnumColor color, String registryName, boolean glowing, boolean connectedTexture,
            Map<EnumColor, DeferredBlock<GlowPlasticBlock>> blocks,
            Map<EnumColor, DeferredItem<Item>> items) {
        DeferredBlock<GlowPlasticBlock> block = BLOCKS.register(
                registryName,
                () -> new GlowPlasticBlock(color, glowing, connectedTexture));
        blocks.put(color, block);

        DeferredItem<Item> item = ITEMS.register(
                registryName,
                () -> new BlockItem(block.get(), new Item.Properties()));
        items.put(color, item);
    }

    // ------------------------------------------------------------------ 恒温源

    /**
     * 全部塑料方块（18 色 × 4 变体 = 72 个），供 {@code BlockEntityType} 注册使用。
     *
     * <p>一个 BE 类型服务全部变体，因此不必给每种颜色各注册一份。</p>
     */
    public static Block[] allBlocks() {
        List<Block> blocks = new ArrayList<>(72);
        for (Map<EnumColor, DeferredBlock<GlowPlasticBlock>> map : ALL_BLOCK_MAPS) {
            for (DeferredBlock<GlowPlasticBlock> holder : map.values()) {
                blocks.add(holder.get());
            }
        }
        return blocks.toArray(new Block[0]);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new PlasticThermostatBlockEntity(pos, state);
    }

    /**
     * 恒温源的「主动驱动」tick。
     *
     * <p>Mekanism 的传热是<b>发送方驱动</b>（见 {@link PlasticThermostatHeatHook}），所以必须由方块
     * 自己每 tick 把热推给邻居。未配置（{@code enabled == false}）时直接返回——绝大多数塑料方块
     * 从未被配置过，因此这一条几乎零开销。</p>
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level,
                                                                  @NotNull BlockState state,
                                                                  @NotNull BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (!(blockEntity instanceof PlasticThermostatBlockEntity thermostat) || !thermostat.isEnabled()) {
                return;
            }
            PlasticThermostatHeatHook.HeatDriver driver = PlasticThermostatHeatHook.driver;
            if (driver != null && tickLevel instanceof ServerLevel serverLevel) {
                driver.tick(serverLevel, pos, thermostat);
            }
        };
    }
}
