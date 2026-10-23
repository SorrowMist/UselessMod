package com.sorrowmist.useless.tests;

import com.sorrowmist.useless.UselessMod;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.helpers.IGuiHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.event.TickEvent;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

/** Runs the real third-party constructor under the client Mixin transformer, then exits. */
@Mod.EventBusSubscriber(modid = UselessMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ExMachinisClientProbe {
    private static volatile boolean finished;
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        if (!Boolean.getBoolean("useless.compatClientProbe")) return;
        event.enqueueWork(() -> {
            try {
                var background = new AtomicReference<ResourceLocation>();
                var drawable = Proxy.newProxyInstance(IDrawableStatic.class.getClassLoader(),
                        new Class<?>[]{IDrawableStatic.class}, (proxy, method, args) -> switch (method.getName()) {
                            case "getWidth" -> 82;
                            case "getHeight" -> 34;
                            default -> null;
                        });
                var helper = Proxy.newProxyInstance(IGuiHelper.class.getClassLoader(),
                        new Class<?>[]{IGuiHelper.class}, (proxy, method, args) -> {
                            if (method.getName().equals("createDrawable")) background.set((ResourceLocation) args[0]);
                            if (method.getName().startsWith("createDrawable")) return drawable;
                            throw new AssertionError("Unexpected JEI call: " + method.getName());
                        });
                var category = Class.forName("net.permutated.exmachinis.compat.jei.category.CompactingCategory")
                        .getConstructor(IGuiHelper.class).newInstance(helper);
                var expected = new ResourceLocation("useless_mod", "textures/gui/exmachinis_recipe.png");
                if (!expected.equals(background.get())) throw new AssertionError("Mixin did not redirect the removed field");
                var rendered = (IDrawable) category.getClass().getMethod("getBackground").invoke(category);
                if (rendered.getWidth() != 82 || rendered.getHeight() != 34) throw new AssertionError("Incorrect background size");
                try (var texture = ExMachinisClientProbe.class.getResourceAsStream(
                        "/assets/useless_mod/textures/gui/exmachinis_recipe.png")) {
                    if (texture == null || texture.readAllBytes().length < 100) throw new AssertionError("Missing background texture");
                }
                System.out.println("COMPAT_CLIENT_PROBE_OK ExMachinis constructor + JEI 15.74 + redirected texture");
                finished = true;
            } catch (ReflectiveOperationException | java.io.IOException failure) {
                throw new IllegalStateException("Ex Machinis compatibility probe failed", failure);
            }
        });
    }

    @Mod.EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
    public static final class ExitAfterStartup {
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (finished && event.phase == TickEvent.Phase.END) Minecraft.getInstance().stop();
        }
    }
}
