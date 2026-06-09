package com.sorrowmist.useless.mixin.mek;

import mekanism.common.base.holiday.Holiday;
import mekanism.common.base.holiday.HolidayManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.stream.Collectors;

@Mixin(value = HolidayManager.class, remap = false)
public class FuckingPrideHolidayMixin {
    @Mutable
    @Final
    @Shadow
    private static Set<Holiday> holidays;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void removePrideHoliday(CallbackInfo ci) {
        holidays = holidays.stream()
                .filter(holiday -> !"mekanism.common.base.holiday.Pride".equals(holiday.getClass().getName()))
                .collect(Collectors.toUnmodifiableSet());
    }
}
