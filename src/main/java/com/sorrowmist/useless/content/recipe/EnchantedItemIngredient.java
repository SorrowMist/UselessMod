package com.sorrowmist.useless.content.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeCodecs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.crafting.AbstractIngredient;
import net.minecraftforge.common.crafting.IIngredientSerializer;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/** Same item, with every template enchantment at least its required level. Extra NBT is allowed. */
public final class EnchantedItemIngredient extends AbstractIngredient {
    public static final ResourceLocation ID = new ResourceLocation("useless_mod", "enchanted_item");
    public static final IIngredientSerializer<EnchantedItemIngredient> TYPE = new Serializer();
    private final ItemStack template;
    private final Map<Enchantment, Integer> requiredEnchantments;

    public EnchantedItemIngredient(ItemStack template) {
        super(Stream.of(new Ingredient.ItemValue(checkedCopy(template))));
        this.template = checkedCopy(template);
        this.requiredEnchantments = Map.copyOf(EnchantmentHelper.getEnchantments(this.template));
    }

    private static ItemStack checkedCopy(ItemStack template) {
        if (template == null || template.isEmpty()) throw new IllegalArgumentException("Empty enchanted ingredient template");
        return template.copyWithCount(1);
    }

    public static Ingredient of(ItemStack template) { return new EnchantedItemIngredient(template); }
    public ItemStack template() { return template.copy(); }

    @Override
    public boolean test(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(template.getItem())) return false;
        Map<Enchantment, Integer> actual = EnchantmentHelper.getEnchantments(stack);
        return requiredEnchantments.entrySet().stream()
                .allMatch(entry -> actual.getOrDefault(entry.getKey(), 0) >= entry.getValue());
    }

    @Override public boolean isSimple() { return false; }
    @Override public IIngredientSerializer<? extends Ingredient> getSerializer() { return TYPE; }
    @Override public ItemStack[] getItems() { return new ItemStack[] {template()}; }
    @Override public boolean isEmpty() { return false; }

    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", ID.toString());
        // Forge 1.20.1 item/count/nbt, never NeoForge 1.21 components.
        json.add("item", RecipeCodecs.writeItem(template));
        return json;
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof EnchantedItemIngredient other
                && template.is(other.template.getItem()) && requiredEnchantments.equals(other.requiredEnchantments);
    }

    @Override public int hashCode() { return Objects.hash(template.getItem(), requiredEnchantments); }

    private static final class Serializer implements IIngredientSerializer<EnchantedItemIngredient> {
        @Override public EnchantedItemIngredient parse(FriendlyByteBuf buffer) {
            return new EnchantedItemIngredient(buffer.readItem());
        }
        @Override public EnchantedItemIngredient parse(JsonObject json) {
            return new EnchantedItemIngredient(RecipeCodecs.readItem(GsonHelper.getAsJsonObject(json, "item")));
        }
        @Override public void write(FriendlyByteBuf buffer, EnchantedItemIngredient ingredient) {
            // Full NBT (not the item's limited share-tag) preserves the enchantment requirements.
            buffer.writeItemStack(ingredient.template, false);
        }
    }
}
