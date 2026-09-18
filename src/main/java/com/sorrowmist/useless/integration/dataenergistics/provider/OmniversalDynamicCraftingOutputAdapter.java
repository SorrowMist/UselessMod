package com.sorrowmist.useless.integration.dataenergistics.provider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.fish_dan_.data_energistics.api.crafting.dynamic.DynamicCraftingOutput;
import com.fish_dan_.data_energistics.api.crafting.dynamic.DynamicCraftingOutputAdapter;
import com.fish_dan_.data_energistics.api.crafting.dynamic.DynamicCraftingOutputMatchMode;
import com.fish_dan_.data_energistics.api.crafting.dynamic.DynamicCraftingOutputSemantics;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPatterns;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 把万象样板的「id-only 产物槽」声明给数据能源，让它的 bigint（exact）分支也能对上账。
 *
 * <p><b>为什么需要它</b>：万象样板里被标记为「只按物品 id 匹配」的产物槽，其<b>声明键只是模板</b> ——
 * 实际落进网络的是<b>配方产出</b>的键（可能带着运行时组件）。数据能源的计划会把声明键写进
 * CPU 的 {@code waitingFor}，如果实际键与模板键不等，那条等待就永远不会被满足。</p>
 *
 * <p>注册本适配器后，数据能源会把这些槽按 {@link DynamicCraftingOutputMatchMode#SAME_ITEM}
 * 处理（按注册物品匹配、忽略数据组件），并且<b>保留机器返回的完整键</b> —— 它只放宽「哪条等待可以
 * 接受这个键」，从不把返回的键改写成模板。</p>
 *
 * <p>本类必须<b>无状态</b>：不持有 CPU、供应器、网格、世界或合成任务（数据能源的接口契约要求）。</p>
 */
public final class OmniversalDynamicCraftingOutputAdapter implements DynamicCraftingOutputAdapter {
    /** 注册 id，同时也是去重与诊断用的稳定标识。 */
    public static final ResourceLocation ID = UselessMod.id("omniversal_pattern");

    @Override
    public @NotNull ResourceLocation id() {
        return ID;
    }

    @Override
    public @NotNull Optional<DynamicCraftingOutputSemantics> resolve(@NotNull IPatternDetails details) {
        IPatternDetails original = SmartDoublingPatterns.unwrap(details);
        if (!(original instanceof OmniversalPatternDetails omniversal)) {
            return Optional.empty();
        }
        List<GenericStack> outputs = original.getOutputs();
        List<DynamicCraftingOutput> declared = new ArrayList<>();
        for (int slot = 0; slot < outputs.size(); slot++) {
            GenericStack output = outputs.get(slot);
            if (output == null || output.what() == null || output.amount() <= 0L) {
                continue;
            }
            // 只有被标记为 id-only 的产物槽才是「模板键可能不等于实际键」；
            // SAME_ITEM 只对物品键有意义（数据能源会在构造时拒绝非物品键）。
            if (!omniversal.isItemIdOutput(slot) || !(output.what() instanceof AEItemKey)) {
                continue;
            }
            declared.add(new DynamicCraftingOutput(output, DynamicCraftingOutputMatchMode.SAME_ITEM));
        }
        return declared.isEmpty()
                ? Optional.empty()
                : Optional.of(new DynamicCraftingOutputSemantics(declared));
    }
}
