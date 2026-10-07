package com.sorrowmist.useless.content.blockentities;

import com.sorrowmist.useless.content.blocks.TemperatureRegulatorBlock;
import com.sorrowmist.useless.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 「温度调节器」方块的「恒温源」状态。
 *
 * <p>温度调节器是一个独立的方块；本 BE 给它一个可配置的温度，语义是<b>无限恒温源</b>：
 * 温度恒定，可被无限取热 / 放热（不守恒）。</p>
 *
 * <p><b>本类刻意不引用任何 Mekanism / PneumaticCraft 类型。</b> 那两个模组的热适配器由
 * {@code compat} 层反射加载后缓存进 {@link #heatAdapters}（不透明的 {@code Object}），
 * 主动驱动则通过 {@link ThermostatHeatHook} 安装，因此没装对应模组时本类照常加载。</p>
 *
 * <h2>单位与范围</h2>
 *
 * <p>温度单位是<b>开尔文</b>，与 Mekanism（{@code HeatAPI.AMBIENT_TEMP = 300}）和气动工艺
 * （{@code HeatExchangerLogicTicking} 把机器自身温度夹到 0~2273）一致。</p>
 *
 * <p>存 {@code long}，范围 {@code [0, Long.MAX_VALUE]}。实践上没必要填太大：气动会把机器温度
 * 夹在 2273 K，超过就只是饱和；而 {@code double} 在 2^53（约 9e15）以上会丢整数精度，
 * 所以超过这个量级的值只是「更大」，不再精确。</p>
 */
public final class ThermostatBlockEntity extends BlockEntity {

    /** 默认温度：300 K，即 Mek 定义的「水的温度」/ 气动的环境温度。 */
    public static final long DEFAULT_TEMPERATURE = 300L;

    /** 允许的最低温度。 */
    public static final long MIN_TEMPERATURE = 0L;

    private static final String TAG_TEMPERATURE = "Temperature";
    private static final String TAG_ENABLED = "Enabled";

    private long temperature = DEFAULT_TEMPERATURE;
    private boolean enabled;

    /**
     * 外部热适配器缓存，按「模组 id」区分（主代码不关心里面是什么）。
     *
     * <p>惰性分配：绝大多数塑料方块从未被配置，也就不该为它们各建一个 map。缓存是必要的——
     * Mek 的 {@code CapabilityCache} 与气动的 {@code IdentityHashMap} 都按<b>实例身份</b>认连接，
     * 每次能力查询都 new 一个适配器会让它们认不出是同一条热通路。</p>
     */
    @Nullable
    private Map<String, Object> heatAdapters;

    public ThermostatBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TEMPERATURE_REGULATOR.get(), pos, state);
    }

    public long getTemperature() {
        return temperature;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 取某个模组之前缓存的热适配器；没缓存过返回 {@code null}。 */
    @Nullable
    public Object getHeatAdapter(String key) {
        Map<String, Object> adapters = heatAdapters;
        return adapters == null ? null : adapters.get(key);
    }

    /** 缓存某个模组的热适配器。 */
    public void putHeatAdapter(String key, Object adapter) {
        if (heatAdapters == null) {
            heatAdapters = new HashMap<>(2);
        }
        heatAdapters.put(key, adapter);
    }

    /** 开启 / 关闭恒温能力。 */
    public void setEnabled(boolean value) {
        if (this.enabled == value) {
            return;
        }
        this.enabled = value;
        markUpdated();
        if (level != null && !level.isClientSide) {
            // 提醒邻居重做一次热连接发现：气动的机器只在 onLoad / neighborChanged 时重扫，
            // 不推这一下，它可能一直抱着「这里没有热交换器」的旧结论。
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    /** 设定温度（开尔文）；夹到 {@code [0, Long.MAX_VALUE]}。 */
    public void setTemperature(long kelvin) {
        long clamped = Math.max(MIN_TEMPERATURE, kelvin);
        if (this.temperature == clamped) {
            return;
        }
        this.temperature = clamped;
        markUpdated();
    }

    private void markUpdated() {
        setChanged();
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState oldState = getBlockState();
        BlockState newState = TemperatureRegulatorBlock.stateFor(oldState, temperature, enabled);
        if (newState != oldState) {
            // ⛔ 温度档位 / 开关必须真的落到 BlockState 上：染色回调跑在区块网格
            // （RenderChunkRegion）里，那里 getBlockEntity 恒为 null，查 BE 只会永远回落到
            // 默认温度（症状：设多少度都是同一个颜色）。这一步顺带把 BlockState 与 BE 数据
            // （Jade 要读）一起同步给客户端。
            level.setBlock(worldPosition, newState, Block.UPDATE_ALL);
        } else {
            level.sendBlockUpdated(worldPosition, oldState, oldState, Block.UPDATE_ALL);
        }
        // 能力本身始终暴露，但数值 / 开关变化仍通知一次，让缓存里的视图重新取值。
        level.invalidateCapabilities(worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong(TAG_TEMPERATURE, temperature);
        tag.putBoolean(TAG_ENABLED, enabled);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // getLong 接受 TAG_ANY_NUMERIC，所以旧存档里的 INT 温度能原样读回来。
        if (tag.contains(TAG_TEMPERATURE)) {
            temperature = Math.max(MIN_TEMPERATURE, tag.getLong(TAG_TEMPERATURE));
        }
        if (tag.contains(TAG_ENABLED)) {
            enabled = tag.getBoolean(TAG_ENABLED);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** 摄氏温度，仅用于界面显示。 */
    public static double toCelsius(long kelvin) {
        return kelvin - 273.15D;
    }

    /** 把摄氏温度换算回开尔文（界面输入用）。 */
    public static long fromCelsius(double celsius) {
        return Math.max(MIN_TEMPERATURE, Math.round(celsius + 273.15D));
    }
}
