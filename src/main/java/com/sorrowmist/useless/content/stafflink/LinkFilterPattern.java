package com.sorrowmist.useless.content.stafflink;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 过滤器里的一格「模式」标记：{@code #tag} 或含通配符的字面量。
 *
 * <p><b>为什么需要它。</b> 具体标记（一个 {@link ItemStack} / {@link FluidStack}）只能表达
 * 「就是这一种」。可玩家常常想说的是「所有锭」「所有带 {@code iron} 字样的东西」——那要靠
 * 标签或通配符。这一格因此有三种形态：物品、流体、<b>模式</b>。</p>
 *
 * <h2>两种模式</h2>
 *
 * <ul>
 *   <li><b>{@link Kind#TAG}</b>：{@code #namespace:path}，按<b>标签</b>匹配。用的是静态注册表的
 *       标签查询（{@code BuiltInRegistries.ITEM.getTagOrEmpty}），因此<b>不需要</b>
 *       {@code HolderLookup.Provider}，也就能在服务端搬运路径里直接用。</li>
 *   <li><b>{@link Kind#GLOB}</b>：含 {@code *} 或 {@code ?} 的字面量，按<b>注册表 id 与显示名</b>
 *       两边都试。{@code *} 匹配任意长、{@code ?} 匹配恰好一个字符。</li>
 * </ul>
 *
 * <h2>为什么「纯字面量」会被拒绝</h2>
 *
 * <p>{@code minecraft:iron_ingot} 这种既没 {@code #} 也没通配符的输入<b>解析结果为
 * {@code null}</b>（调用方当作非法输入退回）。理由：一个不带组件的裸 id 无法表达
 * 「铁锭（带某某附魔）」，它到底匹不匹配永远说不清；接受了它只会让玩家以为「打上去了」，
 * 而实际永远匹配不上。想精确指定就用具体标记（拿物品点格子 / 从 JEI 拖入）。</p>
 *
 * <h2>未知标签 = 匹配不上任何东西</h2>
 *
 * <p>刻意<b>不</b>把「标签没注册」当成「不限制」。若当不限制，玩家把 {@code #c:ingots} 打错
 * 一个字，整张网络就会全灌进目标容器——破坏性后果不可逆。宁可「什么都不搬」（界面上能看见、
 * 玩家能自己改），也不要「全搬」。</p>
 *
 * <h2>不可变</h2>
 *
 * <p>解析即归一化，之后不再改动；{@link #equals}/{@link #hashCode} 由 record 语义给出，
 * 因此可以安全地放进 {@link LinkFilterSlot} 并参与 NBT / 网络编解码的比较。</p>
 */
public record LinkFilterPattern(Kind kind, String text, @Nullable TagKey<Item> itemTag,
                                @Nullable TagKey<Fluid> fluidTag) {

    /** 输入的形态。 */
    public enum Kind {
        /** {@code #namespace:path}，按标签匹配。 */
        TAG,
        /** 含通配符的字面量，按 id / 显示名匹配。 */
        GLOB
    }

    /** 模式原文长度上限；超出直接判非法（存档被改坏时也不必去匹配一个巨型字符串）。 */
    public static final int MAX_LENGTH = 128;

    /** 通配符：匹配任意长（含空）。 */
    private static final char WILDCARD_ANY = '*';
    /** 通配符：匹配恰好一个字符。 */
    private static final char WILDCARD_ONE = '?';

    public LinkFilterPattern {
        kind = kind == null ? Kind.GLOB : kind;
        text = text == null ? "" : text;
    }

    // ------------------------------------------------------------------ 解析

    /**
     * 把玩家的输入解析成一个模式；<b>非法输入返回 {@code null}</b>。
     *
     * <p>判定顺序：</p>
     * <ol>
     *   <li>空白 / 超长 → {@code null}</li>
     *   <li>以 {@code #} 开头 → 按标签解析（id 非法同样返回 {@code null}）</li>
     *   <li>含 {@code *} 或 {@code ?} → {@link Kind#GLOB}</li>
     *   <li>其余 → {@code null}（见类注释「为什么纯字面量会被拒绝」）</li>
     * </ol>
     */
    @Nullable
    public static LinkFilterPattern parse(@Nullable String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
            return null;
        }
        if (trimmed.charAt(0) == '#') {
            return parseTag(trimmed);
        }
        if (trimmed.indexOf(WILDCARD_ANY) < 0 && trimmed.indexOf(WILDCARD_ONE) < 0) {
            // 没有 # 也没有通配符：一个裸 id 表达不了组件，接受它只会误导玩家。
            return null;
        }
        return new LinkFilterPattern(Kind.GLOB, trimmed, null, null);
    }

    @Nullable
    private static LinkFilterPattern parseTag(String raw) {
        String body = raw.substring(1).trim();
        if (body.isEmpty() || body.length() > MAX_LENGTH) {
            return null;
        }
        ResourceLocation id = parseId(body);
        if (id == null) {
            return null;
        }
        // 物品与流体两个标签都挂上：输入时并不知道玩家这条线路搬的是什么，匹配时按需取用。
        // 两个 TagKey 由同一个 id 派生，因此「同一份文本 ⇒ 同一个 record」成立。
        return new LinkFilterPattern(Kind.TAG, raw,
                TagKey.create(Registries.ITEM, id), TagKey.create(Registries.FLUID, id));
    }

    /**
     * 宽松解析一个资源 id：没写 namespace 时补 {@code minecraft}。
     *
     * <p>{@code ResourceLocation.tryParse} 本身要求写全 {@code namespace:path}，而玩家输入
     * {@code #c:ingots} 时那条 {@code c} 路径也是常见的（通用标签约定），所以两条都试一遍。</p>
     */
    @Nullable
    private static ResourceLocation parseId(String body) {
        ResourceLocation withNamespace = ResourceLocation.tryParse(body);
        if (withNamespace != null) {
            return withNamespace;
        }
        return ResourceLocation.tryParse("minecraft:" + body);
    }

    // ------------------------------------------------------------------ 匹配

    /**
     * 是否匹配这个物品。
     *
     * <p>{@link Kind#TAG} 走静态注册表标签查询；{@link Kind#GLOB} 先比 id，id 不中才构造显示名
     * 再比一次（显示名要新建 {@code Component}，能短路就短路）。</p>
     *
     * <p>{@code tag} 不存在时 {@link #matchesTag} 返回 {@code false}（见类注释）。</p>
     */
    public boolean matchesItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (kind == Kind.TAG) {
            return matchesTag(BuiltInRegistries.ITEM, itemTag, stack.getItem());
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (matchesGlob(id == null ? "" : id.toString())) {
            return true;
        }
        // id 未命中才付显示名的代价（语言相关，但这是玩家自己选的匹配方式）。
        return matchesGlob(stack.getHoverName().getString());
    }

    /** 是否匹配这种流体；语义与 {@link #matchesItem(ItemStack)} 完全对称。 */
    public boolean matchesFluid(FluidStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (kind == Kind.TAG) {
            return matchesTag(BuiltInRegistries.FLUID, fluidTag, stack.getFluid());
        }
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        if (matchesGlob(id == null ? "" : id.toString())) {
            return true;
        }
        return matchesGlob(stack.getHoverName().getString());
    }

    /**
     * 是否匹配某个化学品。
     *
     * <p><b>只支持 {@link Kind#GLOB}，且只按 id。</b> 化学品没有稳定的标签体系（Mekanism 的
     * 化学品注册表挂在自己的注册表上，跨模组没有通用 TagKey），所以 {@code #} 形式对化学品
     * 永远不成立；{@code typeKey()} 又是个不透明的对象，界面上也拿不到显示名，因此只比 id。</p>
     *
     * <p>{@code chemicalId} 为 {@code null} 表示这一端的化学品集成<b>给不出</b> id
     * （见 {@code ChemicalCompatProvider#chemicalIdOf} 的默认实现）；此时 {@link Kind#TAG}
     * 恒 {@code false}，{@link Kind#GLOB} 也恒 {@code false}——宁可「匹配不上」（可观察、
     * 可修正），也不要在拿不到 id 时放行一切。</p>
     */
    public boolean matchesChemicalId(@Nullable ResourceLocation chemicalId) {
        if (kind == Kind.TAG || chemicalId == null) {
            return false;
        }
        return matchesGlob(chemicalId.toString());
    }

    /**
     * 这个模式在当前环境里是否「可能匹配到东西」。
     *
     * <p>界面用它把打错的标签标红：{@link Kind#TAG} 查一下标签注册表里有没有这一条；
     * {@link Kind#GLOB} 恒为真（通配符能不能匹配到要看具体资源，静态判不了）。</p>
     */
    public boolean isResolvable() {
        if (kind == Kind.GLOB) {
            return true;
        }
        if (itemTag != null && BuiltInRegistries.ITEM.getTag(itemTag).isPresent()) {
            return true;
        }
        return fluidTag != null && BuiltInRegistries.FLUID.getTag(fluidTag).isPresent();
    }

    /** 这一格是 {@code #tag} 形式。*/
    public boolean isTag() {
        return kind == Kind.TAG;
    }

    /** 界面上显示的字样（{@code #tag} 原样，glob 原样）。 */
    public String displayName() {
        return text;
    }

    // ------------------------------------------------------------------ 内部

    private static <T> boolean matchesTag(net.minecraft.core.Registry<T> registry,
                                          @Nullable TagKey<T> tag, T value) {
        if (tag == null || value == null) {
            return false;
        }
        // 遍历标签内容而不是 wrapAsHolder(...).is(tag)：后者要先把对象包成 Holder 再查，
        // 这里已经在注册表上，直接取条目列表更直白，且对「标签不存在」天然返回空。
        for (var holder : registry.getTagOrEmpty(tag)) {
            if (holder.value() == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * 通配符匹配：{@code *} 任意长、{@code ?} 恰好一个。
     *
     * <p>手写两指针回溯（不用正则）：一是不必在搬运热路径上编译 {@code Pattern}，二是这里的
     * 语义只有两个通配符，正则那套转义反而容易出错。回溯版本最坏 O(n·m)，但输入上限 128、
     * 被匹配的 id 通常只有几十字符，代价可忽略。</p>
     */
    private boolean matchesGlob(String candidate) {
        return globMatches(text, candidate);
    }

    /**
     * {@code *}/{@code ?} 通配符匹配。<b>包级可见</b>仅为方便测试，实现细节不属于公开契约。
     *
     * @param pattern   模式（含通配符）
     * @param candidate 被匹配的字符串
     */
    static boolean globMatches(String pattern, String candidate) {
        int p = 0;
        int c = 0;
        int starP = -1;
        int starC = 0;
        int patternLength = pattern.length();
        int candidateLength = candidate.length();
        while (c < candidateLength) {
            if (p < patternLength
                    && (pattern.charAt(p) == WILDCARD_ONE || pattern.charAt(p) == candidate.charAt(c))) {
                p++;
                c++;
            } else if (p < patternLength && pattern.charAt(p) == WILDCARD_ANY) {
                // 记下「从这里开始可以吞任意长」，先试着不吞。
                starP = p;
                starC = c;
                p++;
            } else if (starP >= 0) {
                // 回退：上一个 * 多吞一个字符，模式指针回到 * 之后。
                starC++;
                c = starC;
                p = starP + 1;
            } else {
                return false;
            }
        }
        // 候选串走完了，模式剩下的必须全是 *。
        while (p < patternLength && pattern.charAt(p) == WILDCARD_ANY) {
            p++;
        }
        return p == patternLength;
    }

    /** 供界面列举「这一格大概匹配什么」用：把所有候选 id 里命中的挑出来（上限 {@code max}）。 */
    public List<ResourceLocation> sampleItemIds(int max) {
        List<ResourceLocation> result = new ArrayList<>();
        if (max <= 0) {
            return result;
        }
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) {
                continue;
            }
            if (kind == Kind.TAG ? matchesTag(BuiltInRegistries.ITEM, itemTag, item) : matchesGlob(id.toString())) {
                result.add(id);
                if (result.size() >= max) {
                    break;
                }
            }
        }
        return result;
    }
}
