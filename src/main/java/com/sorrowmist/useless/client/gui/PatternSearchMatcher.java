package com.sorrowmist.useless.client.gui;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.integration.dataenergistics.search.OmniversalPatternMoldSearchTerms;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * 样板与模具的搜索匹配工具。
 *
 * <p>匹配在客户端执行：服务端只同步当前页的槽位内容，因此搜索必须自行遍历当前页并筛出
 * 命中项。样板的输入与输出分开索引，由 {@link PatternSearchMode} 决定本次查询作用于哪一侧；
 * 模具没有编码内容，按其自身物品名与配方登记的可接受物品名匹配。</p>
 */
final class PatternSearchMatcher {
    /** 样板缓存键按输入、输出、模具三侧分列，避免不同搜索范围互相污染结果。 */
    private static final Map<ItemStack, PatternSearchText> PATTERN_CACHE = new WeakHashMap<>();
    private static final Map<ItemStack, String> MOLD_CACHE = new WeakHashMap<>();

    private PatternSearchMatcher() {
    }

    /** 将查询串规范化为小写，空白查询视为“未过滤”。 */
    static String normalize(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 判断单个样板在指定搜索范围下是否命中查询。
     *
     * <p>空查询命中全部非空槽位。样板的输入与输出分别匹配，只有所选范围包含该侧且该侧文本
     * 含查询串时才算命中；解码不可用时退化为物品自身显示名，避免一次解码失败使样板无法被搜索。</p>
     */
    static boolean matchesPattern(ItemStack pattern, String normalizedQuery, PatternSearchMode mode,
                                  @Nullable Level level) {
        if (pattern.isEmpty()) return false;
        if (normalizedQuery.isEmpty()) return true;
        PatternSearchText text = patternSearchText(pattern, level);
        return mode.matchesOutput() && text.outputs().contains(normalizedQuery)
                || mode.matchesInput() && text.inputs().contains(normalizedQuery)
                || mode.matchesMold() && text.molds().contains(normalizedQuery);
    }

    /**
     * 判断单个模具是否命中查询。
     *
     * <p>模具没有编码内容，可搜索文本由物品显示名与配方登记的可接受物品名共同构成，
     * 因此按任意可接受物品名搜索都能命中对应模具。</p>
     */
    static boolean matchesMold(ItemStack mold, String normalizedQuery, @Nullable Level level) {
        if (mold.isEmpty()) return false;
        if (normalizedQuery.isEmpty()) return true;
        return moldSearchText(mold, level).contains(normalizedQuery);
    }

    private static PatternSearchText patternSearchText(ItemStack pattern, @Nullable Level level) {
        PatternSearchText cached = PATTERN_CACHE.get(pattern);
        if (cached != null) return cached;
        PatternSearchText text = buildPatternSearchText(pattern, level);
        // 以副本作为弱引用键，避免外部修改原栈后污染缓存条目。
        PATTERN_CACHE.put(pattern.copy(), text);
        return text;
    }

    private static String moldSearchText(ItemStack mold, @Nullable Level level) {
        String cached = MOLD_CACHE.get(mold);
        if (cached != null) return cached;
        String text = buildMoldSearchText(mold, level);
        MOLD_CACHE.put(mold.copy(), text);
        return text;
    }

    private static PatternSearchText buildPatternSearchText(ItemStack pattern, @Nullable Level level) {
        StringBuilder inputs = new StringBuilder();
        StringBuilder outputs = new StringBuilder();
        StringBuilder molds = new StringBuilder();
        // 样板自身的物品名归入三侧，使按样板名搜索在任意范围下都能命中。
        appendItemName(inputs, pattern);
        appendItemName(outputs, pattern);
        appendItemName(molds, pattern);
        // 样板设置的模具写入组件，直接读取即可，无需解码配方。
        appendPatternMolds(molds, pattern);

        AEItemKey key = AEItemKey.of(pattern);
        if (key != null) {
            // 处理样板自带编好的输入输出，无需走配方解码，避免为搜索触发动态配方兼容检查。
            if (pattern.get(AEComponents.ENCODED_PROCESSING_PATTERN) != null) {
                appendEncodedProcessingPattern(inputs, outputs, key);
            } else {
                appendDecodedPattern(inputs, outputs, pattern, level);
            }
        }
        // 单一出口：无论解码走到哪一步，三侧文本都按当前累积内容构造，
        // 物品名始终保留，因此各失败分支无需各自返回。
        return new PatternSearchText(inputs.toString(), outputs.toString(), molds.toString());
    }

    /** 从样板自带编码的输入输出中提取可搜索文本。 */
    private static void appendEncodedProcessingPattern(StringBuilder inputs, StringBuilder outputs, AEItemKey key) {
        try {
            AEProcessingPattern decoded = new AEProcessingPattern(key);
            for (var input : decoded.getInputs()) {
                appendPossibleInputs(inputs, input.getPossibleInputs());
            }
            appendStacks(outputs, decoded.getOutputs());
        } catch (RuntimeException ignored) {
            // 非法样板仅保留物品名匹配。
        }
    }

    /**
     * 从配方解码结果中提取可搜索文本。
     *
     * <p>解码仅在样板已编码且客户端层级可用时进行；条件不满足或解码失败时保留物品名匹配，
     * 不清空已有内容，也不向上抛出。</p>
     */
    private static void appendDecodedPattern(StringBuilder inputs, StringBuilder outputs, ItemStack pattern, @Nullable Level level) {
        Level effectiveLevel = level == null ? clientLevel() : level;
        if (effectiveLevel == null || !PatternDetailsHelper.isEncodedPattern(pattern)) return;
        try {
            var details = PatternDetailsHelper.decodePattern(pattern, effectiveLevel);
            if (details == null) return;
            for (var input : details.getInputs()) {
                appendPossibleInputs(inputs, input.getPossibleInputs());
            }
            appendStacks(outputs, details.getOutputs());
        } catch (RuntimeException ignored) {
            // 解码失败时保留物品名匹配，不清空已有内容。
        }
    }

    /**
     * 将样板设置的模具显示名写入模具侧可搜索文本。
     *
     * <p>样板所需的模具以组件形式随样板持久化，读取组件即可覆盖已编码样板；仅当组件缺失时
     * 才回退到配方解码得到的模具信息，避免搜索路径触发不必要的配方兼容检查。</p>
     */
    private static void appendPatternMolds(StringBuilder molds, ItemStack pattern) {
        // 只使用公开的 ItemStack 重载：data 版本是包私有的，跨包调用无法编译。
        for (String term : OmniversalPatternMoldSearchTerms.searchTerms(pattern)) {
            molds.append(term.toLowerCase(Locale.ROOT)).append('\n');
        }
    }

    private static String buildMoldSearchText(ItemStack mold, @Nullable Level level) {
        StringBuilder text = new StringBuilder();
        appendItemName(text, mold);

        Level effectiveLevel = level == null ? clientLevel() : level;
        if (effectiveLevel == null) return text.toString();

        // 模具按配方登记的 Ingredient 匹配，把命中配方的全部可接受物品名一并纳入，
        // 使按同一模具的任何可接受物品名都能搜到该模具。
        for (AlloyFurnaceRecipeCatalog.Entry entry : AlloyFurnaceRecipeCatalog.entries(effectiveLevel)) {
            var recipe = entry.recipe();
            if (recipe == null) continue;
            for (Ingredient registered : recipe.molds()) {
                if (registered == null || registered.isEmpty() || !registered.test(mold)) continue;
                for (ItemStack accepted : registered.getItems()) {
                    appendItemName(text, accepted);
                }
            }
        }
        return text.toString();
    }

    private static void appendPossibleInputs(StringBuilder text, GenericStack[] possible) {
        for (GenericStack stack : possible) {
            if (stack != null) appendKey(text, stack.what());
        }
    }

    private static void appendStacks(StringBuilder text, List<GenericStack> stacks) {
        for (GenericStack stack : stacks) {
            if (stack != null) appendKey(text, stack.what());
        }
    }

    private static void appendKey(StringBuilder text, @Nullable AEKey key) {
        if (key == null) return;
        key.getDisplayName().visit(content -> {
            text.append(content.toLowerCase(Locale.ROOT)).append('\n');
            return Optional.empty();
        });
    }

    private static void appendItemName(StringBuilder text, ItemStack stack) {
        if (stack.isEmpty()) return;
        text.append(stack.getHoverName().getString().toLowerCase(Locale.ROOT)).append('\n');
    }

    @Nullable
    private static Level clientLevel() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level;
    }

    /** 单个样板的输入侧、输出侧与模具侧可搜索文本，均已小写化。 */
    private record PatternSearchText(String inputs, String outputs, String molds) {
    }
}