package com.sorrowmist.useless.content.recipe.adapters.apotheosis;

import com.mojang.serialization.MapCodec;

import dev.shadowsoffire.apotheosis.adventure.socket.gem.Gem;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemItem;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemRegistry;
import dev.shadowsoffire.placebo.reload.DynamicHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import com.sorrowmist.useless.compat.forge1201.recipe.CodecIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.CodecIngredientSerializer;


import java.util.ArrayList;
import java.util.List;

/**
 * 匹配任意神化宝石的原料。
 *
 * <p>神化宝石是动态物品，其名称、效果与渲染均取自 {@code apotheosis:gem} 数据组件。
 * {@code Ingredient.of(gem)} 生成的候选栈不含该组件，展示时会退化为
 * {@code item.apotheosis.gem} 并附带 {@code Errored gem with no bonus!} 提示。</p>
 *
 * <p>本类在匹配时只要求目标栈为宝石物品且持有已绑定的宝石组件；候选栈则返回带完整组件的
 * 合法宝石栈，供 JEI 与配方界面展示使用。</p>
 *
 * <p>本类引用了神化的类型，因此<b>只能在神化存在时被加载</b>：注册入口
 * {@code ModIngredientTypes.APOTHEOSIS_GEM} 已做加载检查，不要在任何无条件执行的静态
 * 初始化里触碰本类。</p>
 */
public final class ApotheosisGemIngredient extends CodecIngredient<ApotheosisGemIngredient> {

    public static final ApotheosisGemIngredient INSTANCE = new ApotheosisGemIngredient();

    public static final MapCodec<ApotheosisGemIngredient> CODEC = MapCodec.unit(INSTANCE);

    public static final CodecIngredientSerializer<ApotheosisGemIngredient> TYPE = new CodecIngredientSerializer<>(CODEC.codec());

    private ApotheosisGemIngredient() {
    }

    /** 构造匹配任意神化宝石的原料。 */
    public static Ingredient of() {
        return INSTANCE;
    }

    @Override
    public boolean test(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof GemItem)) {
            return false;
        }
        DynamicHolder<Gem> holder = GemItem.getGem(stack);
        return holder.isBound();
    }

    @Override
    public ItemStack[] getItems() {
        List<ItemStack> stacks = new ArrayList<>();
        for (Gem gem : GemRegistry.INSTANCE.getValues()) {
            // 每种宝石仅提供一个代表栈，避免候选列表随品级数量膨胀。
            stacks.add(GemRegistry.createGemStack(gem,gem.getMinRarity()));
        }
        return stacks.toArray(ItemStack[]::new);
    }

    @Override
    public CodecIngredientSerializer<ApotheosisGemIngredient> getSerializer() { return TYPE; }

    @Override
    public boolean equals(Object object) {
        return object instanceof ApotheosisGemIngredient;
    }

    @Override
    public int hashCode() {
        return ApotheosisGemIngredient.class.hashCode();
    }
}
