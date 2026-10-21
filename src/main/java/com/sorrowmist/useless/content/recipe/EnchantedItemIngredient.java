package com.sorrowmist.useless.content.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sorrowmist.useless.init.ModIngredientTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * 匹配「指定物品 + 附魔达到最低等级」的原料。
 *
 * <p>原版 {@link Ingredient} 与 NeoForge 的 {@code DataComponentIngredient} 只能精确匹配组件值，
 * 无法表达「时运等级 &gt;= 3」这类下界要求，故以样板物品栈承载附魔要求：样板只需写出物品
 * 与各附魔的最低等级，匹配时要求实际物品相同，且每一项附魔等级都不低于样板。</p>
 *
 * <p>序列化沿用 {@link ItemStack#CODEC}，因此配方 JSON 里 {@code item} 字段就是一个普通物品栈，
 * 附魔以 {@code minecraft:enchantments} 组件自然表达，便于手写与阅读。</p>
 */
public final class EnchantedItemIngredient implements ICustomIngredient {
    public static final MapCodec<EnchantedItemIngredient> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ItemStack.CODEC.fieldOf("item")
                            .forGetter(EnchantedItemIngredient::template)
            ).apply(instance, EnchantedItemIngredient::new));

    public static final IngredientType<EnchantedItemIngredient> TYPE = new IngredientType<>(CODEC);

    private final ItemStack template;
    private final ItemEnchantments requiredEnchantments;

    public EnchantedItemIngredient(ItemStack template) {
        if (template == null || template.isEmpty()) {
            throw new IllegalArgumentException("附魔原料必须指定非空样板物品");
        }
        this.template = template.copyWithCount(1);
        this.requiredEnchantments = this.template.getEnchantments();
    }

    public static Ingredient of(ItemStack template) {
        return new Ingredient(new EnchantedItemIngredient(template));
    }

    ItemStack template() {
        return this.template.copy();
    }

    @Override
    public boolean test(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(this.template.getItem())) return false;
        if (this.requiredEnchantments.isEmpty()) return true;
        ItemEnchantments actual = stack.getEnchantments();
        for (var entry : this.requiredEnchantments.entrySet()) {
            if (actual.getLevel(entry.getKey()) < entry.getValue()) return false;
        }
        return true;
    }

    @Override
    public Stream<ItemStack> getItems() {
        return Stream.of(this.template.copy());
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return ModIngredientTypes.ENCHANTED_ITEM.get();
    }

    @Override
    public boolean equals(Object object) {
        return this == object
                || object instanceof EnchantedItemIngredient other
                && ItemStack.isSameItemSameComponents(this.template, other.template);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.template.getItem(), this.requiredEnchantments);
    }
}
