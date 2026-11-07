package com.sorrowmist.useless.content.recipe.adapters.extremereactors;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.sorrowmist.useless.InventoryTestBootstrap;
import it.zerono.mods.extremereactors.config.Config;
import it.zerono.mods.zerocore.lib.CodeHelper;
import it.zerono.mods.zerocore.lib.recipe.ModRecipe;
import it.zerono.mods.zerocore.lib.recipe.ingredient.FluidStackRecipeIngredient;
import it.zerono.mods.zerocore.lib.recipe.ingredient.ItemStackRecipeIngredient;
import it.zerono.mods.zerocore.lib.recipe.result.FluidStackRecipeResult;
import it.zerono.mods.zerocore.lib.recipe.result.ItemStackRecipeResult;
import it.zerono.mods.zerocore.lib.tag.TagList;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingContext;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;

/** Only launcher/I/O context is mocked; recipes, ingredients, results and ER registries are native. */
final class ExtremeReactorsNativeFixtures {
    private static boolean initialized;

    private ExtremeReactorsNativeFixtures() {
    }

    static synchronized void initialize() throws Exception {
        InventoryTestBootstrap.initialize();
        if (initialized) return;
        for (String name : List.of("fluidizercontroller", "reprocessorcontroller",
                "basic_reactorcontroller", "reinforced_reactorcontroller")) {
            ResourceLocation id = new ResourceLocation("bigreactors", name);
            if (!BuiltInRegistries.ITEM.containsKey(id)) {
                InventoryTestBootstrap.registerItem(id, () -> new Item(new Item.Properties()));
            }
        }
        // Config's real static initializer only needs its launcher-owned config directory here.
        // Do not create config files in the working tree or substitute a fake energy supplier.
        try (var io = mockStatic(CodeHelper.class)) {
            io.when(() -> CodeHelper.ioCreateModConfigDirectory("extremereactors")).thenReturn(true);
            Class.forName("it.zerono.mods.extremereactors.config.Config");
        }
        var field = Config.class.getDeclaredField("s_commonSpec");
        field.setAccessible(true);
        ((ForgeConfigSpec) field.get(null)).setConfig(CommentedConfig.inMemory());
        initialized = true;
    }

    static ResourceLocation id(String name) {
        return new ResourceLocation("useless_mod_er_native_test", name);
    }

    static ItemStack mold(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("bigreactors", name)));
    }

    static <T extends ModRecipe> T recipe(Class<T> type, String name, Object... components) throws Exception {
        Class<?>[] signature = new Class<?>[components.length + 1];
        Object[] arguments = new Object[components.length + 1];
        signature[0] = ResourceLocation.class;
        arguments[0] = id(name);
        for (int i = 0; i < components.length; i++) {
            Object value = components[i];
            signature[i + 1] = value instanceof ItemStackRecipeIngredient ? ItemStackRecipeIngredient.class
                    : value instanceof FluidStackRecipeIngredient ? FluidStackRecipeIngredient.class
                    : value instanceof ItemStackRecipeResult ? ItemStackRecipeResult.class
                    : FluidStackRecipeResult.class;
            arguments[i + 1] = value;
        }
        // All four ER 2.0.84 constructors are protected. Invoke the actual class, not a
        // test subclass whose overridden getters could hide an incompatible native API.
        Constructor<T> constructor = type.getDeclaredConstructor(signature);
        constructor.setAccessible(true);
        return constructor.newInstance(arguments);
    }

    static Level levelWith(ModRecipe... recipes) {
        RecipeManager manager = mock(RecipeManager.class);
        when(manager.getRecipes()).thenReturn(List.of(recipes));
        Level level = mock(Level.class);
        when(level.getRecipeManager()).thenReturn(manager);
        return level;
    }

    static void asNativeMod(Runnable action) {
        // InternalDispatcher executes registry API calls immediately for the owning mod;
        // other callers send deferred IMC. Supply only that real Forge launcher context.
        try (var loading = mockStatic(ModLoadingContext.class)) {
            ModLoadingContext context = mock(ModLoadingContext.class);
            ModContainer container = mock(ModContainer.class);
            when(container.getModId()).thenReturn("bigreactors");
            when(context.getActiveContainer()).thenReturn(container);
            loading.when(ModLoadingContext::get).thenReturn(context);
            action.run();
        }
    }

    static <T> Map<TagKey<T>, List<Holder<T>>> tags(Registry<T> registry) {
        Map<TagKey<T>, List<Holder<T>>> result = new LinkedHashMap<>();
        registry.getTags().forEach(entry -> result.put(entry.getFirst(), entry.getSecond().stream().toList()));
        return result;
    }

    @SafeVarargs
    static <T> void bind(Registry<T> registry, TagKey<T> tag, T... values) {
        Map<TagKey<T>, List<Holder<T>>> tags = tags(registry);
        tags.put(tag, java.util.Arrays.stream(values).map(registry::wrapAsHolder).toList());
        registry.bindTags(tags);
        refreshTags();
    }

    static void refreshTags() {
        Ingredient.invalidateAll();
        // Populates ZeroCore's native TagList caches just as the game's tag reload does.
        // TagList.initialize only attaches this native callback to the launcher's Forge bus.
        // JUnit has no FML module layer; invoke the same verified 2.1.45 callback directly.
        try {
            var callback = TagList.class.getDeclaredMethod("lambda$initialize$0", TagsUpdatedEvent.class);
            callback.setAccessible(true);
            callback.invoke(null, new TagsUpdatedEvent(
                    RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY), false, false));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot run ZeroCore's native tag-reload callback", exception);
        }
    }
}
