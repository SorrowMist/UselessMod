package com.sorrowmist.useless.compat.forge1201.jei;

import appeng.api.stacks.*;
import com.google.gson.*;
import com.sorrowmist.useless.compat.jei.*;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.*;
import dev.emi.emi.api.stack.serializer.EmiIngredientSerializer;
import dev.emi.emi.jemi.*;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.util.List;
import java.util.Optional;

/** Scoped JEMI bridge: native recipe identity, full AE key persistence and long quantities.
 * No EMI entrypoint: JEMI must continue importing this mod's JEI recipe categories.
 */
public final class AEKeyEmiStack extends JemiStack<AEKeyJeiIngredients.KeyIngredient> {
    private final EmiStack nativeStack;
    public AEKeyEmiStack(AEKeyJeiIngredients.KeyIngredient value) {
        super(AEKeyJeiIngredients.TYPE, AEKeyJeiIngredients.HELPER, AEKeyJeiIngredients.RENDERER, value);
        var converted = GenericStackJeiIngredientProviders.resolveNative(new GenericStack(value.key(), 1));
        nativeStack = converted == null ? null : nativeStack(converted.type(), converted.value());
        amount = value.amount();
    }
    private static <T> EmiStack nativeStack(IIngredientType<T> type, Object value) {
        if (type == VanillaTypes.ITEM_STACK || type == ForgeTypes.FLUID_STACK)
            return JemiUtil.getStack(type, value);
        if (JemiPlugin.runtime == null) return null;
        var manager = JemiPlugin.runtime.getIngredientManager();
        // Native custom ingredients are also bridged by JemiUtil now. Construct the identity
        // view directly rather than recursively converting the same chemical into another AE key.
        return new JemiStack<>(type, manager.getIngredientHelper(type), manager.getIngredientRenderer(type),
                type.getIngredientClass().cast(value));
    }
    /** Preserve native JEI ghost-drag/filter semantics after EMI changes a stack's amount. */
    public Optional<ITypedIngredient<?>> nativeTypedIngredient() {
        if (amount <= 0 || JemiPlugin.runtime == null
                || ingredient.key() instanceof AEItemKey || ingredient.key() instanceof AEFluidKey)
            return Optional.empty();
        var converted = GenericStackJeiIngredientProviders.resolveNative(new GenericStack(ingredient.key(), amount));
        return converted == null ? Optional.empty() : nativeTypedIngredient(converted.type(), converted.value());
    }
    private static <T> Optional<ITypedIngredient<?>> nativeTypedIngredient(IIngredientType<T> type, Object value) {
        return JemiPlugin.runtime.getIngredientManager()
                .createTypedIngredient(type, type.getIngredientClass().cast(value)).map(typed -> typed);
    }
    @Override public AEKeyEmiStack copy() {
        var copy = new AEKeyEmiStack(new AEKeyJeiIngredients.KeyIngredient(ingredient.key(), Math.max(1, amount)));
        copy.amount = amount;
        copy.renderer = renderer;
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
        return tooltipView().getTooltipText();
    }
    @Override public List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> getTooltip() {
        var view = tooltipView();
        view.setAmount(amount).setChance(chance).setRemainder(getRemainder());
        return view.getTooltip();
    }
    private JemiStack<?> tooltipView() {
        var value = new AEKeyJeiIngredients.KeyIngredient(ingredient.key(), Math.max(1, amount));
        if (renderer != AEKeyJeiIngredients.RENDERER && amount > 0 && JemiPlugin.runtime != null) {
            // Use the same typed ingredient that JemiSlotWidget uses to select its slot renderer.
            // A native renderer must never receive our KeyIngredient wrapper (or vice versa).
            var typed = JemiUtil.getTyped(this).orElse(null);
            if (typed != null) {
                if (typed.getType() != AEKeyJeiIngredients.TYPE) return nativeTooltipView(typed);
                return new JemiStack<>(AEKeyJeiIngredients.TYPE, AEKeyJeiIngredients.HELPER, renderer, value);
            }
        }
        return new JemiStack<>(AEKeyJeiIngredients.TYPE, AEKeyJeiIngredients.HELPER,
                AEKeyJeiIngredients.RENDERER, value);
    }
    @SuppressWarnings("unchecked")
    private <T> JemiStack<T> nativeTooltipView(ITypedIngredient<T> typed) {
        return new JemiStack<>(typed.getType(),
                JemiPlugin.runtime.getIngredientManager().getIngredientHelper(typed.getType()),
                (IIngredientRenderer<T>) (IIngredientRenderer<?>) renderer, typed.getIngredient());
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
