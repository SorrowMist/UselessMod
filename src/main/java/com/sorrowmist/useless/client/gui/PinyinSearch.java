package com.sorrowmist.useless.client.gui;

import com.sorrowmist.useless.UselessMod;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * 容器列表的搜索匹配。
 *
 * <p>装了 <b>Just Enough Characters</b>（`jecharacters`，JEI 的拼音搜索就是它做的）时借它的
 * {@code me.towdium.jecharacters.utils.Match#contains} 做匹配——这样「xiangzi」也能搜到「箱子」，
 * 与 JEI 搜索行为一致。没装（或反射失败）时退回大小写不敏感的子串匹配。</p>
 *
 * <p>走反射而不是直接引用，是因为它是运行时才可能出现的可选模组，编译期并不存在。</p>
 */
final class PinyinSearch {
    private static final String MOD_ID = "jecharacters";
    private static final String MATCH_CLASS = "me.towdium.jecharacters.utils.Match";

    private static boolean resolved;
    @Nullable
    private static Method contains;

    private PinyinSearch() {
    }

    static boolean matches(String text, String query) {
        if (query.isEmpty()) {
            return true;
        }
        if (text.isEmpty()) {
            return false;
        }
        resolve();
        if (contains != null) {
            try {
                if (Boolean.TRUE.equals(contains.invoke(null, text, query))) {
                    return true;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // 借不到就按普通匹配来，不影响功能。
            }
        }
        // 拼音没命中（或借不到）时仍走普通子串匹配：中文直接输入、英文、坐标都还能搜。
        return text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        try {
            Class<?> match = Class.forName(MATCH_CLASS, true, PinyinSearch.class.getClassLoader());
            contains = match.getMethod("contains", CharSequence.class, CharSequence.class);
        } catch (ReflectiveOperationException | LinkageError exception) {
            UselessMod.LOGGER.warn(
                    "Just Enough Characters is installed but its Match API is unavailable; "
                            + "falling back to plain substring search", exception);
        }
    }
}
