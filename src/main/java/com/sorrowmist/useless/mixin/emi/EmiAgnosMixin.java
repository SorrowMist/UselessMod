package com.sorrowmist.useless.mixin.emi;

import com.sorrowmist.useless.compat.emi.EMIPlugin;
import dev.emi.emi.platform.EmiAgnos;
import dev.emi.emi.registry.EmiPluginContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(EmiAgnos.class)
public class EmiAgnosMixin {

    @Inject(method = "getPlugins", at = @At("RETURN"), remap = false)
    private static void injectGetPlugins(CallbackInfoReturnable<List<EmiPluginContainer>> cir) {
        // 获取EMI返回的插件列表
        List<EmiPluginContainer> plugins = cir.getReturnValue();
        // 添加我们的EMI插件
        plugins.add(new EmiPluginContainer(new EMIPlugin(), "useless_mod"));
    }
}