package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AEProcessingPattern;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 单张万象样板的失效判定与重建。
 *
 * <p>不主动触发：只在玩家执行修复命令（{@code OmniversalPatternRepairService}）时
 * 逐张判定与重建，避免机器在每次载入存档时改动已编码的样板。判定分两层：
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
     * <p>绑定配方若仍能由 {@code recipeId} 唯一定位，先用旧 payload 重编码：旧 payload 里的
     * 输出带着玩家编码时绑定的具体组件（药水效果、附魔、灵名等），可以原样保留；只有旧
     * payload 无法重编码（例如输入已随配方改动而不再匹配）时，才退到用该配方**当前的输入
     * 输出**重建。recipeId 无法唯一定位时，退回用旧 payload 做内容匹配。</p>
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
            // 优先复用旧 payload：它保留了玩家编码时绑定的具体产物组件。
            ItemStack rebuilt = OmniversalPatternEncoding.reencodeExisting(stack, byRecipeId.get(), level);
            if (!rebuilt.isEmpty()) return rebuilt;
            rebuilt = OmniversalPatternEncoding.reencodeFromRecipe(byRecipeId.get(), level);
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