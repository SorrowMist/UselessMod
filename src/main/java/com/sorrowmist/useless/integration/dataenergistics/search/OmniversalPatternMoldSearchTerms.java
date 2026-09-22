package com.sorrowmist.useless.integration.dataenergistics.search;

import com.fish_dan_.data_energistics.api.registry.search.TrinityPatternSearchTermContributor;
import com.sorrowmist.useless.core.component.OmniversalPatternData;
import com.sorrowmist.useless.core.component.UComponents;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Extracts encoded internal molds as separate Trinity pattern-search candidates.
 */
public final class OmniversalPatternMoldSearchTerms {

    private OmniversalPatternMoldSearchTerms() {}

    /**
     * 数据能源 3.3.0 的 {@code TrinityPatternSearchTermContributor} 实现。
     *
     * <p>3.3.0 起该接口的两个方法都是 default（新增了 FastUtil 版 {@code searchTermsFast}），
     * 所以它<b>不再是函数接口</b>，不能再用方法引用注册，必须给出实现类型。</p>
     */
    public static final class Contributor implements TrinityPatternSearchTermContributor {
        /**
         * {@inheritDoc}
         *
         * <p>必须经由 {@link OmniversalPatternMoldSearchTerms#searchTerms(ItemStack)} 限定调用外层类的静态方法。
         * 接口自带的同名 default 方法返回空列表，而继承来的实例方法在本类作用域内优先于外层类的静态方法；
         * 若以非限定名调用，编译为虚调用并静默落入该空实现，模具名候选将恒为空。</p>
         */
        @Override
        public ObjectList<String> searchTermsFast(ItemStack encodedPattern) {
            return ObjectLists.unmodifiable(new ObjectArrayList<>(
                    OmniversalPatternMoldSearchTerms.searchTerms(encodedPattern)));
        }
    }

    /**
     * Returns mold display names stored in one Omniversal Pattern, when that pattern needs molds.
     *
     * @param encodedPattern candidate encoded pattern stack
     * @return independent mold-name candidates, or no candidates for every other pattern
     */
    public static List<String> searchTerms(ItemStack encodedPattern) {
        OmniversalPatternData data = encodedPattern.get(UComponents.OMNIVERSAL_PATTERN_DATA.get());
        return data == null ? List.of() : searchTerms(data);
    }

    /**
     * Returns display candidates for metadata that has already been decoded from an Omniversal Pattern.
     *
     * @param data persisted Omniversal Pattern metadata
     * @return independent mold-name candidates, or no candidates when no mold is required
     */
    static List<String> searchTerms(OmniversalPatternData data) {
        if (!data.requiresMold()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        Set<Integer> tagMoldSlots = new HashSet<>();
        data.moldTagInputSlots().forEach(slot -> tagMoldSlots.add(slot.moldSlot()));
        if (!data.displayMolds().isEmpty()) {
            for (int moldSlot = 0; moldSlot < data.displayMolds().size(); moldSlot++) {
                if (tagMoldSlots.contains(moldSlot)) continue;
                terms.add(data.displayMolds().get(moldSlot).getDisplayName().getString());
            }
        } else if (!tagMoldSlots.contains(0)) {
            data.displayMold().map(key -> key.getDisplayName().getString()).ifPresent(terms::add);
        }
        data.moldTagInputSlots().stream()
                .map(slot -> "#" + slot.tag().location())
                .forEach(terms::add);
        return terms.stream().distinct().toList();
    }
}
