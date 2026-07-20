package com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Loads the optional AE2 Lightning Tech recipe bridge without linking it from core classes. */
public final class AELightningTechCompatLoader {
    private static final String PROVIDER_CLASS =
            "com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt.AELightningTechCompatProvider";

    private AELightningTechCompatLoader() {
    }

    public static List<IRecipeAdapter<?>> createAdapters() {
        Object result = invoke("createAdapters", new Class<?>[0]);
        if (!(result instanceof List<?> rawAdapters)) {
            throw new IllegalStateException("AE2 Lightning Tech compat provider returned invalid adapters");
        }
        List<IRecipeAdapter<?>> adapters = new ArrayList<>(rawAdapters.size());
        for (Object value : rawAdapters) {
            if (!(value instanceof IRecipeAdapter<?> adapter)) {
                throw new IllegalStateException("AE2 Lightning Tech compat provider returned a non-adapter value");
            }
            adapters.add(adapter);
        }
        return List.copyOf(adapters);
    }

    public static List<AdvancedAlloyFurnaceRecipe> getJeiRecipes(
            RecipeManager recipeManager, Level level) {
        Object result = invoke(
                "getJeiRecipes",
                new Class<?>[]{RecipeManager.class, Level.class},
                recipeManager,
                level);
        if (!(result instanceof List<?> rawRecipes)) {
            throw new IllegalStateException("AE2 Lightning Tech compat provider returned invalid JEI recipes");
        }
        List<AdvancedAlloyFurnaceRecipe> recipes = new ArrayList<>(rawRecipes.size());
        for (Object value : rawRecipes) {
            if (!(value instanceof AdvancedAlloyFurnaceRecipe recipe)) {
                throw new IllegalStateException("AE2 Lightning Tech compat provider returned an invalid JEI recipe");
            }
            recipes.add(recipe);
        }
        return List.copyOf(recipes);
    }

    private static Object invoke(String methodName, Class<?>[] parameterTypes, Object... arguments) {
        try {
            ClassLoader loader = AELightningTechCompatLoader.class.getClassLoader();
            Class<?> provider = Class.forName(PROVIDER_CLASS, true, loader);
            Method method = provider.getMethod(methodName, parameterTypes);
            return method.invoke(null, arguments);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            throw new IllegalStateException(
                    "AE2 Lightning Tech compat provider failed in " + methodName,
                    cause == null ? exception : cause);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException(
                    "AE2 Lightning Tech compat provider could not be loaded for " + methodName,
                    exception);
        }
    }
}
