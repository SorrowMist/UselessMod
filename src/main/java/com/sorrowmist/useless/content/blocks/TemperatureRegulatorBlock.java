package com.sorrowmist.useless.content.blocks;

import com.sorrowmist.useless.content.blockentities.ThermostatBlockEntity;
import com.sorrowmist.useless.content.blockentities.ThermostatHeatHook;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 「温度调节器」方块：可配置的<b>无限恒温源</b>。
 *
 * <p>功能从原塑料方块上剥离而来：用权杖 <b>Alt + 右键</b> 打开配置界面设定温度 / 开关。
 * 温度单位是开尔文，默认 300 K。</p>
 *
 * <h2>外观与为什么颜色要写进 BlockState</h2>
 *
 * <p>模型由两层构成：外框（不染色）+ 内板（{@code tintindex: 0}，按温度染色）。染色公式移植自
 * 气动工艺压缩铁块（见 {@code TemperatureColors}）。</p>
 *
 * <p><b>⛔ 关键约束：染色回调里拿不到 BlockEntity。</b> 区块网格是在 {@code RenderChunkRegion}
 * 上烘焙的，它的 {@code getBlockEntity} 恒为 {@code null}；因此在渲染期去查 BE 只会永远回落
 * 到默认温度（表现为「不管设多少度都是同一个颜色」）。渲染期唯一可靠的信息就是 {@link BlockState}
 * 本身，所以这里把温度<b>量化成档位</b>存进状态，由 {@link ThermostatBlockEntity} 在温度 / 开关
 * 变化时同步进来；染色回调只读状态。</p>
 *
 * <p>档位数量取 256：一是让 273~323 K 的「室温白」区间足够宽、默认 300 K 不会因为量化误差
 * 掉进青蓝色区；二是 256 × 2（开关）个状态与原版红石线（1296 个）同量级，完全可以接受。</p>
 *
 * <h2>传热</h2>
 *
 * <p>气动工艺是接收方驱动（机器自己会向我们收敛），只需暴露热能力；Mekanism 是发送方驱动，
 * 必须由本方块每 tick 主动推热——见 {@link ThermostatHeatHook}。未开启（{@code enabled == false}）
 * 时 tick 首行即返回，几乎零开销。</p>
 */
public class TemperatureRegulatorBlock extends Block implements EntityBlock {

    /** 温度档位总数（含 0）。越大颜色越平滑，代价是更多的方块状态组合。 */
    public static final int TEMPERATURE_LEVELS = 256;

    /**
     * 档位映射的饱和温度（K）。
     *
     * <p>超过这个温度后档位一律取满。取 10 000 K 是为了让 0~2273 K 这段真正会被气动 / Mek
     * 用到的区间分到足够多的档位（约 58 档），高温端留出余量只是为了让「极端数值」有个确定的
     * 归宿，<b>不是</b>为了让颜色继续变化——气动取色公式在 2273 K 就已经走到黄色，再高不会再变。</p>
     */
    public static final long SATURATION_KELVIN = 10_000L;

    /** 当前温度所处的档位。染色回调读它，再换算回温度取色。 */
    public static final IntegerProperty TEMPERATURE_LEVEL =
            IntegerProperty.create("temperature_level", 0, TEMPERATURE_LEVELS - 1);

    /** 恒温能力是否开启。关闭时内板不着色（保持贴图原色）。 */
    public static final BooleanProperty ENABLED = BooleanProperty.create("enabled");

    /** 开始自发光的温度（K）。低于此温度不发光。 */
    public static final long LIGHT_START_KELVIN = 500L;

    /** 自发光达到满亮度（15）的温度（K）。 */
    public static final long LIGHT_FULL_KELVIN = 5000L;

    public TemperatureRegulatorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(TEMPERATURE_LEVEL, levelForTemperature(ThermostatBlockEntity.DEFAULT_TEMPERATURE))
                .setValue(ENABLED, false));
    }

    /** 温度（K）→ 档位。超出 {@link #SATURATION_KELVIN} 时取满。 */
    public static int levelForTemperature(long kelvin) {
        long clamped = Math.max(0L, Math.min(SATURATION_KELVIN, kelvin));
        return (int) (clamped * (TEMPERATURE_LEVELS - 1) / SATURATION_KELVIN);
    }

    /** 档位 → 代表温度（K），用于取色。与 {@link #levelForTemperature} 互为近似逆运算。 */
    public static long temperatureForLevel(int level) {
        return (long) level * SATURATION_KELVIN / (TEMPERATURE_LEVELS - 1);
    }

    /** 由温度 / 开关推出对应的方块状态（保留 base 上的其它属性）。 */
    public static BlockState stateFor(BlockState base, long kelvin, boolean enabled) {
        return base.setValue(TEMPERATURE_LEVEL, levelForTemperature(kelvin))
                .setValue(ENABLED, enabled);
    }

    /**
     * 按温度取自发光亮度（0~15），供 {@code BlockBehaviour.Properties#lightLevel} 使用。
     *
     * <p>没有自发光的高温方块会被世界环境光压暗，看上去「不热」——这是光靠提亮贴图解决不了的。
     * 这里让温度越高越亮：{@link #LIGHT_START_KELVIN} 以下不发光（冷 / 室温），到
     * {@link #LIGHT_FULL_KELVIN} 达到满亮度 15；关闭时恒为 0。</p>
     */
    public static int lightForState(BlockState state) {
        if (!state.getValue(ENABLED)) {
            return 0;
        }
        long kelvin = temperatureForLevel(state.getValue(TEMPERATURE_LEVEL));
        if (kelvin <= LIGHT_START_KELVIN) {
            return 0;
        }
        float ramp = Math.min(1f, (kelvin - LIGHT_START_KELVIN)
                / (float) (LIGHT_FULL_KELVIN - LIGHT_START_KELVIN));
        return Math.round(ramp * 15f);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TEMPERATURE_LEVEL, ENABLED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new ThermostatBlockEntity(pos, state);
    }

    /**
     * 恒温源的「主动驱动」tick（仅服务端）。
     *
     * <p>Mekanism 的传热是<b>发送方驱动</b>（见 {@link ThermostatHeatHook}），所以必须由方块自己
     * 每 tick 把热推给邻居。未配置（{@code enabled == false}）时直接返回。</p>
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level,
                                                                  @NotNull BlockState state,
                                                                  @NotNull BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (!(blockEntity instanceof ThermostatBlockEntity thermostat) || !thermostat.isEnabled()) {
                return;
            }
            ThermostatHeatHook.HeatDriver driver = ThermostatHeatHook.driver;
            if (driver != null && tickLevel instanceof ServerLevel serverLevel) {
                driver.tick(serverLevel, pos, thermostat);
            }
        };
    }
}