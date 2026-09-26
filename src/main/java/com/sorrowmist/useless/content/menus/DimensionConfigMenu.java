package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.init.ModMenuType;
import com.sorrowmist.useless.network.DimensionConfigGhostSlotPacket;
import com.sorrowmist.useless.network.DimensionConfigSubmitPacket;
import com.sorrowmist.useless.world.dimension.DimensionGenerationConfig;
import com.sorrowmist.useless.world.dimension.UselessDimensionConfigManager;
import com.sorrowmist.useless.world.dimension.UselessDimensions;
import com.sorrowmist.useless.world.teleport.AbstractDimensionTeleporter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public final class DimensionConfigMenu extends AbstractContainerMenu {
    private static final int PLAYER_INVENTORY_X = 16;
    private static final int PLAYER_INVENTORY_Y = 254;
    private static final int PLAYER_HOTBAR_Y = 312;

    public static final int BORDER_SLOT = 0;
    public static final int FILL_SLOT = 1;
    public static final int CENTER_SLOT = 2;
    public static final int BOUNDARY_A_SLOT = 3;
    public static final int BOUNDARY_B_SLOT = 4;
    public static final int ROAD_A_SLOT = 5;
    public static final int ROAD_B_SLOT = 6;
    public static final int ROAD_C_SLOT = 7;
    public static final int CENTER_MARKER_SLOT = 8;
    public static final int GHOST_SLOT_COUNT = 9;

    private final UUID playerId;
    private final ResourceKey<Level> targetDimension;
    private final boolean canTeleport;
    private final boolean firstSetup;
    private final ResourceKey<Level> sourceDimension;
    private final BlockPos sourcePos;
    @Nullable
    private final AbstractDimensionTeleporter teleporter;
    private final GhostSlot[] ghostSlots = new GhostSlot[GHOST_SLOT_COUNT];
    /** 打开菜单时该维度的原始配置，用于多联模式下保留边界与道路方块设置。 */
    private DimensionGenerationConfig initialConfig;
    /**
     * 菜单打开时该维度配置的只读快照。
     *
     * <p>多联模式下边界与道路槽位被隐藏，其方块值无法从槽位读取，必须以该维度的
     * 原始配置兜底。{@link #initialConfig} 会在每次提交或导入后被重写为当前编辑
     * 结果，无法承担该职责，因此单独保留一份不随编辑变化的快照。
     */
    private final DimensionGenerationConfig preservedConfig;

    private int platformLayers;
    private int platformStartY;
    private int boundaryIntervalX;
    private int boundaryIntervalZ;
    private int roadWidth;
    private DimensionGenerationConfig.Mode mode;
    private boolean centerMarkerEnabled;
    private boolean generateBedrock;
    private boolean bedrockAtBottom;

    public DimensionConfigMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, Context.read(buffer, inventory.player));
    }

    public DimensionConfigMenu(int containerId, Inventory inventory, Context context) {
        super(ModMenuType.DIMENSION_CONFIG_MENU.get(), containerId);
        this.playerId = inventory.player.getUUID();
        this.targetDimension = context.targetDimension();
        this.canTeleport = context.canTeleport();
        this.firstSetup = context.firstSetup();
        this.sourceDimension = context.sourceDimension();
        this.sourcePos = context.sourcePos().immutable();
        this.teleporter = context.teleporter();

        DimensionGenerationConfig config = context.initialConfig().normalized();
        this.initialConfig = config;
        this.preservedConfig = config;
        this.platformLayers = config.platformLayers();
        this.platformStartY = config.platformStartY();
        this.boundaryIntervalX = config.boundaryIntervalX();
        this.boundaryIntervalZ = config.boundaryIntervalZ();
        this.roadWidth = config.roadWidth();
        this.mode = config.mode();
        this.centerMarkerEnabled = config.centerMarkerEnabled();
        this.generateBedrock = config.generateBedrock();
        this.bedrockAtBottom = config.bedrockAtBottom();

        ghostSlots[BORDER_SLOT] = new GhostSlot(config.borderBlockId(), 16, 34);
        ghostSlots[FILL_SLOT] = new GhostSlot(config.fillBlockId(), 16, 52);
        ghostSlots[CENTER_SLOT] = new GhostSlot(config.centerBlockId(), 16, 70);
        ghostSlots[BOUNDARY_A_SLOT] = new GhostSlot(config.boundaryBlockAId(), 16, 100);
        ghostSlots[BOUNDARY_B_SLOT] = new GhostSlot(config.boundaryBlockBId(), 16, 118);
        ghostSlots[ROAD_A_SLOT] = new GhostSlot(config.roadBlockAId(), 16, 148);
        ghostSlots[ROAD_B_SLOT] = new GhostSlot(config.roadBlockBId(), 16, 165);
        ghostSlots[ROAD_C_SLOT] = new GhostSlot(config.roadBlockCId(), 16, 182);
        ghostSlots[CENTER_MARKER_SLOT] = new GhostSlot(config.centerMarkerBlockId(), 16, 212);
        for (GhostSlot slot : ghostSlots) addSlot(slot);
        updateSlotVisibility();
        addPlayerInventory(inventory);
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        PLAYER_INVENTORY_X + column * 18, PLAYER_INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, PLAYER_INVENTORY_X + column * 18,
                    PLAYER_HOTBAR_Y));
        }
    }

    public static void openForTeleport(ServerPlayer player, AbstractDimensionTeleporter teleporter,
                                       BlockPos sourcePos) {
        ResourceKey<Level> target = teleporter.targetDimensionFor(player.level().dimension());
        open(player, new Context(target, teleporter,
                player.level().dimension(), sourcePos,
                true, !UselessDimensionConfigManager.isConfigured(player.server, target),
                UselessDimensionConfigManager.get(player.server, target)));
    }

    public static void openForEdit(ServerPlayer player, AbstractDimensionTeleporter teleporter,
                                   BlockPos sourcePos) {
        // 以传送方块自身所属的维度为准，而不是玩家当前所在的维度：
        // 玩家可以在任一维度里放置并编辑属于其它维度的传送方块，
        // 若按玩家所在维度取目标，界面会显示并写入错误的维度配置。
        ResourceKey<Level> target = teleporter.dimensionKey();
        open(player, new Context(target, teleporter,
                player.level().dimension(), sourcePos, true,
                !UselessDimensionConfigManager.isConfigured(player.server, target),
                UselessDimensionConfigManager.get(player.server, target)));
    }

    private static void open(ServerPlayer player, Context context) {
        if (player.containerMenu instanceof DimensionConfigMenu) return;
        MenuProvider provider = new MenuProvider() {
            @Override
            public @NotNull Component getDisplayName() {
                return Component.translatable("menu.useless_mod.dimension_config");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
                return new DimensionConfigMenu(containerId, inventory, context);
            }
        };
        player.openMenu(provider, buffer -> context.write(buffer));
    }

    public ResourceKey<Level> getTargetDimension() {
        return targetDimension;
    }

    public boolean canTeleport() {
        return canTeleport;
    }

    public int getPlatformLayers() {
        return platformLayers;
    }

    public int getPlatformStartY() {
        return platformStartY;
    }

    public int getBoundaryIntervalX() {
        return boundaryIntervalX;
    }

    public int getBoundaryIntervalZ() {
        return boundaryIntervalZ;
    }

    public int getRoadWidth() {
        return roadWidth;
    }

    public DimensionGenerationConfig.Mode getMode() {
        return mode;
    }

    /** 是否处于多联模式：此时边界与道路配置整体隐藏。 */
    public boolean isMultiMode() {
        return mode == DimensionGenerationConfig.Mode.MULTI;
    }

    /** 模式按钮：在马路与多联之间循环。 */
    public void cycleMode() {
        mode = mode.next();
        updateSlotVisibility();
    }

    /** 按当前模式刷新幽灵槽位的显示状态：多联模式隐藏边界与道路相关槽位。 */
    private void updateSlotVisibility() {
        for (int index = 0; index < GHOST_SLOT_COUNT; index++) {
            ghostSlots[index].setHidden(!isGhostSlotActive(index));
        }
    }

    public boolean isCenterMarkerEnabled() {
        return centerMarkerEnabled;
    }

    public boolean isGenerateBedrock() {
        return generateBedrock;
    }

    public boolean isBedrockAtBottom() {
        return bedrockAtBottom;
    }

    public void setPlatformLayers(int value) {
        platformLayers = value;
    }

    public void setPlatformStartY(int value) {
        platformStartY = value;
    }

    public void setBoundaryIntervalX(int value) {
        boundaryIntervalX = value;
    }

    public void setBoundaryIntervalZ(int value) {
        boundaryIntervalZ = value;
    }

    public void setRoadWidth(int value) {
        roadWidth = value;
    }

    /** 边界间隔配置在马路模式下表示道路间隔，在多联模式下表示合并尺寸，因此始终显示。 */
    public boolean isBoundaryIntervalVisible() {
        return true;
    }

    /** 边界交替方块仅在马路模式使用；多联模式的合并组内部不再显示边框。 */
    public boolean isBoundarySlotVisible() {
        return mode != DimensionGenerationConfig.Mode.MULTI;
    }

    /** 道路宽度属于道路布局，仅马路模式可配置。 */
    public boolean isRoadWidthVisible() {
        return mode == DimensionGenerationConfig.Mode.ROAD;
    }

    /** 仅马路模式需要道路主体与中心标线配置。 */
    public boolean isRoadPatternSelected() {
        return mode == DimensionGenerationConfig.Mode.ROAD;
    }

    /** 中心标记仅马路模式可配置；多联模式不需要。 */
    public boolean isCenterMarkerVisible() {
        return mode != DimensionGenerationConfig.Mode.MULTI;
    }

    public void toggleCenterMarker() {
        centerMarkerEnabled = !centerMarkerEnabled;
    }

    public void toggleGenerateBedrock() {
        generateBedrock = !generateBedrock;
    }

    public void toggleBedrockAtBottom() {
        bedrockAtBottom = !bedrockAtBottom;
    }

    public GhostSlot getGhostSlot(int index) {
        return index >= 0 && index < GHOST_SLOT_COUNT ? ghostSlots[index] : null;
    }

    public boolean isGhostSlotActive(int index) {
        if (index < 0 || index >= GHOST_SLOT_COUNT) return false;
        return switch (index) {
            case BOUNDARY_A_SLOT, BOUNDARY_B_SLOT, ROAD_A_SLOT -> isRoadFeatureEnabled();
            case ROAD_B_SLOT, ROAD_C_SLOT -> isRoadFeatureEnabled()
                    && isRoadPatternSelected();
            case CENTER_MARKER_SLOT -> isCenterMarkerVisible();
            default -> true;
        };
    }

    private boolean isRoadFeatureEnabled() {
        // 多联模式不使用道路布局，相关槽位整体隐藏。
        if (mode == DimensionGenerationConfig.Mode.MULTI) return false;
        return DimensionGenerationConfig.areBoundaryAndRoadFeaturesEnabled(
                boundaryIntervalX, boundaryIntervalZ, roadWidth);
    }

    public boolean isCompleteConfiguration() {
        return createConfiguration().map(config -> config.isValid() && config.hasAllowedBlockIds()).orElse(false);
    }

    public Optional<DimensionGenerationConfig> createConfiguration() {
        ResourceLocation[] blocks = new ResourceLocation[GHOST_SLOT_COUNT];
        // 基础方块槽位在界面中始终可见，正常情况下必定有值；仅当槽位为空或
        // 引用了已失效的方块时才回落到该维度的原始配置，避免整份配置被判为
        // 不完整而让预览与保存一并失效。
        for (int i = BORDER_SLOT; i <= CENTER_SLOT; i++) {
            blocks[i] = DimensionGenerationConfig.blockId(ghostSlots[i].getItem());
            if (blocks[i] == null) {
                blocks[i] = switch (i) {
                    case BORDER_SLOT -> preservedConfig.borderBlockId();
                    case FILL_SLOT -> preservedConfig.fillBlockId();
                    default -> preservedConfig.centerBlockId();
                };
            }
        }
        // 多联模式下这些槽位被隐藏，槽位可能为空或已被导入预设改写，
        // 必须以该维度打开菜单时的原始配置回落，否则保存多联模式会把该维度
        // 已有的边界与道路方块覆盖成全局默认值，或覆盖成预设里的值。
        // 该回落基准不能使用 initialConfig：它在每次提交与导入后都会被改写。
        DimensionGenerationConfig fallback = isMultiMode()
                ? preservedConfig : DimensionGenerationConfig.defaults();
        blocks[BOUNDARY_A_SLOT] = optionalBlock(BOUNDARY_A_SLOT, fallback.boundaryBlockAId());
        blocks[BOUNDARY_B_SLOT] = optionalBlock(BOUNDARY_B_SLOT, fallback.boundaryBlockBId());
        blocks[ROAD_A_SLOT] = optionalBlock(ROAD_A_SLOT, fallback.roadBlockAId());
        blocks[ROAD_B_SLOT] = optionalBlock(ROAD_B_SLOT, fallback.roadBlockBId());
        blocks[ROAD_C_SLOT] = optionalBlock(ROAD_C_SLOT, fallback.roadBlockCId());
        blocks[CENTER_MARKER_SLOT] = optionalBlock(CENTER_MARKER_SLOT, fallback.centerMarkerBlockId());
        DimensionGenerationConfig.Features features = new DimensionGenerationConfig.Features(
                blocks[BOUNDARY_A_SLOT],
                blocks[BOUNDARY_B_SLOT],
                boundaryIntervalX,
                boundaryIntervalZ,
                roadWidth,
                mode,
                blocks[ROAD_A_SLOT],
                blocks[ROAD_B_SLOT],
                blocks[ROAD_C_SLOT],
                centerMarkerEnabled,
                blocks[CENTER_MARKER_SLOT]);
        return Optional.of(new DimensionGenerationConfig(
                blocks[BORDER_SLOT], blocks[FILL_SLOT], blocks[CENTER_SLOT],
                platformLayers, platformStartY, generateBedrock, bedrockAtBottom, features));
    }

    private ResourceLocation optionalBlock(int slot, ResourceLocation fallback) {
        ResourceLocation id = DimensionGenerationConfig.blockId(ghostSlots[slot].getItem());
        return id == null ? fallback : id;
    }

    public void setGhostSlotFromClient(int index, ItemStack stack) {
        if (!isGhostSlotActive(index)) return;
        ResourceLocation id = DimensionGenerationConfig.blockId(stack);
        if (id == null || !DimensionGenerationConfig.isValidBlockId(id)) return;
        GhostSlot slot = getGhostSlot(index);
        if (slot == null) return;
        slot.setBlockId(id);
        PacketDistributor.sendToServer(new DimensionConfigGhostSlotPacket(containerId, index, id));
    }

    public boolean setGhostBlockId(int index, @Nullable ResourceLocation id) {
        if (!isGhostSlotActive(index)) return false;
        GhostSlot slot = getGhostSlot(index);
        if (slot == null) return false;
        if (id != null && !DimensionGenerationConfig.isAllowedBlockId(id)) return false;
        slot.setBlockId(id);
        return true;
    }

    public void submit(ServerPlayer player, DimensionGenerationConfig requested, boolean teleportAfterSave) {
        if (!requested.isValid() || !requested.hasAllowedBlockIds()
                || !UselessDimensions.isUselessDimension(targetDimension)
                || !player.getUUID().equals(playerId)
                || !stillValid(player)) {
            player.displayClientMessage(Component.translatable("gui.useless_mod.dimension_config.invalid"), true);
            return;
        }

        DimensionGenerationConfig applied;
        try {
            applied = UselessDimensionConfigManager.save(
                    player.server, targetDimension, requested, firstSetup);
        } catch (IllegalArgumentException exception) {
            player.displayClientMessage(Component.translatable("gui.useless_mod.dimension_config.invalid"), true);
            return;
        }
        copyFrom(applied);

        if (teleportAfterSave && canTeleport() && sourceIsStillValid(player)) {
            player.closeContainer();
            teleporter.teleportAfterConfiguration(player, sourceDimension, sourcePos);
            return;
        }
        player.closeContainer();
    }

    /**
     * 套用导入的预设到当前编辑状态，但不提交给服务端：
     * 玩家仍需点击「应用」才会写入世界配置。
     */
    public void applyPreset(DimensionGenerationConfig config) {
        copyFrom(config);
    }

    private void copyFrom(DimensionGenerationConfig config) {
        initialConfig = config;
        platformLayers = config.platformLayers();
        platformStartY = config.platformStartY();
        boundaryIntervalX = config.boundaryIntervalX();
        boundaryIntervalZ = config.boundaryIntervalZ();
        roadWidth = config.roadWidth();
        mode = config.mode();
        updateSlotVisibility();
        centerMarkerEnabled = config.centerMarkerEnabled();
        generateBedrock = config.generateBedrock();
        bedrockAtBottom = config.bedrockAtBottom();
        ghostSlots[BORDER_SLOT].setBlockId(config.borderBlockId());
        ghostSlots[FILL_SLOT].setBlockId(config.fillBlockId());
        ghostSlots[CENTER_SLOT].setBlockId(config.centerBlockId());
        ghostSlots[BOUNDARY_A_SLOT].setBlockId(config.boundaryBlockAId());
        ghostSlots[BOUNDARY_B_SLOT].setBlockId(config.boundaryBlockBId());
        ghostSlots[ROAD_A_SLOT].setBlockId(config.roadBlockAId());
        ghostSlots[ROAD_B_SLOT].setBlockId(config.roadBlockBId());
        ghostSlots[ROAD_C_SLOT].setBlockId(config.roadBlockCId());
        ghostSlots[CENTER_MARKER_SLOT].setBlockId(config.centerMarkerBlockId());
    }

    private boolean sourceIsStillValid(ServerPlayer player) {
        return teleporter != null
                && player.level().dimension().equals(sourceDimension)
                && player.level().getBlockState(sourcePos).is(teleporter.getTeleportBlockForValidation());
    }

    @Override
    public void clicked(int slotId, int button, @NotNull ClickType clickType, @NotNull Player player) {
        if (slotId >= 0 && slotId < GHOST_SLOT_COUNT) {
            ItemStack carried = getCarried();
            setGhostBlockId(slotId,
                    !carried.isEmpty() && carried.getItem() instanceof BlockItem
                            ? DimensionGenerationConfig.blockId(carried) : null);
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return player.getUUID().equals(playerId)
                && player.level().dimension().equals(sourceDimension);
    }

    public record Context(ResourceKey<Level> targetDimension,
                          @Nullable AbstractDimensionTeleporter teleporter,
                          ResourceKey<Level> sourceDimension,
                          BlockPos sourcePos,
                          boolean canTeleport,
                          boolean firstSetup,
                          DimensionGenerationConfig initialConfig) {
        public void write(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(targetDimension.location());
            buffer.writeBoolean(canTeleport && teleporter != null);
            buffer.writeBoolean(firstSetup);
            initialConfig.write(buffer);
        }

        private static Context read(FriendlyByteBuf buffer, Player player) {
            ResourceKey<Level> target = ResourceKey.create(Registries.DIMENSION, buffer.readResourceLocation());
            boolean canTeleport = buffer.readBoolean();
            boolean firstSetup = buffer.readBoolean();
            return new Context(target, null, player.level().dimension(), player.blockPosition(),
                    canTeleport, firstSetup, DimensionGenerationConfig.read(buffer));
        }
    }

    public static final class GhostSlot extends Slot {
        private ItemStack stack;
        /** 隐藏的槽位既不渲染底板也不响应点击，用于多联模式下整体移除边界与道路配置。 */
        private boolean hidden;

        public boolean isHidden() {
            return hidden;
        }

        public void setHidden(boolean hidden) {
            this.hidden = hidden;
        }

        GhostSlot(ResourceLocation blockId, int x, int y) {
            super(new SimpleContainer(1), 0, x, y);
            this.stack = stackFor(blockId);
        }

        private static ItemStack stackFor(@Nullable ResourceLocation id) {
            if (id == null || !DimensionGenerationConfig.isValidBlockId(id)) return ItemStack.EMPTY;
            return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(id).asItem());
        }

        private void setBlockId(@Nullable ResourceLocation id) {
            stack = stackFor(id);
        }

        @Override
        public ItemStack getItem() {
            return stack;
        }

        @Override
        public boolean hasItem() {
            return !stack.isEmpty();
        }

        @Override
        public void set(ItemStack stack) {
            setBlockId(DimensionGenerationConfig.blockId(stack));
        }

        @Override
        public ItemStack remove(int amount) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof BlockItem;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean allowModification(Player player) {
            return false;
        }

        @Override
        public boolean isFake() {
            return true;
        }

        /** 隐藏后既不可渲染也不可交互，等同于该槽位不存在。 */
        @Override
        public boolean isActive() {
            return !hidden;
        }
    }
}
