package com.sorrowmist.useless.content.recipe.adapters.apotheosis;

import appeng.api.stacks.AEKey;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.api.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将神化的铁砧砸碎宝石机制转换为高级合金炉配方。
 *
 * <p>神化并未把该机制实现为数据驱动配方，而是在 {@code AdventureEvents#gemSmashing} 中监听
 * 铁砧落地事件，扫描落点方块范围内的掉落物实体，把宝石原地替换为等量的宝石粉。因此本适配器
 * 以合成配方的形式表达这条固定转换，模具统一为铁砧。</p>
 *
 * <p>产出语义与原始机制保持 1:1：输入若干宝石产出等量宝石粉，不做数量放大。</p>
 */
public final class ApotheosisAnvilSmashingRecipeAdapter
        implements IRecipeAdapter<ApotheosisAnvilSmashingRecipeAdapter.AnvilSmashingRecipe> {

    /** 神化宝石的物品 id。 */
    private static final ResourceLocation GEM_ID =
            ResourceLocation.fromNamespaceAndPath("apotheosis", "gem");
    /** 神化宝石粉的物品 id。 */
    private static final ResourceLocation GEM_DUST_ID =
            ResourceLocation.fromNamespaceAndPath("apotheosis", "gem_dust");
    /** 合成配方使用的稳定标识。 */
    private static final ResourceLocation SYNTHETIC_ID =
            ResourceLocation.fromNamespaceAndPath("apotheosis", "anvil_gem_smashing_converted");

    @Override
    public String sourceId() {
        return RecipeSourceIds.APOTHEOSIS;
    }

    @Override
    public Class<AnvilSmashingRecipe> getRecipeClass() {
        return AnvilSmashingRecipe.class;
    }

    /** 模具统一为铁砧，与原始机制的触发方块一致。 */
    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(Items.ANVIL);
    }

    @Override
    public List<RecipeHolder<AnvilSmashingRecipe>> getGeneratedRecipes(Level level) {
        Item gemDust = BuiltInRegistries.ITEM.getOptional(GEM_DUST_ID).orElse(null);
        if (gemDust == null) {
            return List.of();
        }

        AdvancedAlloyFurnaceRecipe converted = new AdvancedAlloyFurnaceRecipe(
                SYNTHETIC_ID,
                List.of(new CountedIngredient(ApotheosisGemIngredient.of(), 1L)),
                List.of(),
                List.of(),
                List.of(new ItemStack(gemDust)),
                List.of(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL);
        return List.of(new RecipeHolder<>(SYNTHETIC_ID, new AnvilSmashingRecipe(converted)));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<AnvilSmashingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        AdvancedAlloyFurnaceRecipe converted = holder.value().convertedRecipe();
        return converted == null ? List.of() : List.of(converted);
    }

    @Override
    public List<RecipeHolder<AnvilSmashingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }

        List<RecipeHolder<AnvilSmashingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<AnvilSmashingRecipe> holder : getGeneratedRecipes(level)) {
            List<AdvancedAlloyFurnaceRecipe> converted = convertAll(holder, level);
            if (converted.isEmpty()) {
                continue;
            }
            if (AdapterUtils.matchesRequired(mergedInputs, requiredCounts(converted.getFirst().inputs()))) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    private static Map<Ingredient, Long> requiredCounts(List<CountedIngredient> inputs) {
        Map<Ingredient, Long> required = new LinkedHashMap<>();
        for (CountedIngredient input : inputs) {
            if (input == null || input.ingredient() == null || input.ingredient().isEmpty()
                    || input.count() <= 0) {
                continue;
            }
            AdapterUtils.mergeIngredient(required, input.ingredient(), input.count());
        }
        return required;
    }

    /**
     * 承载转换结果的合成配方。
     *
     * <p>该配方不参与原版合成匹配，仅作为适配器向配方管理器传递转换产物的载体；
     * 真正的匹配与产出由 {@link AdvancedAlloyFurnaceRecipe} 决定。</p>
     */
    public static final class AnvilSmashingRecipe implements Recipe<RecipeInput> {
        private final AdvancedAlloyFurnaceRecipe convertedRecipe;

        AnvilSmashingRecipe(AdvancedAlloyFurnaceRecipe convertedRecipe) {
            this.convertedRecipe = convertedRecipe;
        }

        public AdvancedAlloyFurnaceRecipe convertedRecipe() {
            return convertedRecipe;
        }

        @Override
        public boolean matches(RecipeInput input, Level level) {
            return false;
        }

        @Override
        public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return false;
        }

        @Override
        public ItemStack getResultItem(HolderLookup.Provider registries) {
            return ItemStack.EMPTY;
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return null;
        }

        @Override
        public RecipeType<?> getType() {
            return null;
        }
    }
}
