package com.sorrowmist.useless.content.menus;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.network.NetworkHooks;
import appeng.client.gui.Icon;
import com.sorrowmist.useless.inventory.slot.PatternSlotItemHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.items.IItemHandler;

public final class ContainerPatternConverter extends AEBaseMenu {
    private static final int SLOT_SIZE = 18;
    private static final int PATTERN_X = 8;
    private static final int PATTERN_Y = 30;
    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 97;
    private static final int PLAYER_HOTBAR_X = 8;
    private static final int PLAYER_HOTBAR_Y = 155;

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
                    PATTERN_X + column * SLOT_SIZE,
                    PATTERN_Y + row * SLOT_SIZE);
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
                        PLAYER_INVENTORY_X + column * SLOT_SIZE,
                        PLAYER_INVENTORY_Y + row * SLOT_SIZE), SlotSemantics.PLAYER_INVENTORY);
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column,
                    PLAYER_HOTBAR_X + column * SLOT_SIZE,
                    PLAYER_HOTBAR_Y), SlotSemantics.PLAYER_HOTBAR);
        }
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
