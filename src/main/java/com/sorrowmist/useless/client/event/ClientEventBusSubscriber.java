package com.sorrowmist.useless.client.event;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.tool.EnchantMode;
import com.sorrowmist.useless.api.tool.FunctionMode;
import com.sorrowmist.useless.common.KeyBindings;
import com.sorrowmist.useless.items.EndlessBeafItem;
import com.sorrowmist.useless.network.EnchantmentSwitchPacket;
import com.sorrowmist.useless.network.FunctionModeTogglePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
public class ClientEventBusSubscriber {

    private static final ResourceLocation MY_ULTIMINE_LAYER = ResourceLocation.fromNamespaceAndPath("mymod",
                                                                                                    "ultimine_status"
    );

    @SubscribeEvent
    public static void registerKeyBindings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.SWITCH_SILK_TOUCH_KEY.get());
        event.register(KeyBindings.SWITCH_FORTUNE_KEY.get());
        event.register(KeyBindings.TOGGLE_CHAIN_MODE_KEY.get());
        event.register(KeyBindings.SWITCH_MODE_WHEEL_KEY.get());
        event.register(KeyBindings.SWITCH_FORCE_MINING_KEY.get());
        event.register(KeyBindings.TRIGGER_FORCE_MINING_KEY.get());
    }

    @SubscribeEvent
    public static void onKeyInput(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.player == null || mc.screen != null) return;

        if (KeyBindings.SWITCH_FORTUNE_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                PacketDistributor.sendToServer(
                        new EnchantmentSwitchPacket(EnchantMode.FORTUNE));
            }
        }

        if (KeyBindings.SWITCH_SILK_TOUCH_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                PacketDistributor.sendToServer(
                        new EnchantmentSwitchPacket(EnchantMode.SILK_TOUCH));
            }
        }

        if (KeyBindings.TOGGLE_CHAIN_MODE_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {

                PacketDistributor.sendToServer(
                        new FunctionModeTogglePacket(FunctionMode.CHAIN_MINING));
            }
        }

        if (KeyBindings.SWITCH_FORCE_MINING_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                PacketDistributor.sendToServer(new FunctionModeTogglePacket(FunctionMode.FORCE_MINING));
            }
        }
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.HOTBAR,
                MY_ULTIMINE_LAYER,
                (guiGraphics, partialTick) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player == null || mc.screen != null) return; // 不渲染在 GUI 屏幕上

                    // 检测 Tab 键是否按下（Tab 默认键码是 GLFW.GLFW_KEY_TAB）
                    boolean isTabPressed = KeyBindings.SWITCH_FORCE_MINING_KEY.get().isDown();

                    if (!mc.options.keyPlayerList.isDown()) return; // 只在按下 Tab 时渲染

                    // 你的状态逻辑（示例：这里简单用一个静态变量，实际可以更复杂，如疲劳/冷却）
                    String status = getCurrentStatus(); // "激活" / "冷却中" / "未激活"
                    int color = switch (status) {
                        case "激活" -> 0xFF00FF00; // 绿色
                        case "冷却中" -> 0xFFFFFF00; // 黄色
                        default -> 0xFFFF0000; // 红色
                    };

                    // 计算文本
                    String text = "Ultimine: " + status;
                    int textWidth = mc.font.width(text);
                    int padding = 4;
                    int boxWidth = textWidth + padding * 2;
                    int boxHeight = mc.font.lineHeight + padding * 2;

                    int x = 10; // 左上角偏移
                    int y = 10;

                    // 绘制深灰色半透明背景 (ARGB: 深灰 0x404040, alpha 0x80 ~ 50% 透明)
                    guiGraphics.fill(x, y, x + boxWidth, y + boxHeight, 0x80404040);

                    // 绘制边框（白色或浅灰，1像素宽）
                    guiGraphics.renderOutline(x, y, boxWidth, boxHeight, 0xFFFFFFFF); // 白边框

                    // 绘制文本（带阴影）
                    guiGraphics.drawString(mc.font, text, x + padding, y + padding, color, true);

                    // 可选：如果有模式或其他信息，多行绘制
                }
        );
    }

    // 示例状态获取（实际你可以加冷却计时器、疲劳系统等）
    private static String getCurrentStatus() {
        // 这里替换成你的逻辑，例如检查工具、疲劳值、冷却剩余等
        return "激活"; // 或 "冷却中" / "未激活"
    }
}