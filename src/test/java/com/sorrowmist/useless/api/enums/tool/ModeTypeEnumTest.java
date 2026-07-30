package com.sorrowmist.useless.api.enums.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModeTypeEnumTest {
    @Test
    void beefCaptureModeReflectsItsEnabledState() {
        assertEquals(ModeTypeEnum.BEEF_CAPTURE_ENABLED, ModeTypeEnum.getBeefCaptureMode(true));
        assertEquals(ModeTypeEnum.BEEF_CAPTURE_DISABLED, ModeTypeEnum.getBeefCaptureMode(false));
    }
}
