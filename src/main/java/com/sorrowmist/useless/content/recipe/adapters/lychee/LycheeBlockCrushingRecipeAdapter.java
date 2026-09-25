package com.sorrowmist.useless.content.recipe.adapters.lychee;

import appeng.api.stacks.AEKey;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.api.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import snownee.kiwi.recipe.SizedIngredient;
import snownee.lychee.RecipeTypes;
import snownee.lychee.contextual.Chance;
import snownee.lychee.recipes.BlockCrushingRecipe;
import snownee.lychee.util.IngredientCollection;
import snownee.lychee.util.action.PostAction;
import snownee.lychee.util.contextual.ContextualCondition;
import snownee.lychee.util.predicates.BlockPredicateExtensions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 将 Lychee 的 {@code lychee:block_crushing} 配方转换为高级合金炉配方。
 *
 * <p>Lychee 的下落方块压碎配方是数据驱动的：配方声明下落方块谓词、落地方块谓词与一组带数量的
 * 输入材料，匹配成功后执行 {@code post} 中的动作列表。KubeJS 通过
 * {@code ServerEvents.recipes} 注册的同类配方同样经由 RecipeManager 加载，因此本适配器统一
 * 枚举 {@link RecipeTypes#BLOCK_CRUSHING}，不区分配方来自数据包 JSON 还是脚本。</p>
 *
 * <p>本适配器只接纳下落方块为铁砧（或谓词为任意方块）的配方，并以铁砧作为合金炉模具，从而与
 * 神化铁砧机制保持一致。</p>
 *
 * <p>产出语义：Lychee 把每条产出的触发概率表达为 post action 上的 {@code chance} 条件，而非
 * 产出的固有属性。转换时经 {@link ExpectedOutputScaler} 把独立概率产出折算为最小确定性批次，
 * 输入、能耗与处理时间按同一批次倍数同步放大，避免把概率产出错误地当作必出。</p>
 *
 * <p>已知取舍：{@code max_repeats} 控制同一次落地重复执行配方的次数，其随机上界无法在不引入
 * 运行期随机的前提下表达为确定性配方，故此处按单次执行折算。</p>
 */
public final class LycheeBlockCrushingRecipeAdapter
        implements IRecipeAdapter<BlockCrushingRecipe> {

    @Override
    public String sourceId() {
        return RecipeSourceIds.LYCHEE;
    }

    @Override
    public Class<BlockCrushingRecipe> getRecipeClass() {
        return BlockCrushingRecipe.class;
    }

    /** 模具统一为铁砧，仅接管铁砧下落压碎配方。 */
    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(Items.ANVIL);
    }

    @Override
    public List<RecipeHolder<BlockCrushingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }

        List<RecipeHolder<BlockCrushingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<BlockCrushingRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(RecipeTypes.BLOCK_CRUSHING)) {
            AdvancedAlloyFurnaceRecipe converted = convertRecipe(holder);
            if (converted == null) {
                continue;
            }
            if (AdapterUtils.matchesRequired(mergedInputs, requiredCounts(converted.inputs()))) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<BlockCrushingRecipe> holder, Level level) {
        AdvancedAlloyFurnaceRecipe converted = convertRecipe(holder);
        return converted == null ? List.of() : List.of(converted);
    }

    @Nullable
    private AdvancedAlloyFurnaceRecipe convertRecipe(@Nullable RecipeHolder<BlockCrushingRecipe> holder) {
        if (holder == null || holder.value() == null) {
            return null;
        }

        BlockCrushingRecipe source = holder.value();
        if (!isAnvilFallingBlock(source)) {
            return null;
        }

        List<CountedIngredient> inputs = toCountedIngredients(source.ingredientCollection());
        if (inputs.isEmpty()) {
            return null;
        }

        List<ItemStack> deterministic = new ArrayList<>();
        List<ExpectedOutputScaler.WeightedItemOutput> weighted = new ArrayList<>();
        collectOutputs(source, deterministic, weighted);
        if (deterministic.isEmpty() && weighted.isEmpty()) {
            return null;
        }

        ItemStack mold = getMoldItem();
        if (mold.isEmpty()) {
            return null;
        }

        Optional<ExpectedOutputScaler.ScaledOutputs> scaled = ExpectedOutputScaler.scale(weighted);
        if (scaled.isEmpty()) {
            return null;
        }
        int operations = scaled.get().operations();

        List<CountedIngredient> scaledInputs = scaleInputs(inputs, operations);
        if (scaledInputs == null) {
            return null;
        }

        List<ItemStack> outputs = new ArrayList<>();
        for (ItemStack stack : deterministic) {
            if (!addOutput(outputs, stack, (long) stack.getCount() * operations)) {
                return null;
            }
        }
        for (ItemStack stack : scaled.get().outputs()) {
            if (!addOutput(outputs, stack, stack.getCount())) {
                return null;
            }
        }
        if (outputs.isEmpty()) {
            return null;
        }

        final long energy;
        final int processTime;
        try {
            energy = Math.multiplyExact(AdapterUtils.DEFAULT_ENERGY, (long) operations);
            long time = Math.multiplyExact((long) AdapterUtils.DEFAULT_PROCESS_TIME, operations);
            if (time <= 0 || time > Integer.MAX_VALUE) {
                return null;
            }
            processTime = (int) time;
        } catch (ArithmeticException exception) {
            return null;
        }

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                scaledInputs,
                List.of(),
                List.of(),
                outputs,
                List.of(),
                List.of(),
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(mold),
                AlloyFurnaceMode.NORMAL);
    }

    /**
     * 判定配方的下落方块谓词是否覆盖铁砧。
     *
     * <p>谓词为“任意方块”时同样接受：这类配方本就不限定下落方块，而合金炉通过铁砧模具
     * 表达触发条件，转换后语义仍然成立。</p>
     */
    private static boolean isAnvilFallingBlock(BlockCrushingRecipe recipe) {
        if (BlockPredicateExtensions.isAny(recipe.blockPredicate())) {
            return true;
        }
        for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(BlockTags.ANVIL)) {
            for (BlockState state : holder.value().getStateDefinition().getPossibleStates()) {
                if (recipe.matchesFallingBlock(state, null)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 按产出动作收集结果，并按动作自身的概率条件分流。
     *
     * <p>概率为 1 的产出进入确定性列表，其余进入带权列表交由 {@link ExpectedOutputScaler}
     * 折算；概率为 0 的产出直接丢弃。</p>
     */
    private static void collectOutputs(
            BlockCrushingRecipe recipe,
            List<ItemStack> deterministic,
            List<ExpectedOutputScaler.WeightedItemOutput> weighted) {
        for (PostAction action : recipe.postActions()) {
            if (action == null) {
                continue;
            }
            double chance = chanceOf(action);
            if (chance <= 0.0d) {
                continue;
            }
            for (ItemStack stack : action.getOutputItems()) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                if (chance >= 1.0d) {
                    deterministic.add(stack.copy());
                } else {
                    weighted.add(new ExpectedOutputScaler.WeightedItemOutput(
                            stack.copy(), stack.getCount(), stack.getCount(), chance));
                }
            }
        }
    }

    /**
     * 汇总动作条件中的概率约束。
     *
     * <p>同一动作上的多个 {@link Chance} 条件按逻辑与语义叠乘；未声明概率约束时视为必出。</p>
     */
    private static double chanceOf(PostAction action) {
        double chance = 1.0d;
        for (ContextualCondition condition : action.conditions()) {
            if (condition instanceof Chance chanced) {
                chance *= Math.max(0.0d, Math.min(1.0d, chanced.chance()));
            }
        }
        return chance;
    }

    private static List<CountedIngredient> toCountedIngredients(@Nullable IngredientCollection collection) {
        if (collection == null || collection.isEmpty()) {
            return List.of();
        }

        Map<Ingredient, Long> merged = new LinkedHashMap<>();
        for (SizedIngredient sized : collection.ingredients()) {
            if (sized == null || sized.ingredient() == null || sized.ingredient().isEmpty()
                    || sized.count() <= 0) {
                continue;
            }
            AdapterUtils.mergeIngredient(merged, sized.ingredient(), sized.count());
        }
        return merged.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** 按批次倍数放大输入数量；溢出或结果为空时返回 {@code null}。 */
    @Nullable
    private static List<CountedIngredient> scaleInputs(List<CountedIngredient> inputs, int operations) {
        if (operations <= 0) {
            return null;
        }
        List<CountedIngredient> scaled = new ArrayList<>(inputs.size());
        for (CountedIngredient input : inputs) {
            if (input == null || input.ingredient() == null || input.ingredient().isEmpty()
                    || input.count() <= 0) {
                continue;
            }
            final long count;
            try {
                count = Math.multiplyExact(input.count(), (long) operations);
            } catch (ArithmeticException exception) {
                return null;
            }
            scaled.add(new CountedIngredient(input.ingredient(), count));
        }
        return scaled.isEmpty() ? null : List.copyOf(scaled);
    }

    /** 合并同物品产出；数量溢出时返回 {@code false}。 */
    private static boolean addOutput(List<ItemStack> outputs, ItemStack stack, long amount) {
        if (stack == null || stack.isEmpty() || amount <= 0) {
            return true;
        }
        for (ItemStack existing : outputs) {
            if (!ItemStack.isSameItemSameComponents(existing, stack)) {
                continue;
            }
            long merged = (long) existing.getCount() + amount;
            if (merged > Integer.MAX_VALUE) {
                return false;
            }
            existing.setCount((int) merged);
            return true;
        }
        if (amount > Integer.MAX_VALUE) {
            return false;
        }
        ItemStack copy = stack.copy();
        copy.setCount((int) amount);
        outputs.add(copy);
        return true;
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
}
