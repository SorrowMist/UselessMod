package com.sorrowmist.useless.mixin.ae2;

import appeng.api.networking.IGrid;
import appeng.me.service.CraftingService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 取 {@link CraftingService} 所在网格。
 *
 * <p>智能倍增要按关卡判定「可复用输入」（带耐久返还的催化剂只有拿到 {@code Level} 才能认出来），
 * 而 {@code CraftingService} 没有公开网格入口 —— 它的 {@code grid} 是私有 final 字段。</p>
 */
@Mixin(value = CraftingService.class, remap = false)
public interface CraftingServiceGridAccessor {

    @Accessor("grid")
    IGrid uselessMod$getGrid();
}
