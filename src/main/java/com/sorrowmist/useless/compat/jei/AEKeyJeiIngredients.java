package com.sorrowmist.useless.compat.jei;

import com.sorrowmist.useless.utils.RateLimitedLogger;

import appeng.api.client.AEKeyRendering;
import appeng.api.integrations.jei.IngredientConverter;
import appeng.api.integrations.jei.IngredientConverters;
import appeng.api.stacks.*;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.compat.forge1201.jei.AEKeyBookmarkCatalog;
import mezz.jei.api.ingredients.*;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import java.nio.file.Path;
import java.util.*;

/** Actual JEI ingredient for keys with no native converter (e.g. Applied Flux energy).
 * Both recipe pages and bookmarks use the AE key renderer/helper, never a fake item.
 */
public final class AEKeyJeiIngredients {
    public record KeyIngredient(AEKey key, long amount) {
        public KeyIngredient { Objects.requireNonNull(key); if (amount <= 0) throw new IllegalArgumentException("amount"); }
        public GenericStack stack() { return new GenericStack(key, amount); }
    }
    public static final IIngredientType<KeyIngredient> TYPE = new IIngredientType<>() {
        @Override public Class<KeyIngredient> getIngredientClass() { return KeyIngredient.class; }
        @Override public String getUid() { return "useless_mod:ae_key"; }
    };
    public static final Helper HELPER = new Helper();
    public static final Renderer RENDERER = new Renderer();
    public static final IngredientConverter<KeyIngredient> CONVERTER = new IngredientConverter<>() {
        @Override public IIngredientType<KeyIngredient> getIngredientType() { return TYPE; }
        @Override public KeyIngredient getIngredientFromStack(GenericStack stack) {
            if (stack == null || stack.amount() <= 0 || GenericStackJeiIngredientProviders.resolveNative(stack) != null)
                return null;
            return remember(stack);
        }
        @Override public GenericStack getStackFromIngredient(KeyIngredient value) { return value.stack(); }
    };
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final AEKeyBookmarkCatalog CATALOG = new AEKeyBookmarkCatalog();
    private static @Nullable Path catalogFile;
    private static @Nullable IIngredientManager manager;
    private static final Set<String> registered = new HashSet<>();
    private AEKeyJeiIngredients() {}

    public static void register(IModIngredientRegistration registration, Path file) {
        manager = null;
        catalogFile = file;
        try { CATALOG.load(file); }
        catch (java.io.IOException exception) {
            // Keep the unreadable original intact rather than replacing user state with an empty cache.
            catalogFile = null;
            RateLimitedLogger.warn(LOGGER, "Could not read AE key bookmark catalogue {}; automatic saves disabled", file, exception);
        }
        var values = CATALOG.decoded().stream().map(s -> new KeyIngredient(s.what(), 1)).toList();
        registered.clear(); values.forEach(value -> registered.add(HELPER.getUniqueId(value, UidContext.Ingredient)));
        registration.register(TYPE, values, HELPER, RENDERER);
        IngredientConverters.register(CONVERTER);
    }
    public static void setRuntime(@Nullable IIngredientManager value) {
        manager = value;
        if (value == null) return;
        var additions = new ArrayList<KeyIngredient>();
        for (var stack : CATALOG.decoded()) {
            var ingredient = new KeyIngredient(stack.what(), 1);
            if (registered.add(HELPER.getUniqueId(ingredient, UidContext.Ingredient))) additions.add(ingredient);
        }
        if (!additions.isEmpty()) value.addIngredientsAtRuntime(TYPE, additions);
    }
    public static GenericStackJeiIngredientProvider.Ingredient resolve(GenericStack stack) {
        return new GenericStackJeiIngredientProvider.Ingredient(TYPE, remember(stack));
    }
    private static KeyIngredient remember(GenericStack stack) {
        var value = new KeyIngredient(stack.what(), stack.amount());
        if (CATALOG.remember(value.key()) && catalogFile != null) {
            try { CATALOG.save(catalogFile); }
            catch (java.io.IOException exception) { RateLimitedLogger.warn(LOGGER, "Could not persist AE key bookmarks {}", catalogFile, exception); }
        }
        String uid = HELPER.getUniqueId(value, UidContext.Ingredient);
        if (manager != null && registered.add(uid)) manager.addIngredientsAtRuntime(TYPE, List.of(HELPER.normalizeIngredient(value)));
        return value;
    }
    public static final class Helper implements IIngredientHelper<KeyIngredient> {
        @Override public IIngredientType<KeyIngredient> getIngredientType() { return TYPE; }
        @Override public String getDisplayName(KeyIngredient value) { return value.key().getDisplayName().getString(); }
        @Override public String getUniqueId(KeyIngredient value, UidContext context) { return AEKeyBookmarkCatalog.uid(value.key()); }
        @Override public ResourceLocation getResourceLocation(KeyIngredient value) { return value.key().getId(); }
        @Override public String getDisplayModId(KeyIngredient value) { return value.key().getModId(); }
        @Override public KeyIngredient copyIngredient(KeyIngredient value) { return new KeyIngredient(value.key(), value.amount()); }
        @Override public KeyIngredient normalizeIngredient(KeyIngredient value) { return new KeyIngredient(value.key(), 1); }
        @Override public long getAmount(KeyIngredient value) { return value.amount(); }
        @Override public KeyIngredient copyWithAmount(KeyIngredient value, long amount) { return new KeyIngredient(value.key(), Math.max(1, amount)); }
        @Override public boolean isValidIngredient(KeyIngredient value) { return value != null && value.key() != null && value.amount() > 0; }
        @Override public String getErrorInfo(KeyIngredient value) { return value == null ? "null AEKey" : getUniqueId(value, UidContext.Ingredient); }
    }
    public static final class Renderer implements IIngredientRenderer<KeyIngredient> {
        @Override public void render(GuiGraphics gui, KeyIngredient value) {
            if (value.key().getId().toString().equals("appflux:gteu")) {
                // Applied Flux 1.3.2 ships no GTEU sprite. Draw an unambiguous fallback.
                gui.fill(1, 1, 15, 15, 0xff25354b);
                gui.fill(7, 2, 12, 6, 0xffffcf40);
                gui.fill(5, 6, 10, 10, 0xffffcf40);
                gui.fill(4, 10, 8, 14, 0xffffcf40);
            } else if (AEKeyRendering.get(value.key().getType()) != null) {
                AEKeyRendering.drawInGui(Minecraft.getInstance(), gui, 0, 0, value.key());
            } else {
                gui.drawString(Minecraft.getInstance().font, "?", 4, 4, 0xffffff, false);
            }
        }
        @Override public List<Component> getTooltip(KeyIngredient value, TooltipFlag flag) {
            var lines = AEKeyRendering.get(value.key().getType()) == null
                    ? new ArrayList<>(List.of(value.key().getDisplayName()))
                    : new ArrayList<>(AEKeyRendering.getTooltip(value.key()));
            if (value.amount() > 1) lines.add(Component.literal(value.key().formatAmount(value.amount(), AmountFormat.FULL)));
            return lines;
        }
    }
}
