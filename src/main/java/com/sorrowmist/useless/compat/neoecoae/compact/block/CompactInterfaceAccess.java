package com.sorrowmist.useless.compat.neoecoae.compact.block;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;

/**
 * 紧凑方块的「潜行右键开通讯接口界面」入口。
 *
 * <p>三个紧凑方块共用同一个布尔属性 {@link #INTERFACE}。它只活在菜单 holder 的方块状态字段里：
 * 服务端打开菜单时用 {@code state.setValue(INTERFACE, true)} 造一份<b>不下世界、不写方块</b>的
 * 伪装状态，两端 {@code createUI} 都以 holder 里这一份状态作为唯一分支依据，
 * 于是「开主机面板」与「开通讯接口界面」在双端必然同构，不存在各自读潜行状态的竞态。</p>
 *
 * <p>伪装的属性值不会被任何世界逻辑读取：LDLib2 的 {@code stillValid} 只比方块身份，
 * ECO 的距离校验只读世界方块状态，画模型走的是世界状态。</p>
 */
public final class CompactInterfaceAccess {

    /** 伪装状态位；multipart 方块状态 JSON 不引用它，所以不需要改模型。 */
    public static final BooleanProperty INTERFACE = BooleanProperty.create("useless_interface");

    private CompactInterfaceAccess() {}

    /** 菜单 holder 里的状态位：true 表示这次要开通讯接口界面而不是主机面板。 */
    public static boolean requested(BlockState state) {
        return state.hasProperty(INTERFACE) && state.getValue(INTERFACE);
    }

    public static boolean isPlayerCloseEnough(Block block, Level level, BlockPos pos, Player player) {
        return player.level() == level
                && level.getBlockState(pos).getBlock() == block
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    /** 服务端调用：用伪装状态自建 holder 并打开菜单。 */
    public static void open(BlockUIMenuType.BlockUI blockUI, BlockState state, BlockPos pos, ServerPlayer player) {
        open(blockUI, state, pos, player, true);
    }

    /** 服务端调用：显式打开普通主机面板，避免旧世界残留的伪装状态污染菜单分支。 */
    public static void openMain(BlockUIMenuType.BlockUI blockUI, BlockState state, BlockPos pos, ServerPlayer player) {
        open(blockUI, state, pos, player, false);
    }

    private static void open(BlockUIMenuType.BlockUI blockUI, BlockState state, BlockPos pos,
                             ServerPlayer player, boolean interfaceMode) {
        player.openMenu(new BlockUIMenuType.BlockUIHolder(blockUI, player, pos,
                state.setValue(INTERFACE, interfaceMode)));
    }
}
