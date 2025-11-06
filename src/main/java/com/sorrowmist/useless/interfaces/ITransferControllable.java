package com.sorrowmist.useless.interfaces;

import net.minecraft.core.Direction;

// 在您的包中添加这个接口
public interface ITransferControllable {
    boolean hasTransferIn();
    boolean hasTransferOut();
    boolean getTransferIn();
    boolean getTransferOut();
    void setControl(boolean input, boolean output);

    // 按面控制的方法
    boolean getTransferOut(Direction direction);
    void setTransferOut(Direction direction, boolean enabled);
}
