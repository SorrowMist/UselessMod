package com.sorrowmist.useless.content.blocks.multiblock;

import net.minecraft.util.StringRepresentable;

public enum MultiblockFurnaceActivity implements StringRepresentable {
    IDLE("idle"),
    RUN("run"),
    WAIT("wait");

    private final String serializedName;

    MultiblockFurnaceActivity(String serializedName) {
        this.serializedName = serializedName;
    }

    public static MultiblockFurnaceActivity resolve(
            boolean formed, boolean progressed, boolean hasWork) {
        if (!formed) {
            return IDLE;
        }
        if (progressed) {
            return RUN;
        }
        return hasWork ? WAIT : IDLE;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
