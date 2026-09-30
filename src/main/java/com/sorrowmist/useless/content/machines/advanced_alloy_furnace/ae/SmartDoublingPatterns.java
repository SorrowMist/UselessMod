package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;

/** Creates, restores and resolves this mod's smart-doubling pattern wrappers. */
public final class SmartDoublingPatterns {
    private static final Logger LOGGER = LoggerFactory.getLogger("useless_mod/smart_doubling");

    private SmartDoublingPatterns() {
    }

    /**
     * 把样板包装成“一次推送代表多份操作”。
     *
     * <p>合成样板必须保留 {@code IMolecularAssemblerSupportedPattern} 身份（接收方要靠它判断
     * “这是要自己装配的合成样板”），所以它走专门的包装类；其余样板沿用处理样板包装。</p>
     */
    public static ScaledProcessingPattern scale(IPatternDetails pattern, long operationsPerPush) {
        return scale(pattern, operationsPerPush, null);
    }

    /**
     * 带 {@link Level} 的包装：只有拿到关卡才能认出「带耐久受损的返还物」这类可复用输入
     * （注魔水晶），并把它们的倍率钉在 1 份上。见 {@link ReusablePatternInputs}。
     *
     * @param level 可为 {@code null}，此时退回保守判据（只认原样返还）
     */
    public static ScaledProcessingPattern scale(IPatternDetails pattern, long operationsPerPush,
                                                @Nullable Level level) {
        // 两个包装类都会在构造时解析已有倍率，所以这里直接把入参交给对应包装，
        // 嵌套调用时的乘法语义与处理样板保持一致。
        if (pattern instanceof IMolecularAssemblerSupportedPattern crafting) {
            return new ScaledCraftingPattern(crafting, operationsPerPush, level);
        }
        return new ScaledProcessingPattern(pattern, operationsPerPush, level);
    }

    /**
     * 从网格取判定可复用输入所需的关卡。
     *
     * <p>规划入口（{@code CraftingService#submitJob}）跑在服务端线程上，取关卡是安全的；
     * 取不到时返回 {@code null}，包装会退回保守判据 —— 只少修一类，不会错修。</p>
     */
    public static @Nullable Level levelOf(@Nullable IGrid grid) {
        try {
            IGridNode pivot = grid == null ? null : grid.getPivot();
            return pivot == null ? null : pivot.getLevel();
        } catch (RuntimeException exception) {
            LOGGER.debug("Unable to resolve the crafting grid level for reusable-input detection",
                    exception);
            return null;
        }
    }

    /**
     * 样板里「整批只需要 1 份、倍率不能放大」的输入槽位（注魔水晶这类可复用催化剂）。
     *
     * <p>任何按倍率放大输入的地方（本模组的包装、数据能源 counted 派发的原型放大）都必须跳过这些槽位，
     * 否则会凭空造出 {@code count - 1} 份催化剂。判定见 {@link ReusablePatternInputs}。</p>
     */
    public static Set<Integer> reusableInputSlots(@Nullable IPatternDetails pattern,
                                                 @Nullable Level level) {
        return ReusablePatternInputs.reusableRemainders(pattern, level).keySet();
    }

    public static IPatternDetails unwrap(IPatternDetails pattern) {
        return resolve(pattern).pattern();
    }

    /**
     * 判断一个样板能否被放大。
     *
     * <p>这里只做结构健全性检查：样板确实声明了可用的输入。同键返还（模具、可复用催化剂、
     * 带耐久返还的工具）<b>不再</b>被排除 —— 接收方会在铺完一份之后逐键比对“剩下的材料是不是
     * 这一份消耗量的整齐倍数”，只有确认整批每一份输入完全相同才折叠成一次装配 ×N；
     * 一批里混着不同变体时会退回逐份装配，所以同键返还参与批量同样是安全的。</p>
     *
     * <p><b>注意「可复用输入」不在本方法的职责内</b>：注魔水晶这类「返还物仍能当输入用」的槽，
     * AE2 的合成计划对它只算 1 份，倍率不能放大 —— 那件事由 {@link ReusablePatternInputs}
     * 在 {@link #scale} 里按槽位处理，本方法仍只看声明结构，因此<b>不碰 {@code Level}</b>：
     * 规划可能跑在计算线程上。</p>
     */
    public static boolean canScale(IPatternDetails pattern) {
        IPatternDetails base = unwrap(pattern);
        if (!(base instanceof IMolecularAssemblerSupportedPattern)) {
            return true;
        }
        IPatternDetails.IInput[] inputs = base.getInputs();
        if (inputs == null || inputs.length == 0) {
            return false;
        }
        for (IPatternDetails.IInput input : inputs) {
            if (input == null) {
                return false;
            }
            GenericStack[] possibleInputs = input.getPossibleInputs();
            if (possibleInputs == null || possibleInputs.length == 0) {
                return false;
            }
            for (GenericStack candidate : possibleInputs) {
                if (candidate == null || candidate.what() == null) {
                    return false;
                }
            }
        }
        return true;
    }

