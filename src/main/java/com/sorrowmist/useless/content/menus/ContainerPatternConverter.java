package com.sorrowmist.useless.content.menus;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.NetworkHooks;
import appeng.client.gui.Icon;
import com.sorrowmist.useless.compat.forge1201.component.ComponentAccess;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.inventory.slot.PatternSlotItemHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public final class ContainerPatternConverter extends AEBaseMenu {
    /** 半尺寸设计的槽位节距：20px 底格 + 2px 间隙（与 PagedRecoverableMenu 一致）。 */
    private static final int SLOT_PITCH = 22;
    // 单栏布局（236 宽）：22 是底格 +2 后的物品坐标，27 个样板槽 9×3 在上，物品栏在下。
    private static final int PATTERN_X = 22;
    private static final int PATTERN_Y = 173;
    private static final int PLAYER_INVENTORY_X = 22;
    private static final int PLAYER_INVENTORY_Y = 311;
    private static final int PLAYER_HOTBAR_X = 22;
    private static final int PLAYER_HOTBAR_Y = 379;

    // AE2 15's builder queues registration in the AE2 namespace. Own this type here;
    // ModMenuType registers it once in our namespace, while AE2 owns locator handling.
    public static final MenuType<ContainerPatternConverter> TYPE = createType();

    private static MenuType<ContainerPatternConverter> createType() {
        MenuType<ContainerPatternConverter> type = IForgeMenuType.create(ContainerPatternConverter::fromNetwork);
        MenuOpener.addOpener(type, ContainerPatternConverter::open);
        return type;
    }

    private static ContainerPatternConverter fromNetwork(int id, Inventory inventory, FriendlyByteBuf data) {
        MenuLocator locator = MenuLocators.readFromPacket(data);
        HostPatternConverter host = locator.locate(inventory.player, HostPatternConverter.class);
        if (host == null) throw new IllegalStateException("Pattern converter item is no longer in its menu slot");
        ContainerPatternConverter menu = new ContainerPatternConverter(id, inventory, host);
        menu.setLocator(locator);
        menu.setReturnedFromSubScreen(data.readBoolean());
        return menu;
    }

    private static boolean open(Player player, MenuLocator locator, boolean returning) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        HostPatternConverter host = locator.locate(player, HostPatternConverter.class);
        if (host == null) return false;
        NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider((id, inventory, ignored) -> {
            ContainerPatternConverter menu = new ContainerPatternConverter(id, inventory, host);
            menu.setLocator(locator);
            return menu;
        }, Component.translatable("item.useless_mod.omniversal_pattern_converter")), data -> {
            MenuLocators.writeToPacket(data, locator);
            data.writeBoolean(returning);
        });
        return true;
    }

    public static final int CONVERT_BUTTON = 0;
    private final HostPatternConverter host;

    public ContainerPatternConverter(int containerId, Inventory playerInventory, HostPatternConverter host) {
        super(TYPE, containerId, playerInventory, host);
        this.host = host;

        IItemHandler patternHandler = host.getPatternInventory().toItemHandler();
        for (int slot = 0; slot < HostPatternConverter.PATTERN_SLOTS; slot++) {
            int column = slot % 9;
            int row = slot / 9;
            PatternSlotItemHandler patternSlot = new PatternSlotItemHandler(
                    patternHandler, slot,
                    PATTERN_X + column * SLOT_PITCH,
                    PATTERN_Y + row * SLOT_PITCH);
            patternSlot.setIcon(Icon.BACKGROUND_BLANK_PATTERN);
            this.addSlot(patternSlot, SlotSemantics.ENCODED_PATTERN);
        }

        addPlayerInventorySlots(playerInventory);
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int slot = column + row * 9;
                addSlot(new Slot(playerInventory, slot + 9,
                        PLAYER_INVENTORY_X + column * SLOT_PITCH,
                        PLAYER_INVENTORY_Y + row * SLOT_PITCH), SlotSemantics.PLAYER_INVENTORY);
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column,
                    PLAYER_HOTBAR_X + column * SLOT_PITCH,
                    PLAYER_HOTBAR_Y), SlotSemantics.PLAYER_HOTBAR);
        }
    }

    /**
     * 当前绑定的目标位置（来自转换器物品上的链接组件），未绑定时为 {@code null}。
     *
     * <p>组件随物品栈同步到客户端，因此界面可以直接展示「已经真实绑定到哪里」，
     * 而不是只给一句笼统的已绑定 / 未绑定提示。</p>
     */
    @Nullable
    public GlobalPos getLinkTarget() {
        return ComponentAccess.get(host.getItemStack(), UComponents.PATTERN_CONVERTER_LINK_TARGET.get());
    }

    /**
     * 已绑定目标方块实体（仅当它在当前维度且已加载时可解析）。
     *
     * <p>解析不到只说明目标没加载或已被拆除，链接本身仍然存在：界面据此退回显示维度与坐标，
     * 与 {@link HostPatternConverter} 在转换时判定 {@code TARGET_UNAVAILABLE} 的情形一致。</p>
     */
    @Nullable
    public BlockEntity getLinkedBlockEntity() {
        GlobalPos link = getLinkTarget();
        if (link == null) return null;
        Level level = host.getPlayer().level();
        if (!level.dimension().equals(link.dimension()) || !level.isLoaded(link.pos())) return null;
        return level.getBlockEntity(link.pos());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != CONVERT_BUTTON) return false;
        if (player.level().isClientSide()) return true;

        HostPatternConverter.ConversionResult result = host.convertPatterns();
        player.displayClientMessage(createResultMessage(result), true);
        broadcastChanges();
        return true;
    }

    private static Component createResultMessage(HostPatternConverter.ConversionResult result) {
        return switch (result.failure()) {
            case UNBOUND -> Component.translatable("message.useless_mod.pattern_converter.unbound");
            case TARGET_UNAVAILABLE -> Component.translatable("message.useless_mod.pattern_converter.target_unavailable");
            case NO_PATTERNS -> Component.translatable("message.useless_mod.pattern_converter.no_patterns");
            case INVALID_CONTEXT -> Component.translatable("message.useless_mod.pattern_converter.invalid_context");
            case NONE -> Component.translatable(
                    "message.useless_mod.pattern_converter.result", result.converted(), result.skipped());
        };
    }
}
