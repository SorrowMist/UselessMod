package com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt;

import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * AE2 Lightning Tech 牛排工具闪电配方适配器
 * <p>
 * 使用牛排工具（EndlessBeafItem，任意变体/状态）作为模具，额外提供两个配方：
 * - 输入 1mb 水 → 输出 1 个高压闪电到 AE 网络
 * - 输入 1mb 熔岩 → 输出 1 个极高压闪电到 AE 网络
 * <p>
 * 牛排工具没有固定单一物品，作为 fallback 适配器通过 {@link #matchesMold(ItemStack)} 动态判断。
 */
public class SteakLightningRecipeAdapter implements IRecipeAdapter<SteakLightningRecipeAdapter.SteakLightningDummyRecipe> {

    private static final int ENERGY = 2000;
    private static final int PROCESS_TIME = 100;

    private final AdvancedAlloyFurnaceRecipe waterRecipe;
    private final AdvancedAlloyFurnaceRecipe lavaRecipe;

    public SteakLightningRecipeAdapter() {
        Ingredient moldIngredient = createSteakMold();

        this.waterRecipe = new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath("ae2lt", "steak_water_high_voltage_lightning"),
                List.<CountedIngredient>of(),
                List.of(new FluidStack(Fluids.WATER, 1)),
                List.<GenericStack>of(),
                List.<ItemStack>of(),
                List.<FluidStack>of(),
                List.of(new GenericStack(LightningKey.HIGH_VOLTAGE, 1)),
                ENERGY,
                PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                moldIngredient,
                AlloyFurnaceMode.NORMAL
        );

        this.lavaRecipe = new AdvancedAlloyFurnaceRecipe(
                ResourceLocation.fromNamespaceAndPath("ae2lt", "steak_lava_extreme_high_voltage_lightning"),
                List.<CountedIngredient>of(),
                List.of(new FluidStack(Fluids.LAVA, 1)),
                List.<GenericStack>of(),
                List.<ItemStack>of(),
                List.<FluidStack>of(),
                List.of(new GenericStack(LightningKey.EXTREME_HIGH_VOLTAGE, 1)),
                ENERGY,
                PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                moldIngredient,
                AlloyFurnaceMode.NORMAL
        );
    }

    private static Ingredient createSteakMold() {
        return Ingredient.of(
                ModItems.ENDLESS_BEAF_ITEM.get(),
                ModItems.ENDLESS_BEAF_WRENCH.get(),
                ModItems.ENDLESS_BEAF_SCREWDRIVER.get(),
                ModItems.ENDLESS_BEAF_MALLET.get(),
                ModItems.ENDLESS_BEAF_CROWBAR.get(),
                ModItems.ENDLESS_BEAF_HAMMER.get()
        );
    }

    public List<AdvancedAlloyFurnaceRecipe> getAllRecipes() {
        return List.of(waterRecipe, lavaRecipe);
    }

    @Override
    public Class<SteakLightningDummyRecipe> getRecipeClass() {
        return SteakLightningDummyRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null; // 牛排工具有多个变体，作为 fallback 适配器动态匹配
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return mold != null && !mold.isEmpty() && mold.getItem() instanceof EndlessBeafItem;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<SteakLightningDummyRecipe> holder, Level level) {
        return List.of(holder.value().convertedRecipe);
    }

    @Override
    @Nullable
    public AdvancedAlloyFurnaceRecipe convert(RecipeHolder<SteakLightningDummyRecipe> holder, Level level) {
        return holder.value().convertedRecipe;
    }

    @Override
    @Nullable
    public List<RecipeHolder<SteakLightningDummyRecipe>> findMatchingRecipes(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null) return List.of();
        if (!matchesMold(mold)) return List.of();
        if (mergedFluids == null || mergedFluids.isEmpty()) return List.of();

        List<RecipeHolder<SteakLightningDummyRecipe>> matches = new java.util.ArrayList<>();
        if (hasFluid(mergedFluids, Fluids.WATER)) {
            matches.add(new RecipeHolder<>(waterRecipe.id(), new SteakLightningDummyRecipe(waterRecipe)));
        }
        if (hasFluid(mergedFluids, Fluids.LAVA)) {
            matches.add(new RecipeHolder<>(lavaRecipe.id(), new SteakLightningDummyRecipe(lavaRecipe)));
        }
        return matches;
    }

    private static boolean hasFluid(Map<FluidStack, Long> mergedFluids, Fluid fluid) {
        for (Map.Entry<FluidStack, Long> entry : mergedFluids.entrySet()) {
            if (entry.getKey().getFluid().isSame(fluid) && entry.getValue() >= 1) {
                return true;
            }
        }
        return false;
    }

    public static class SteakLightningDummyRecipe implements Recipe<RecipeInput> {
        final AdvancedAlloyFurnaceRecipe convertedRecipe;

        SteakLightningDummyRecipe(AdvancedAlloyFurnaceRecipe r) { this.convertedRecipe = r; }

        @Override public boolean matches(RecipeInput input, Level level) { return false; }
        @Override public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) { return ItemStack.EMPTY; }
        @Override public boolean canCraftInDimensions(int w, int h) { return false; }
        @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return ItemStack.EMPTY; }
        @Override public RecipeSerializer<?> getSerializer() { return null; }
        @Override public RecipeType<?> getType() { return null; }
    }
}
