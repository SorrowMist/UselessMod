package com.sorrowmist.useless.compat.forge1201.jei;

import appeng.api.stacks.*;
import com.google.gson.*;
import com.sorrowmist.useless.compat.jei.*;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.*;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.jemi.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.util.List;

/** Scoped JEMI bridge: native recipe identity, full AE key persistence and long quantities.
 * No EMI entrypoint: JEMI must continue importing this mod's JEI recipe categories.
 */
public final class AEKeyEmiStack extends JemiStack<AEKeyJeiIngredients.KeyIngredient> {
    private final EmiStack nativeStack;
    public AEKeyEmiStack(AEKeyJeiIngredients.KeyIngredient value) {
        super(AEKeyJeiIngredients.TYPE, AEKeyJeiIngredients.HELPER, AEKeyJeiIngredients.RENDERER, value);
        var converted = GenericStackJeiIngredientProviders.resolveNative(new GenericStack(value.key(), 1));
        nativeStack = converted == null ? null : JemiUtil.getStack(converted.type(), converted.value());
        amount = value.amount();
    }
    @Override public AEKeyEmiStack copy() {
        var copy = new AEKeyEmiStack(new AEKeyJeiIngredients.KeyIngredient(ingredient.key(), Math.max(1, amount)));
        copy.amount = amount;
        copy.chance = chance;
        copy.comparison = comparison;
        copy.setRemainder(getRemainder());
        return copy;
    }
    @Override public Object getKey() { return nativeStack == null ? super.getKey() : nativeStack.getKey(); }
    @Override public CompoundTag getNbt() { return nativeStack == null ? null : nativeStack.getNbt(); }
    @Override public ItemStack getItemStack() {
        if (ingredient.key() instanceof AEItemKey item) return item.toStack((int)Math.min(Integer.MAX_VALUE, amount));
        return ItemStack.EMPTY;
    }
    @Override public List<Component> getTooltipText() {
        return AEKeyJeiIngredients.RENDERER.getTooltip(
                new AEKeyJeiIngredients.KeyIngredient(ingredient.key(), Math.max(1, amount)), TooltipFlag.NORMAL);
    }
    @Override public List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> getTooltip() {
        // JEMI renders the immutable ingredient; rebuild its view after EMI changes the amount.
        return amount == ingredient.amount() ? originalTooltip() : copy().originalTooltip();
    }
    private List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> originalTooltip() {
        return super.getTooltip();
    }
    @Override public void render(GuiGraphics gui, int x, int y, float delta, int flags) {
        if ((flags & 1) != 0) {
            gui.pose().pushPose();
            gui.pose().translate(x, y, 0);
            AEKeyJeiIngredients.RENDERER.render(gui, ingredient);
            gui.pose().popPose();
        }
        if ((flags & 2) != 0 && amount > 1) {
            var font = net.minecraft.client.Minecraft.getInstance().font;
            String text = ingredient.key().formatAmount(amount, AmountFormat.SLOT);
            gui.pose().pushPose(); gui.pose().translate(0, 0, 200);
            gui.drawString(font, text, x + 17 - font.width(text), y + 9, 0xffffff, true);
            gui.pose().popPose();
        }
    }
    public static void register(EmiRegistry registry) {
        registry.addIngredientSerializer(AEKeyEmiStack.class, new EmiIngredientSerializer<AEKeyEmiStack>() {
            @Override public String getType() { return "useless_mod:ae_key"; }
            @Override public JsonElement serialize(AEKeyEmiStack stack) {
                var json = new JsonObject();
                json.addProperty("type", getType());
                json.addProperty("key", stack.ingredient.key().toTagGeneric().toString());
                json.addProperty("amount", stack.amount);
                json.addProperty("chance", stack.chance);
                return json;
            }
            @Override public EmiIngredient deserialize(JsonElement element) {
                try {
                    var json = element.getAsJsonObject();
                    var key = AEKey.fromTagGeneric(TagParser.parseTag(json.get("key").getAsString()));
                    long count = json.get("amount").getAsLong();
                    if (key == null || count <= 0) return EmiStack.EMPTY;
                    var stack = new AEKeyEmiStack(new AEKeyJeiIngredients.KeyIngredient(key, count));
                    if (json.has("chance")) stack.setChance(json.get("chance").getAsFloat());
                    return stack;
                } catch (Exception exception) { return EmiStack.EMPTY; }
            }
        });
    }
}
