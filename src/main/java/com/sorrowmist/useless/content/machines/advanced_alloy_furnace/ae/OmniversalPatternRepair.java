package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AEProcessingPattern;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;

/**
 * 单张万象样板的失效判定与重建。
 *
 * <p>与网格无关，供机器在自身样板槽上就地修复，也供命令扫描网格时复用。判定分两层：
 * 无法解码为 {@link OmniversalPatternDetails}，或 payload 仍可解码但输入槽语义已与当前
 * 配方不符（见 {@link OmniversalPatternEncoding#isStale}）。</p>
 */
public final class OmniversalPatternRepair {
    private OmniversalPatternRepair() {
    }

    /** 判定一张万象样板是否需要按当前配方重建。 */
    public static boolean needsRepair(ItemStack stack, Level level) {
        if (stack == null || stack.isEmpty() || level == null) {
            return false;
        }
        if (!isDecodable(stack, level)) {
            return true;
        }
        return OmniversalPatternEncoding.isStale(stack, level);
    }

    /**
     * 按当前配方目录重建一张万象样板；无法重建时返回 {@link ItemStack#EMPTY}。
     *
     * <p>绑定配方若仍能由 {@code recipeId} 唯一定位，就以该配方**当前的输入输出**重建
     * payload——配方被改动（输入变化）而失效的样板只能这样修复。只有 recipeId 无法唯一
     * 定位时才退回用旧 payload 做内容匹配，复用旧 payload 重编码。</p>
     */
    public static ItemStack rebuild(ItemStack stack, Level level) {
        if (stack == null || stack.isEmpty() || level == null) {
            return ItemStack.EMPTY;
        }
        OmniversalPatternData data = stack.get(UComponents.OMNIVERSAL_PATTERN_DATA.get());
        if (data == null) {
            return ItemStack.EMPTY;
        }

        Optional<AlloyFurnaceRecipeCatalog.Entry> byRecipeId = resolveEntryByRecipeId(data, level);
        if (byRecipeId.isPresent()) {
            ItemStack rebuilt = OmniversalPatternEncoding.reencodeFromRecipe(byRecipeId.get(), level);
            if (!rebuilt.isEmpty()) return rebuilt;
        }

        AEItemKey definition = AEItemKey.of(stack);
        if (definition == null) return ItemStack.EMPTY;

        AEProcessingPattern source;
        try {
            source = new AEProcessingPattern(definition);
        } catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }

