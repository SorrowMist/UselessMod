package com.sorrowmist.useless.mixin.jei;

import com.sorrowmist.useless.compat.jei.JEIPlugin;
import mezz.jei.api.IModPlugin;
import mezz.jei.forge.startup.ForgePluginFinder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ForgePluginFinder.class)
public class ForgePluginFinderMixin {

    @Inject(method = "getModPlugins", at = @At("RETURN"), remap = false)
    private static void injectGetModPlugins(CallbackInfoReturnable<List<IModPlugin>> cir) {
        // 获取GTOCore返回的插件列表
        List<IModPlugin> plugins = cir.getReturnValue();
        // 添加我们的JEI插件
        plugins.add(new JEIPlugin());
    }
}