package com.sorrowmist.useless.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.sorrowmist.useless.api.enums.tool.ConstructionWandCoreMode;
import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.api.enums.tool.ModeTypeEnum;
import com.sorrowmist.useless.api.enums.tool.ToolTypeMode;
import com.sorrowmist.useless.content.items.BeefToolVariants;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.network.ConstructionWandCorePacket;
import com.sorrowmist.useless.network.EnchantmentSwitchPacket;
import com.sorrowmist.useless.network.ModeTogglePacket;
import com.sorrowmist.useless.network.ToolTypeModeSwitchPacket;
import com.sorrowmist.useless.utils.UselessItemUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class ModeWheelScreen extends Screen {
    private static final int PANEL_MAX_WIDTH = 420;
    private static final int PANEL_MARGIN = 8;
    private static final int SECTION_COLUMN_GAP = 6;
    private static final int SECTION_CONTENT_TOP = 21;
    private static final int SECTION_TITLE_HEIGHT = 15;
    private static final int SECTION_BOTTOM_PADDING = 6;
    private static final int PANEL_BOTTOM_PADDING = 8;
    private static final int DEFAULT_BUTTON_HEIGHT = 18;

    private static final boolean hasGtceuMod = ModList.get().isLoaded("gtceu");
    private static final boolean hasOmnitoolMod = ModList.get().isLoaded("omnitools");
    private static final boolean hasAE2 = ModList.get().isLoaded("ae2");

    private final List<ModeData> toolModes = new ArrayList<>();
    private final List<ModeData> miningModes = new ArrayList<>();
    private final List<ModeData> combatModes = new ArrayList<>();
    private final List<ModeData> auxiliaryModes = new ArrayList<>();
    private final List<PressableAE2Button> modeButtons = new ArrayList<>();

    private ItemStack targetItem;
    private List<SectionLayout> sectionLayouts = List.of();
    private String modeLayoutSignature = "";
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int buttonHeight;
    private int buttonGap;
    private int sectionGap;

    public ModeWheelScreen(ItemStack targetItem) {
        super(Component.translatable("gui.useless_mod.mode_config.title"));
        this.targetItem = targetItem;
        this.loadModesFromEnums();
    }

    private void loadModesFromEnums() {
        this.toolModes.clear();
        this.miningModes.clear();
        this.combatModes.clear();
        this.auxiliaryModes.clear();

        if (this.targetItem == null || this.targetItem.isEmpty()) {
            return;
        }

        EnchantMode currentEnchant = this.targetItem.get(UComponents.EnchantModeComponent);
        ToolTypeMode currentTool = this.targetItem.get(UComponents.CurrentToolTypeComponent);
        boolean chainMiningEnabled = this.targetItem.getOrDefault(UComponents.EnhancedChainMiningComponent, false);
        boolean forceMiningEnabled = this.targetItem.getOrDefault(UComponents.ForceMiningComponent, false);
        boolean aeStorageEnabled = this.targetItem.getOrDefault(UComponents.AEStoragePriorityComponent, false);
        boolean forceKillEnabled = this.targetItem.getOrDefault(UComponents.ForceKillEnabledComponent, false);
        boolean beefTimeAccelerationEnabled = this.targetItem.getOrDefault(
                UComponents.BeefTimeAccelerationEnabledComponent, false);
        boolean beefInvulnerabilityEnabled = this.targetItem.getOrDefault(
                UComponents.BeefInvulnerabilityEnabledComponent, false);
        boolean beefCaptureEnabled = this.targetItem.getOrDefault(UComponents.BeefCaptureEnabledComponent, false);
        boolean beefTeleportEnabled = this.targetItem.getOrDefault(UComponents.BeefTeleportEnabledComponent, false);
        boolean beefAoeDamageEnabled = this.targetItem.getOrDefault(UComponents.BeefAoeDamageEnabledComponent, false);
        boolean beefMagnetEnabled = this.targetItem.getOrDefault(UComponents.BeefMagnetEnabledComponent, false);
        boolean wrenchTagEnabled = this.targetItem.getOrDefault(UComponents.WrenchTagEnabledComponent, true);
        boolean constructionWandEnabled = this.targetItem.getOrDefault(
                UComponents.ConstructionWandEnabledComponent, false);
        ConstructionWandCoreMode constructionWandCore = this.targetItem.getOrDefault(
                UComponents.ConstructionWandCoreComponent, ConstructionWandCoreMode.DEFAULT);

        for (EnchantMode mode : EnchantMode.values()) {
            this.toolModes.add(new ModeData(mode, mode.getTooltip(), mode == currentEnchant));
        }
        for (ToolTypeMode mode : ToolTypeMode.values()) {
            boolean shouldAdd = switch (mode) {
                case NONE_MODE -> hasGtceuMod || hasOmnitoolMod;
                case WRENCH_MODE, SCREWDRIVER_MODE, MALLET_MODE, CROWBAR_MODE, HAMMER_MODE -> hasGtceuMod;
                case OMNITOOL_MODE -> hasOmnitoolMod;
            };
            if (shouldAdd) {
                this.toolModes.add(new ModeData(mode, mode.getTooltip(), mode == currentTool));
            }
        }

        if (this.targetItem.getItem() instanceof EndlessBeafItem) {
            this.miningModes.add(new ModeData(
                    ModeTypeEnum.getConstructionWandMode(constructionWandEnabled),
                    ModeTypeEnum.getConstructionWandMode(constructionWandEnabled).getTooltip(),
                    constructionWandEnabled));
            this.miningModes.add(new ModeData(
                    ModeTypeEnum.CONSTRUCTION_WAND_ANGEL_CORE,
                    ModeTypeEnum.CONSTRUCTION_WAND_ANGEL_CORE.getTooltip(),
                    constructionWandCore == ConstructionWandCoreMode.ANGEL));
            this.miningModes.add(new ModeData(
                    ModeTypeEnum.CONSTRUCTION_WAND_DESTRUCTION_CORE,
                    ModeTypeEnum.CONSTRUCTION_WAND_DESTRUCTION_CORE.getTooltip(),
                    constructionWandCore == ConstructionWandCoreMode.DESTRUCTION));
        }
        this.miningModes.add(new ModeData(
                ModeTypeEnum.getEnhancedChainMiningMode(chainMiningEnabled),
                ModeTypeEnum.getEnhancedChainMiningMode(chainMiningEnabled).getTooltip(),
                chainMiningEnabled));
        this.miningModes.add(new ModeData(
                ModeTypeEnum.getForceMiningMode(forceMiningEnabled),
                ModeTypeEnum.getForceMiningMode(forceMiningEnabled).getTooltip(),
                forceMiningEnabled));
        if (hasAE2) {
            this.miningModes.add(new ModeData(
                    ModeTypeEnum.getAEStoragePriorityMode(aeStorageEnabled),
                    ModeTypeEnum.getAEStoragePriorityMode(aeStorageEnabled).getTooltip(),
                    aeStorageEnabled));
        }
        if (BeefToolVariants.isBaseVariant(this.targetItem)) {
            this.miningModes.add(new ModeData(
                    ModeTypeEnum.getWrenchTagMode(wrenchTagEnabled),
                    ModeTypeEnum.getWrenchTagMode(wrenchTagEnabled).getTooltip(),
                    wrenchTagEnabled));
        }

        this.combatModes.add(new ModeData(
                ModeTypeEnum.FORCE_KILL,
                ModeTypeEnum.FORCE_KILL.getTooltip(),
                forceKillEnabled));
        if (this.targetItem.getItem() instanceof EndlessBeafItem) {
            this.combatModes.add(new ModeData(
                    ModeTypeEnum.getBeefCaptureMode(beefCaptureEnabled),
                    ModeTypeEnum.getBeefCaptureMode(beefCaptureEnabled).getTooltip(),
                    beefCaptureEnabled));
            this.combatModes.add(new ModeData(
                    ModeTypeEnum.getBeefAoeDamageMode(beefAoeDamageEnabled),
                    ModeTypeEnum.getBeefAoeDamageMode(beefAoeDamageEnabled).getTooltip(),
                    beefAoeDamageEnabled));
        }

        if (this.targetItem.getItem() instanceof EndlessBeafItem) {
            this.auxiliaryModes.add(new ModeData(
                    ModeTypeEnum.getBeefTimeAccelerationMode(beefTimeAccelerationEnabled),
                    ModeTypeEnum.getBeefTimeAccelerationMode(beefTimeAccelerationEnabled).getTooltip(),
                    beefTimeAccelerationEnabled));
        }
        this.auxiliaryModes.add(new ModeData(
                ModeTypeEnum.getBeefInvulnerabilityMode(beefInvulnerabilityEnabled),
                ModeTypeEnum.getBeefInvulnerabilityMode(beefInvulnerabilityEnabled).getTooltip(),
                beefInvulnerabilityEnabled));
        if (this.targetItem.getItem() instanceof EndlessBeafItem) {
            this.auxiliaryModes.add(new ModeData(
                    ModeTypeEnum.getBeefTeleportMode(beefTeleportEnabled),
                    ModeTypeEnum.getBeefTeleportMode(beefTeleportEnabled).getTooltip(),
                    beefTeleportEnabled));
            this.auxiliaryModes.add(new ModeData(
                    ModeTypeEnum.getBeefMagnetMode(beefMagnetEnabled),
                    ModeTypeEnum.getBeefMagnetMode(beefMagnetEnabled).getTooltip(),
                    beefMagnetEnabled));
        }
    }

    @Override
    protected void init() {
        super.init();
        this.calculateLayout();
        this.sectionLayouts = this.createSectionLayouts();
        this.modeButtons.clear();

        for (SectionLayout section : this.sectionLayouts) {
            int buttonWidth = Math.max(1,
                    (section.width() - 12 - this.buttonGap) / 2);
            List<ModeData> modes = section.modes();
            for (int i = 0; i < modes.size(); i++) {
                ModeData mode = modes.get(i);
                int column = i % 2;
                int row = i / 2;
                int x = section.left() + 6 + column * (buttonWidth + this.buttonGap);
                int y = section.top() + SECTION_TITLE_HEIGHT + row * (this.buttonHeight + this.buttonGap);
                PressableAE2Button button = this.addRenderableWidget(new PressableAE2Button(
                        x, y, buttonWidth, this.buttonHeight, this.buttonMessage(mode),
                        ignored -> this.onModeSelected(mode.mode())));
                this.modeButtons.add(button);
            }
        }
        this.modeLayoutSignature = this.modeLayoutSignature();
    }

    private void calculateLayout() {
        this.panelWidth = Math.max(1, Math.min(PANEL_MAX_WIDTH, this.width - PANEL_MARGIN * 2));
        this.buttonHeight = DEFAULT_BUTTON_HEIGHT;
        this.buttonGap = this.height < 260 ? 1 : 2;
        this.sectionGap = this.height < 260 ? 4 : 6;

        int availableHeight = Math.max(1, this.height - PANEL_MARGIN * 2);
        int desiredHeight = this.calculatePanelHeight();
        while (desiredHeight > availableHeight && this.buttonHeight > 10) {
            this.buttonHeight--;
            desiredHeight = this.calculatePanelHeight();
        }
        this.panelHeight = desiredHeight;
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = Math.max(PANEL_MARGIN, (this.height - this.panelHeight) / 2);
    }

    private int calculatePanelHeight() {
        int topRowHeight = Math.max(
                this.sectionHeight(this.toolModes),
                this.sectionHeight(this.miningModes));
        int bottomRowHeight = Math.max(
                this.sectionHeight(this.combatModes),
                this.sectionHeight(this.auxiliaryModes));
        return SECTION_CONTENT_TOP + topRowHeight + this.sectionGap
                + bottomRowHeight + PANEL_BOTTOM_PADDING;
    }

    private int sectionHeight(List<ModeData> modes) {
        int rows = Math.max(1, (modes.size() + 1) / 2);
        return SECTION_TITLE_HEIGHT + rows * this.buttonHeight
                + Math.max(0, rows - 1) * this.buttonGap + SECTION_BOTTOM_PADDING;
    }

    private List<SectionLayout> createSectionLayouts() {
        int sectionWidth = Math.max(1,
                (this.panelWidth - PANEL_MARGIN * 2 - SECTION_COLUMN_GAP) / 2);
        int leftColumn = this.panelLeft + PANEL_MARGIN;
        int rightColumn = leftColumn + sectionWidth + SECTION_COLUMN_GAP;
        int top = this.panelTop + SECTION_CONTENT_TOP;
        int topHeight = Math.max(this.sectionHeight(this.toolModes), this.sectionHeight(this.miningModes));
        int bottom = top + topHeight + this.sectionGap;

        return List.of(
                new SectionLayout(Component.translatable("gui.useless_mod.mode_config.tools"),
                        this.toolModes, leftColumn, top, sectionWidth, this.sectionHeight(this.toolModes)),
                new SectionLayout(Component.translatable("gui.useless_mod.mode_config.mining"),
                        this.miningModes, rightColumn, top, sectionWidth, this.sectionHeight(this.miningModes)),
                new SectionLayout(Component.translatable("gui.useless_mod.mode_config.combat"),
                        this.combatModes, leftColumn, bottom, sectionWidth, this.sectionHeight(this.combatModes)),
                new SectionLayout(Component.translatable("gui.useless_mod.mode_config.auxiliary"),
                        this.auxiliaryModes, rightColumn, bottom, sectionWidth,
                        this.sectionHeight(this.auxiliaryModes))
        );
    }

    private Component buttonMessage(ModeData mode) {
        if (this.isExclusiveMode(mode.mode())) {
            return mode.active()
                    ? Component.translatable("gui.useless_mod.mode_config.current", mode.name())
                    : mode.name();
        }
        return Component.translatable(
                "gui.useless_mod.mode_config.state",
                mode.name(),
                Component.translatable(mode.active()
                        ? "tooltip.useless_mod.enable"
                        : "tooltip.useless_mod.disable"));
    }

    private boolean isExclusiveMode(Object mode) {
        return mode instanceof EnchantMode
                || mode instanceof ToolTypeMode
                || mode == ModeTypeEnum.CONSTRUCTION_WAND_ANGEL_CORE
                || mode == ModeTypeEnum.CONSTRUCTION_WAND_DESTRUCTION_CORE;
    }

    @Override
    public void tick() {
        super.tick();
        this.refreshTargetAndModes();
    }

    private void refreshTargetAndModes() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }

        var target = UselessItemUtils.findTargetToolInHands(this.minecraft.player);
        if (target.isEmpty()) {
            this.onClose();
            return;
        }

        this.targetItem = target.get().getKey();
        this.loadModesFromEnums();
        String newSignature = this.modeLayoutSignature();
        if (!newSignature.equals(this.modeLayoutSignature)
                || this.modeButtons.size() != this.modeCount()) {
            this.rebuildWidgets();
        } else {
            this.updateModeButtons();
        }
    }

    private void updateModeButtons() {
        int buttonIndex = 0;
        for (SectionLayout section : this.createSectionLayouts()) {
            for (ModeData mode : section.modes()) {
                PressableAE2Button button = this.modeButtons.get(buttonIndex++);
                button.setMessage(this.buttonMessage(mode));
            }
        }
    }

    private int modeCount() {
        return this.toolModes.size() + this.miningModes.size()
                + this.combatModes.size() + this.auxiliaryModes.size();
    }

    private String modeLayoutSignature() {
        StringBuilder signature = new StringBuilder();
        for (List<ModeData> section : List.of(
                this.toolModes, this.miningModes, this.combatModes, this.auxiliaryModes)) {
            signature.append('|');
            for (ModeData mode : section) {
                signature.append(mode.name().getString()).append(';');
            }
        }
        return signature.toString();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MachineScreenStyle.drawPanel(graphics, this.panelLeft, this.panelTop,
                this.panelWidth, this.panelHeight);
        graphics.drawString(this.font, this.title,
                this.panelLeft + PANEL_MARGIN, this.panelTop + 7,
                MachineScreenStyle.TEXT_COLOR, false);

        for (SectionLayout section : this.sectionLayouts) {
            MachineScreenStyle.drawInset(graphics, section.left(), section.top(),
                    section.left() + section.width(), section.top() + section.height());
            graphics.drawString(this.font, section.title(),
                    section.left() + 6, section.top() + 4,
                    MachineScreenStyle.TEXT_COLOR, false);
        }

        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void onModeSelected(Object mode) {
        if (mode instanceof EnchantMode enchantMode) {
            PacketDistributor.sendToServer(new EnchantmentSwitchPacket(enchantMode));
        } else if (mode instanceof ToolTypeMode toolTypeMode) {
            PacketDistributor.sendToServer(new ToolTypeModeSwitchPacket(toolTypeMode));
        } else if (mode instanceof ModeTypeEnum modeType) {
            switch (modeType) {
                case ENHANCED_CHAIN_MINING_ENABLED, ENHANCED_CHAIN_MINING_DISABLED -> {
                    boolean currentEnabled = this.targetItem.getOrDefault(
                            UComponents.EnhancedChainMiningComponent, false);
                    PacketDistributor.sendToServer(new ModeTogglePacket(
                            ModeTogglePacket.ModeType.CHAIN_MINING, !currentEnabled));
                }
                case FORCE_MINING_ENABLED, FORCE_MINING_DISABLED -> {
                    boolean currentEnabled = this.targetItem.getOrDefault(
                            UComponents.ForceMiningComponent, false);
                    PacketDistributor.sendToServer(new ModeTogglePacket(
                            ModeTogglePacket.ModeType.FORCE_MINING, !currentEnabled));
                }
                case AE_STORAGE_PRIORITY_ENABLED, AE_STORAGE_PRIORITY_DISABLED -> {
                    boolean currentEnabled = this.targetItem.getOrDefault(
                            UComponents.AEStoragePriorityComponent, false);
                    PacketDistributor.sendToServer(new ModeTogglePacket(
                            ModeTogglePacket.ModeType.AE_STORAGE_PRIORITY, !currentEnabled));
                }
                case WRENCH_TAG_ENABLED, WRENCH_TAG_DISABLED -> {
                    boolean currentEnabled = this.targetItem.getOrDefault(
                            UComponents.WrenchTagEnabledComponent, true);
                    PacketDistributor.sendToServer(new ModeTogglePacket(
                            ModeTogglePacket.ModeType.WRENCH_TAG, !currentEnabled));
                }
                case CONSTRUCTION_WAND_ENABLED, CONSTRUCTION_WAND_DISABLED -> {
                    boolean currentEnabled = this.targetItem.getOrDefault(
                            UComponents.ConstructionWandEnabledComponent, false);
                    PacketDistributor.sendToServer(new ModeTogglePacket(
                            ModeTogglePacket.ModeType.CONSTRUCTION_WAND, !currentEnabled));
                }
                case CONSTRUCTION_WAND_ANGEL_CORE -> {
                    ConstructionWandCoreMode current = this.targetItem.getOrDefault(
                            UComponents.ConstructionWandCoreComponent, ConstructionWandCoreMode.DEFAULT);
                    PacketDistributor.sendToServer(new ConstructionWandCorePacket(
                            current == ConstructionWandCoreMode.ANGEL
                                    ? ConstructionWandCoreMode.DEFAULT
                                    : ConstructionWandCoreMode.ANGEL));
                }
                case CONSTRUCTION_WAND_DESTRUCTION_CORE -> {
                    ConstructionWandCoreMode current = this.targetItem.getOrDefault(
                            UComponents.ConstructionWandCoreComponent, ConstructionWandCoreMode.DEFAULT);
                    PacketDistributor.sendToServer(new ConstructionWandCorePacket(
                            current == ConstructionWandCoreMode.DESTRUCTION
                                    ? ConstructionWandCoreMode.DEFAULT
                                    : ConstructionWandCoreMode.DESTRUCTION));
                }
                case FORCE_KILL -> {
                    boolean currentEnabled = this.targetItem.getOrDefault(
                            UComponents.ForceKillEnabledComponent, false);
                    PacketDistributor.sendToServer(new ModeTogglePacket(
                            ModeTogglePacket.ModeType.FORCE_KILL, !currentEnabled));
                }
                case BEEF_TIME_ACCELERATION_ENABLED, BEEF_TIME_ACCELERATION_DISABLED -> {
                    boolean currentEnabled = this.targetItem.getOrDefault(
                            UComponents.BeefTimeAccelerationEnabledComponent, false);
                    PacketDistributor.sendToServer(new ModeTogglePacket(
                            ModeTogglePacket.ModeType.BEEF_TIME_ACCELERATION, !currentEnabled));
                }
                case BEEF_INVULNERABILITY_ENABLED, BEEF_INVULNERABILITY_DISABLED -> {
                    boolean currentEnabled = this.targetItem.getOrDefault(
                            UComponents.BeefInvulnerabilityEnabledComponent, false);
                    PacketDistributor.sendToServer(new ModeTogglePacket(
                            ModeTogglePacket.ModeType.BEEF_INVULNERABILITY, !currentEnabled));
                }
                case BEEF_CAPTURE_ENABLED, BEEF_CAPTURE_DISABLED -> {
                    boolean currentEnabled = this.targetItem.getOrDefault(
                            UComponents.BeefCaptureEnabledComponent, false);
                    PacketDistributor.sendToServer(new ModeTogglePacket(
                            ModeTogglePacket.ModeType.BEEF_CAPTURE, !currentEnabled));
                }
                case BEEF_TELEPORT_ENABLED, BEEF_TELEPORT_DISABLED -> {
                    boolean currentEnabled = this.targetItem.getOrDefault(
                            UComponents.BeefTeleportEnabledComponent, false);
                    PacketDistributor.sendToServer(new ModeTogglePacket(
                            ModeTogglePacket.ModeType.BEEF_TELEPORT, !currentEnabled));
                }
                case BEEF_AOE_DAMAGE_ENABLED, BEEF_AOE_DAMAGE_DISABLED -> {
                    boolean currentEnabled = this.targetItem.getOrDefault(
                            UComponents.BeefAoeDamageEnabledComponent, false);
                    PacketDistributor.sendToServer(new ModeTogglePacket(
                            ModeTogglePacket.ModeType.BEEF_AOE_DAMAGE, !currentEnabled));
                }
                case BEEF_MAGNET_ENABLED, BEEF_MAGNET_DISABLED -> {
                    boolean currentEnabled = this.targetItem.getOrDefault(
                            UComponents.BeefMagnetEnabledComponent, false);
                    PacketDistributor.sendToServer(new ModeTogglePacket(
                            ModeTogglePacket.ModeType.BEEF_MAGNET, !currentEnabled));
                }
            }
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (PressableAE2Button modeButton : this.modeButtons) {
            modeButton.releaseVisualState();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private record ModeData(Object mode, Component name, boolean active) {
    }

    private record SectionLayout(Component title, List<ModeData> modes,
                                 int left, int top, int width, int height) {
    }
}