    public static long operationsPerPush(IPatternDetails pattern) {
        return resolve(pattern).operationsPerPush();
    }

    /**
     * Resolves the output-derived manual multiplier carried by the unwrapped processing pattern.
     * Zero means the pattern cannot be proven to represent an exact number of recipe operations.
     */
    public static long manualOperationsPerPattern(
            AdvancedAlloyFurnaceRecipe recipe,
            IPatternDetails pattern) {
        ManualPatternOperationResolver.Resolution resolution =
                ManualPatternOperationResolver.resolve(recipe, unwrap(pattern));
        return resolution.valid() ? resolution.operationsPerPattern() : 0L;
    }

    public static Resolved resolve(IPatternDetails pattern) {
        Objects.requireNonNull(pattern, "pattern");
        IPatternDetails current = pattern;
        long operations = 1L;
        while (current instanceof ScaledPattern scaled) {
            operations = multiplyExactPositive(
                    operations, scaled.getOperationsPerPush(), "nested smart-doubling multiplier");
            current = scaled.getOriginal();
        }
        return new Resolved(current, operations);
    }

    public static long maximumSafeMultiplier(IPatternDetails pattern) {
        IPatternDetails original = unwrap(pattern);
        long maximum = Long.MAX_VALUE;
        for (IPatternDetails.IInput input : original.getInputs()) {
            if (input != null && input.getMultiplier() > 0L) {
                maximum = Math.min(maximum, Long.MAX_VALUE / input.getMultiplier());
                GenericStack[] possibleInputs = input.getPossibleInputs();
                if (possibleInputs != null) {
                    for (GenericStack possibleInput : possibleInputs) {
                        if (possibleInput != null && possibleInput.amount() > 0L) {
                            maximum = Math.min(maximum,
                                    Long.MAX_VALUE / input.getMultiplier() / possibleInput.amount());
                        }
                    }
                }
            }
        }
        for (GenericStack output : original.getOutputs()) {
            if (output != null && output.amount() > 0L) {
                maximum = Math.min(maximum, Long.MAX_VALUE / output.amount());
            }
        }
        return Math.max(1L, maximum);
    }

    @Nullable
    public static Long definitionOperations(AEItemKey definition) {
        return definition == null ? null : definition.get(UComponents.SMART_DOUBLING_OPERATIONS.get());
    }

    @Nullable
    public static IPatternDetails restore(AEItemKey definition, Level level) {
        Long operations = definitionOperations(definition);
        if (operations == null) {
            return null;
        }
        if (operations <= 0L || level == null) {
            return null;
        }

        ItemStack baseStack = definition.toStack();
        baseStack.remove(UComponents.SMART_DOUBLING_OPERATIONS.get());
        AEItemKey baseDefinition = AEItemKey.of(baseStack);
        if (baseDefinition == null) {
            return null;
        }

        IPatternDetails decoded = AdvancedAlloyFurnacePatternResolver.decode(
                baseDefinition.toStack(), level);
        if (decoded == null) {
            return null;
        }
        if (operations > maximumSafeMultiplier(decoded)) {
            return null;
        }
        return scale(decoded, operations, level);
    }

    static AEItemKey executionDefinition(IPatternDetails original, long operationsPerPush) {
        ItemStack definition = original.getDefinition().toStack();
        definition.remove(UComponents.SMART_DOUBLING_OPERATIONS.get());
        definition.set(UComponents.SMART_DOUBLING_OPERATIONS.get(), operationsPerPush);
        return Objects.requireNonNull(AEItemKey.of(definition), "scaled pattern definition");
    }

    static long multiplyExactPositive(long left, long right, String description) {
        if (left <= 0L || right <= 0L) {
            throw new IllegalArgumentException(description + " must be positive");
        }
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(description + " exceeds the long range", exception);
        }
    }

    public record Resolved(IPatternDetails pattern, long operationsPerPush) {
        public Resolved {
            Objects.requireNonNull(pattern, "pattern");
            if (operationsPerPush <= 0L) {
                throw new IllegalArgumentException("operationsPerPush must be positive");
            }
        }
    }
}