        Optional<AlloyFurnaceRecipeCatalog.Entry> entry = resolveEntry(data, source, level);
        return entry.map(value -> OmniversalPatternEncoding.reencodeExisting(stack, value, level))
                .orElse(ItemStack.EMPTY);
    }

    /**
     * 就地修复一段样板槽中全部失真的万象样板，返回实际写回的槽数。
     *
     * <p>供机器在区块加载、样板栏加载完成后自动执行一次，使旧样板无需玩家手动执行命令。
     * 调用方须先确认配方目录已就绪（{@link AlloyFurnaceRecipeCatalog#isReady}），否则
     * 失效判定会因取不到当前配方而失效，只能空跑一轮。</p>
     *
     * @param slotCount 要扫描的槽位数（不含总成的恢复槽）
     * @param getter    按槽位读取物品栈
     * @param setter    按槽位写回物品栈，应触发调用方的样板刷新回调
     */
    public static int repairSlots(int slotCount, IntFunction<ItemStack> getter,
                                  BiConsumer<Integer, ItemStack> setter, Level level) {
        if (slotCount <= 0 || getter == null || setter == null || level == null) {
            return 0;
        }
        int repaired = 0;
        for (int slot = 0; slot < slotCount; slot++) {
            ItemStack stack = getter.apply(slot);
            if (stack == null || stack.isEmpty() || !stack.is(ModItems.OMNIVERSAL_PATTERN.get())) {
                continue;
            }
            if (!needsRepair(stack, level)) continue;

            ItemStack replacement = rebuild(stack, level);
            if (replacement.isEmpty()) continue;
            if (ItemStack.isSameItemSameComponents(stack, replacement)) continue;

            setter.accept(slot, replacement);
            repaired++;
        }
        return repaired;
    }

    private static boolean isDecodable(ItemStack stack, Level level) {
        try {
            IPatternDetails decoded = PatternDetailsHelper.decodePattern(stack, level);
            return decoded instanceof OmniversalPatternDetails;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 仅按 {@code recipeId} 定位当前配方：忽略指纹与旧 payload，返回唯一匹配项。
     *
     * <p>优先在样板记录的 {@code sourceId} 范围内查找，找不到再退到全目录；命中数不为 1
     * （无匹配或存在歧义）时返回空，交由内容匹配回退处理。</p>
     */
    private static Optional<AlloyFurnaceRecipeCatalog.Entry> resolveEntryByRecipeId(
            OmniversalPatternData data, Level level) {
        var recipeId = data.recipeId();
        if (recipeId == null) return Optional.empty();

        List<AlloyFurnaceRecipeCatalog.Entry> matched = new ArrayList<>();
        for (AlloyFurnaceRecipeCatalog.Entry entry : AlloyFurnaceRecipeCatalog.entries(level, data.sourceId())) {
            if (recipeId.equals(entry.identity().recipeId())) matched.add(entry);
        }
        if (matched.isEmpty()) {
            for (AlloyFurnaceRecipeCatalog.Entry entry : AlloyFurnaceRecipeCatalog.entries(level)) {
                if (recipeId.equals(entry.identity().recipeId())) matched.add(entry);
            }
        }
        return matched.size() == 1 ? Optional.of(matched.getFirst()) : Optional.empty();
    }

    /**
     * 按稳健性递减的顺序回查当前配方目录。
     *
     * <p>首选按 {@code sourceId + identity} 精确匹配（旧版样板必须走 legacy 指纹算法）；
     * 其次退到不带画像的身份匹配；最后按样板内容模糊匹配候选，并以确定性评分取首个。</p>
     */
    private static Optional<AlloyFurnaceRecipeCatalog.Entry> resolveEntry(
            OmniversalPatternData data, AEProcessingPattern source, Level level) {
        Optional<AlloyFurnaceRecipeCatalog.Entry> entry =
                data.version() < OmniversalPatternData.TAG_INPUT_VERSION
                        ? AlloyFurnaceRecipeCatalog.resolveLegacyPattern(
                                level, data.sourceId(), data.identity(), source, data.version())
                        : AlloyFurnaceRecipeCatalog.resolvePattern(
                                level, data.sourceId(), data.identity(), source);
        if (entry.isPresent()) return entry;

        entry = AlloyFurnaceRecipeCatalog.resolve(level, data.sourceId(), data.identity());
        if (entry.isPresent()) return entry;

        entry = AlloyFurnaceRecipeCatalog.resolve(level, data.identity());
        if (entry.isPresent()) return entry;

        // allowMissingOutputs=true 是该目录为 omniversal converter 预留的内容匹配模式。
        return selectBest(AlloyFurnaceRecipeCatalog.findPatternCandidates(level, source, true));
    }

    /** 模具数多者优先，其次按 recipeId 字符串序，保证多次运行结果稳定。 */
    private static Optional<AlloyFurnaceRecipeCatalog.Entry> selectBest(
            List<AlloyFurnaceRecipeCatalog.Entry> candidates) {
        AlloyFurnaceRecipeCatalog.Entry best = null;
        for (AlloyFurnaceRecipeCatalog.Entry candidate : candidates) {
            if (candidate == null || candidate.recipe() == null) continue;
            if (best == null || compareEntries(candidate, best) < 0) best = candidate;
        }
        return Optional.ofNullable(best);
    }

    private static int compareEntries(AlloyFurnaceRecipeCatalog.Entry left,
                                      AlloyFurnaceRecipeCatalog.Entry right) {
        int moldComparison = Integer.compare(
                right.recipe().molds().size(), left.recipe().molds().size());
        if (moldComparison != 0) return moldComparison;
        return left.identity().recipeId().toString()
                .compareTo(right.identity().recipeId().toString());
    }
}