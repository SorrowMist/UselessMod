package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import com.jerry.mekmm.api.recipes.MoreMachineRecipeTypes;
import com.jerry.mekmm.api.recipes.RecyclerRecipe;
import com.jerry.mekmm.common.config.MoreMachineConfig;
import com.jerry.mekmm.common.registries.MoreMachineBlocks;
import com.jerry.mekmm.common.tile.machine.TileEntityRecycler;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeFingerprint;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Exposes every possible MoreMachine Recycler output as a water-only recipe. */
public final class RecyclerRecipeAdapter extends MekanismSyntheticRecipeAdapter {
    private static final int WATER_AMOUNT = 1_000;
    private static final int PROCESS_TICKS = TileEntityRecycler.BASE_TICKS_REQUIRED;

    @Override
    public @Nullable ItemStack getMoldItem() {
        return new ItemStack(MoreMachineBlocks.RECYCLER.get());
    }

    @Override
    protected List<RecipeHolder<MekanismSyntheticRecipe>> createGeneratedRecipes(Level level) {
        if (level == null) {
            return List.of();
        }

        List<ItemStack> outputs = new ArrayList<>();
        for (RecipeHolder<RecyclerRecipe> holder : level.getRecipeManager().getAllRecipesFor(
                MoreMachineRecipeTypes.TYPE_RECYCLER.value())) {
            RecyclerRecipe source = holder.value();
            if (source == null || source.isIncomplete() || source.getOutputChance() <= 0) {
                continue;
            }

            for (ItemStack output : source.getChanceOutputDefinition()) {
                if (output == null || output.isEmpty() || containsOutput(outputs, output)) {
                    continue;
                }
                outputs.add(output.copy());
            }
        }

        if (outputs.isEmpty()) {
            return List.of();
        }

        List<RecipeHolder<MekanismSyntheticRecipe>> result = new ArrayList<>(outputs.size());
        for (ItemStack output : outputs) {
            ResourceLocation id = recipeId(output, level.registryAccess());
            AdvancedAlloyFurnaceRecipe converted = MekanismChemicalRecipeSupport.recipe(
                    id,
                    List.of(),
                    List.of(new SizedFluidIngredient(
                            FluidIngredient.tag(FluidTags.WATER), WATER_AMOUNT)),
                    List.of(),
                    List.of(output.copy()),
                    List.of(),
                    List.of(),
                    energyCost(),
                    PROCESS_TICKS,
                    getMoldItem());
            result.add(MekanismChemicalRecipeSupport.syntheticHolder(id, converted));
        }
        return List.copyOf(result);
    }

    private static long energyCost() {
        return MekanismChemicalRecipeSupport.saturatingMultiply(
                MoreMachineConfig.usage.recycler.get(), PROCESS_TICKS);
    }

    private static boolean containsOutput(List<ItemStack> outputs, ItemStack candidate) {
        for (ItemStack output : outputs) {
            if (output.getCount() == candidate.getCount()
                    && ItemStack.isSameItemSameComponents(output, candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 由产物内容构造配方 id。
     *
     * <p>id 不得包含产物列表下标：客户端与服务端的回收配方枚举顺序不保证一致，位置派生的 id
     * 会让同一个 id 在两端指向不同的产物，导致万象样板在服务端无法解析。判重依据必须与
     * {@link #containsOutput} 完全一致——它以「物品 + 组件 + 数量」判定产物是否相同，因此 id
     * 只含物品注册名与数量是不够的：同物品同数量而组件不同的产物会同时存在并产生相同 id。
     * 此处复用指纹模块的物品栈编码，它递归规范化组件映射，只由数据内容决定结果。</p>
     */
    private static ResourceLocation recipeId(ItemStack output, HolderLookup.Provider registries) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(output.getItem());
        String prefix = itemId == null
                ? "unregistered"
                : itemId.getNamespace() + "_" + itemId.getPath().replace('/', '_');
        return ResourceLocation.fromNamespaceAndPath(
                "mekmm", "recycler_water_" + prefix + "_" + output.getCount() + "_"
                        + AdapterUtils.stableHash(
                                AlloyFurnaceRecipeFingerprint.safeItemStack(output, registries)));
    }
}
