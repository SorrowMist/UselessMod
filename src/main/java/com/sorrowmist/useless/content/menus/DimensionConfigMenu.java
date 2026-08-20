package com.sorrowmist.useless.content.menus;

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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

import com.sorrowmist.useless.init.ModMenuType;

public final class DimensionConfigMenu extends AbstractContainerMenu {
    public static final int BORDER_SLOT = 0;
    public static final int FILL_SLOT = 1;
    public static final int CENTER_SLOT = 2;
    private static final int GHOST_SLOT_COUNT = 3;
    private final UUID playerId;
    private final ResourceKey<Level> targetDimension;
    private final boolean canTeleport;
    private final boolean firstSetup;
    private final ResourceKey<Level> sourceDimension;
    private final BlockPos sourcePos;
    @Nullable
    private final AbstractDimensionTeleporter teleporter;
    private final GhostSlot[] ghostSlots = new GhostSlot[GHOST_SLOT_COUNT];

    private int platformLayers;
    private int platformStartY;
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
        this.platformLayers = config.platformLayers();
        this.platformStartY = config.platformStartY();
        this.generateBedrock = config.generateBedrock();
        this.bedrockAtBottom = config.bedrockAtBottom();

        ghostSlots[0] = new GhostSlot(config.borderBlockId(), 16, 34);
        ghostSlots[1] = new GhostSlot(config.fillBlockId(), 16, 52);
        ghostSlots[2] = new GhostSlot(config.centerBlockId(), 16, 70);
        addSlot(ghostSlots[0]);
        addSlot(ghostSlots[1]);
        addSlot(ghostSlots[2]);
        addPlayerInventory(inventory);
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        16 + column * 18, 126 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 16 + column * 18, 184));
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
        ResourceKey<Level> current = player.level().dimension();
        ResourceKey<Level> target = UselessDimensions.isUselessDimension(current)
                ? current : teleporter.dimensionKey();
        open(player, new Context(target, teleporter,
                current, sourcePos, true,
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

    public boolean isFirstSetup() {
        return firstSetup;
    }

    public int getPlatformLayers() {
        return platformLayers;
    }

    public int getPlatformStartY() {
        return platformStartY;
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

    public void toggleGenerateBedrock() {
        generateBedrock = !generateBedrock;
    }

    public void toggleBedrockAtBottom() {
        bedrockAtBottom = !bedrockAtBottom;
    }

    public GhostSlot getGhostSlot(int index) {
        return index >= 0 && index < GHOST_SLOT_COUNT ? ghostSlots[index] : null;
    }

    public boolean isCompleteConfiguration() {
        return createConfiguration().map(config -> config.isValid() && config.hasValidBlockIds()).orElse(false);
    }

    public Optional<DimensionGenerationConfig> createConfiguration() {
        ResourceLocation border = DimensionGenerationConfig.blockId(ghostSlots[0].getItem());
        ResourceLocation fill = DimensionGenerationConfig.blockId(ghostSlots[1].getItem());
        ResourceLocation center = DimensionGenerationConfig.blockId(ghostSlots[2].getItem());
        if (border == null || fill == null || center == null) return Optional.empty();
        return Optional.of(new DimensionGenerationConfig(border, fill, center,
                platformLayers, platformStartY, generateBedrock, bedrockAtBottom));
    }

    public void setGhostSlotFromClient(int index, ItemStack stack) {
        ResourceLocation id = DimensionGenerationConfig.blockId(stack);
        if (id == null || !DimensionGenerationConfig.isValidBlockId(id)) return;
        GhostSlot slot = getGhostSlot(index);
        if (slot == null) return;
        slot.setBlockId(id);
        PacketDistributor.sendToServer(new DimensionConfigGhostSlotPacket(containerId, index, id));
    }

    public boolean setGhostBlockId(int index, @Nullable ResourceLocation id) {
        GhostSlot slot = getGhostSlot(index);
        if (slot == null) return false;
        if (id != null && !DimensionGenerationConfig.isValidBlockId(id)) return false;
        slot.setBlockId(id);
        return true;
    }

    public void submit(ServerPlayer player, DimensionGenerationConfig requested, boolean teleportAfterSave) {
        if (!requested.isValid() || !requested.hasValidBlockIds()
                || !UselessDimensions.isUselessDimension(targetDimension)
                || !player.getUUID().equals(playerId)
                || !stillValid(player)) {
            player.displayClientMessage(Component.translatable("gui.useless_mod.dimension_config.invalid"), true);
            return;
        }

        DimensionGenerationConfig applied = UselessDimensionConfigManager.save(
                player.server, targetDimension, requested, firstSetup);
        platformLayers = applied.platformLayers();
        platformStartY = applied.platformStartY();
        generateBedrock = applied.generateBedrock();
        bedrockAtBottom = applied.bedrockAtBottom();
        for (int i = 0; i < GHOST_SLOT_COUNT; i++) {
            ResourceLocation id = switch (i) {
                case BORDER_SLOT -> applied.borderBlockId();
                case FILL_SLOT -> applied.fillBlockId();
                default -> applied.centerBlockId();
            };
            ghostSlots[i].setBlockId(id);
        }

        if (teleportAfterSave && canTeleport() && sourceIsStillValid(player)) {
            player.closeContainer();
            teleporter.teleportAfterConfiguration(player, sourceDimension, sourcePos);
            return;
        }
        player.closeContainer();
    }

    private boolean sourceIsStillValid(ServerPlayer player) {
        return player.level().dimension().equals(sourceDimension)
                && player.level().getBlockState(sourcePos).is(teleporter.getTeleportBlockForValidation());
    }

    @Override
    public void clicked(int slotId, int button, @NotNull ClickType clickType, @NotNull Player player) {
        if (slotId >= 0 && slotId < GHOST_SLOT_COUNT) {
            ItemStack carried = getCarried();
            if (!carried.isEmpty() && carried.getItem() instanceof BlockItem) {
                setGhostBlockId(slotId, DimensionGenerationConfig.blockId(carried));
            } else {
                setGhostBlockId(slotId, null);
            }
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
    }
}
