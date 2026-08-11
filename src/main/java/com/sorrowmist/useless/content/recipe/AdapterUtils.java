package com.sorrowmist.useless.content.recipe;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.core.component.RitualBlueprintPentacles;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配方适配器工具类
 * <p>
 * 提供适配器实现中常用的静态工具方法，消除重复代码。
 */
public class AdapterUtils {

    /** 默认基础处理时间（ticks） */
    public static final int DEFAULT_PROCESS_TIME = 20;
    /** 默认基础能量消耗（FE） */
    public static final int DEFAULT_ENERGY = 2000;

    public static final int MEKANISM_BASE_TICKS_REQUIRED = 200;
    public static final int MEKANISM_ENRICHMENT_CHAMBER_TICKS_REQUIRED = MEKANISM_BASE_TICKS_REQUIRED;
    public static final int MEKANISM_METALLURGIC_INFUSER_TICKS_REQUIRED = MEKANISM_BASE_TICKS_REQUIRED;
    public static final int MEKANISM_ENRICHMENT_CHAMBER_ENERGY_PER_TICK = 50;
    public static final int MEKANISM_METALLURGIC_INFUSER_ENERGY_PER_TICK = 50;

    /** AE 能量到 FE 的转换系数 */
    public static final int AE_TO_FE_CONVERSION = 2;
    /** ExtendedAE 设备能量乘数 */
    public static final int EXTENDEDAE_ENERGY_MULTIPLIER = 10;
    /** AE2CS 配方最低能量消耗（FE） */
    public static final int AE2CS_MIN_ENERGY = DEFAULT_ENERGY / 4;
    /** AdvancedAE 反应仓默认处理时间（ticks） */
    public static final int ADVANCEDAE_REACTION_CHAMBER_PROCESS_TIME = 100;
    /** Industrial Foregoing 溶解成型机基础能耗 (FE/tick) */
    public static final int IF_BASE_ENERGY_PER_TICK = 90;
    /** Industrial Foregoing 能量倍率 */
    public static final int IF_ENERGY_MULTIPLIER = 4;

    /**
     * 生成转换后的配方 ID（追加 _converted 后缀）
     *
     * @param originalId 原始配方 ID
     * @return 转换后的配方 ID
     */
    public static ResourceLocation convertedId(ResourceLocation originalId) {
        return ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(),
                originalId.getPath() + "_converted"
        );
    }

    /**
     * 将 ItemStack 转换为 Ingredient 模具
     *
     * @param moldItem 模具物品
     * @return 可用于配方模具字段的 Ingredient，空输入返回 Ingredient.EMPTY
     */
    public static Ingredient toMoldIngredient(@Nullable ItemStack moldItem) {
        if (moldItem == null || moldItem.isEmpty()) return Ingredient.EMPTY;
        return Ingredient.of(moldItem);
    }

    /** Matches normal molds exactly and ritual blueprints by required pentacle inclusion. */
    public static boolean matchesMold(@Nullable Ingredient requiredMold, @Nullable ItemStack actualMold) {
        if (requiredMold == null || requiredMold.isEmpty()) return true;
        if (actualMold == null || actualMold.isEmpty()) return false;
        if (requiredMold.test(actualMold)) return true;

        RitualBlueprintPentacles actualPentacles = actualMold.get(UComponents.RITUAL_BLUEPRINT_PENTACLE.get());
        if (actualPentacles == null || actualPentacles.isEmpty()) return false;
        for (ItemStack representative : requiredMold.getItems()) {
            RitualBlueprintPentacles requiredPentacles = representative.get(
                    UComponents.RITUAL_BLUEPRINT_PENTACLE.get());
            if (requiredPentacles != null && !requiredPentacles.isEmpty()
                    && ItemStack.isSameItem(representative, actualMold)
                    && actualPentacles.containsAll(requiredPentacles)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将 AE2CS 原配方能量转换为合金炉能量消耗。
     *
     * @param originalEnergy 原配方 energy_cost
     * @return 转换后的 FE 消耗
     */
    public static int ae2csEnergyCost(int originalEnergy) {
        return Math.max(originalEnergy * AE_TO_FE_CONVERSION, AE2CS_MIN_ENERGY);
    }

    public static int mekanismEnrichmentChamberEnergyCost(long operations) {
        return mekanismEnergyCost(MEKANISM_ENRICHMENT_CHAMBER_ENERGY_PER_TICK, MEKANISM_ENRICHMENT_CHAMBER_TICKS_REQUIRED, operations);
    }

    public static int mekanismEnrichmentChamberProcessTime(long operations) {
        return mekanismProcessTime(MEKANISM_ENRICHMENT_CHAMBER_TICKS_REQUIRED, operations);
    }

    public static int mekanismMetallurgicInfuserEnergyCost(long operations) {
        return mekanismEnergyCost(MEKANISM_METALLURGIC_INFUSER_ENERGY_PER_TICK, MEKANISM_METALLURGIC_INFUSER_TICKS_REQUIRED, operations);
    }

    public static int mekanismMetallurgicInfuserProcessTime(long operations) {
        return mekanismProcessTime(MEKANISM_METALLURGIC_INFUSER_TICKS_REQUIRED, operations);
    }

    public static int mekanismEnergyCost(long energyPerTick, long operations) {
        return mekanismEnergyCost(energyPerTick, MEKANISM_BASE_TICKS_REQUIRED, operations);
    }

    public static int mekanismEnergyCost(long energyPerTick, long ticksRequired, long operations) {
        return safeInt(energyPerTick * ticksRequired * operations);
    }

    public static int mekanismProcessTime(long operations) {
        return mekanismProcessTime(MEKANISM_BASE_TICKS_REQUIRED, operations);
    }

    public static int mekanismProcessTime(long ticksRequired, long operations) {
        return safeInt(ticksRequired * operations);
    }

    public static int safeInt(long value) {
        if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) Math.max(0, value);
    }

    public static long gcd(long a, long b) {
        while (b != 0) {
            long temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }

    /**
     * 将 ItemStack 列表按 Ingredient 合并，返回合并后的计数 Map
     * <p>
     * 用于 AlloyFurnaceRecipeManager 层统一合并输入。相同物品会合并到已有 Ingredient 项中，数量为 ItemStack count 总和。
     *
     * @param inputs 原始物品输入列表
     * @return 合并后的 Ingredient 到数量映射
     */
    public static Map<Ingredient, Long> mergeInputs(List<ItemStack> inputs) {
        Map<Ingredient, Long> merged = new LinkedHashMap<>();
        if (inputs == null) return merged;
        for (ItemStack stack : inputs) {
            if (stack == null || stack.isEmpty()) continue;
            boolean found = false;
            for (Map.Entry<Ingredient, Long> entry : merged.entrySet()) {
                ItemStack[] representatives = entry.getKey().getItems();
                if (representatives.length == 1
                        && ItemStack.isSameItemSameComponents(representatives[0], stack)) {
                    merged.put(entry.getKey(), entry.getValue() + stack.getCount());
                    found = true;
                    break;
                }
            }
            if (!found) {
                // Vanilla Ingredient.of(ItemStack) ignores components in equals(), so it cannot
                // safely be used as a map key for two component-distinct stacks of the same item.
                merged.put(DataComponentIngredient.of(true, stack.copyWithCount(1)), (long) stack.getCount());
            }
        }
        return merged;
    }

    /**
     * 将 FluidStack 列表按流体类型合并，返回合并后的计数 Map
     * <p>
     * 用于 AlloyFurnaceRecipeManager 层统一合并流体输入。流体类型与组件相同的 FluidStack 会合并数量。
     *
     * @param fluidInputs 原始流体输入列表
     * @return 合并后的 FluidStack 到数量映射
     */
    public static Map<FluidStack, Long> mergeFluids(List<FluidStack> fluidInputs) {
        if (fluidInputs == null || fluidInputs.isEmpty()) return Map.of();
        Map<FluidStack, Long> merged = new LinkedHashMap<>();
        for (FluidStack stack : fluidInputs) {
            if (stack.isEmpty()) continue;
            boolean found = false;
            for (Map.Entry<FluidStack, Long> entry : merged.entrySet()) {
                if (FluidStack.isSameFluidSameComponents(entry.getKey(), stack)) {
                    merged.put(entry.getKey(), entry.getValue() + stack.getAmount());
                    found = true;
                    break;
                }
            }
            if (!found) {
                merged.put(stack.copy(), (long) stack.getAmount());
            }
        }
        return merged;
    }

    /** Converts a concrete fluid stack without dropping its component predicate. */
    public static SizedFluidIngredient toSizedFluidIngredient(@Nullable FluidStack stack) {
        if (stack == null || stack.isEmpty() || stack.getAmount() <= 0) return null;
        FluidIngredient ingredient = stack.getComponents().isEmpty()
                ? FluidIngredient.single(stack)
                : DataComponentFluidIngredient.of(true, stack);
        return new SizedFluidIngredient(ingredient, stack.getAmount());
    }

    /** Expands an aggregated fluid map into non-mutating supplies for the common allocator. */
    public static List<FluidStack> fluidSupplies(@Nullable Map<FluidStack, Long> mergedFluids) {
        if (mergedFluids == null || mergedFluids.isEmpty()) return List.of();
        List<FluidStack> supplies = new java.util.ArrayList<>(mergedFluids.size());
        for (Map.Entry<FluidStack, Long> entry : mergedFluids.entrySet()) {
            FluidStack stack = entry.getKey();
            long amount = entry.getValue() == null ? 0L : entry.getValue();
            if (stack == null || stack.isEmpty() || amount <= 0L) continue;
            FluidStack copy = stack.copy();
            copy.setAmount(amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount);
            supplies.add(copy);
        }
        return supplies;
    }

    public static boolean matchesFluidIngredients(
            @Nullable Map<FluidStack, Long> mergedFluids,
            List<SizedFluidIngredient> requirements) {
        if (requirements == null || requirements.isEmpty()) return true;
        return FluidIngredientAllocator.matches(requirements, mergedFluids, 1L);
    }

    /**
     * 将 GenericStack 列表按 AEKey 合并，返回合并后的数量 Map。
     *
     * @param keyInputs 原始 AEKey 输入列表
     * @return 合并后的 AEKey 到数量映射
     */
    public static Map<AEKey, Long> mergeKeys(List<GenericStack> keyInputs) {
        if (keyInputs == null || keyInputs.isEmpty()) return Map.of();
        Map<AEKey, Long> merged = new LinkedHashMap<>();
        for (GenericStack stack : keyInputs) {
            if (stack == null || stack.what() == null || stack.amount() <= 0) continue;
            merged.merge(stack.what(), stack.amount(), Long::sum);
        }
        return merged;
    }

    /**
     * 检查已合并 AEKey 输入是否满足配方需求。
     *
     * @param mergedKeys 已合并的 AEKey 输入
     * @param requiredKeys 配方需求 AEKey 列表
     * @return 是否满足全部 AEKey 需求
     */
    public static boolean matchesKeyRequirements(Map<AEKey, Long> mergedKeys, List<GenericStack> requiredKeys) {
        if (requiredKeys == null || requiredKeys.isEmpty()) return true;
        if (mergedKeys == null || mergedKeys.isEmpty()) return false;
        for (GenericStack required : requiredKeys) {
            if (required == null || required.what() == null || required.amount() <= 0) continue;
            long found = mergedKeys.getOrDefault(required.what(), 0L);
            if (found < required.amount()) return false;
        }
        return true;
    }

    /**
     * 检查合并后的输入是否满足配方需求
     * <p>
     * 适用于多输入配方。每个需求 Ingredient 都会在 mergedInputs 中统计可匹配的总数量。
     *
     * @param mergedInputs 已合并的实际输入
     * @param requiredCounts 配方需求 Ingredient 到数量映射
     * @return 实际输入是否满足全部需求
     */
    public static boolean matchesRequired(Map<Ingredient, Long> mergedInputs, Map<Ingredient, Long> requiredCounts) {
        return ItemIngredientAllocator.matches(mergedInputs, requiredCounts);
    }

    /**
     * 检查已合并输入中是否存在至少 1 个匹配指定 Ingredient 的物品。
     *
     * @param mergedInputs 已合并的实际输入
     * @param required 需要匹配的 Ingredient
     * @return 是否存在匹配输入
     */
    public static boolean hasMatchingIngredient(Map<Ingredient, Long> mergedInputs, Ingredient required) {
        return countMatchingIngredient(mergedInputs, required) > 0;
    }

    /**
     * 检查已合并输入中是否存在足够数量的指定 Ingredient。
     *
     * @param mergedInputs 已合并的实际输入
     * @param required 需要匹配的 Ingredient
     * @param count 需求数量
     * @return 是否存在足够数量的匹配输入
     */
    public static boolean hasMatchingIngredient(Map<Ingredient, Long> mergedInputs, Ingredient required, long count) {
        return countMatchingIngredient(mergedInputs, required) >= count;
    }

    /**
     * 统计已合并输入中可匹配指定 Ingredient 的总数量。
     * <p>
     * 同时使用 areIngredientsEqual 和 ingredientMatches：前者判断两个 Ingredient 的可选项集合是否完全等价，后者判断输入 Ingredient 的代表物品是否能被需求 Ingredient 接受。
     * 这样可以覆盖“输入被 mergeInputs 转成 Ingredient.of(具体 ItemStack)，需求是 tag 或多候选 Ingredient”的场景。
     *
     * @param mergedInputs 已合并的实际输入
     * @param required 需要匹配的 Ingredient
     * @return 匹配到的总数量
     */
    public static long countMatchingIngredient(Map<Ingredient, Long> mergedInputs, Ingredient required) {
        long found = 0;
        for (Map.Entry<Ingredient, Long> input : mergedInputs.entrySet()) {
            if (areIngredientsEqual(required, input.getKey()) || ingredientMatches(required, input.getKey())) {
                found += input.getValue();
            }
        }
        return found;
    }

    /**
     * 判断输入 Ingredient 的任意代表物品是否能满足需求 Ingredient。
     *
     * @param required 配方需求 Ingredient
     * @param input 实际输入 Ingredient
     * @return 输入 Ingredient 是否能被需求 Ingredient 接受
     */
    private static boolean ingredientMatches(Ingredient required, Ingredient input) {
        if (required == null || input == null) return false;

        ItemStack[] inputStacks = input.getItems();
        if (inputStacks.length == 0) return false;

        for (ItemStack stack : inputStacks) {
            if (required.test(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 合并相同 Ingredient 的计数（用 areIngredientsEqual 判断相同）
     *
     * @param map 需要写入的 Ingredient 计数 Map
     * @param ingredient 要合并的 Ingredient
     * @param count 要增加的数量
     */
    public static void mergeIngredient(Map<Ingredient, Long> map, Ingredient ingredient, long count) {
        for (Map.Entry<Ingredient, Long> entry : map.entrySet()) {
            if (areIngredientsEqual(entry.getKey(), ingredient)) {
                map.put(entry.getKey(), entry.getValue() + count);
                return;
            }
        }
        map.put(ingredient, count);
    }

    public static boolean isIngredientEmpty(@Nullable Ingredient ingredient) {
        return ingredient == null || ingredient.isEmpty();
    }

    public static List<CountedIngredient> mergeIngredients(List<Ingredient> itemInputs) {
        if (itemInputs == null || itemInputs.isEmpty()) return List.of();
        Map<Ingredient, Long> ingredientCounts = new LinkedHashMap<>();
        for (Ingredient ingredient : itemInputs) {
            if (isIngredientEmpty(ingredient)) continue;
            mergeIngredient(ingredientCounts, ingredient, 1L);
        }
        return ingredientCounts.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * 检查输入物品列表是否满足所需的 Ingredient 计数
     *
     * @param inputs 原始物品输入列表
     * @param requiredCounts 配方需求 Ingredient 到数量映射
     * @return 输入是否满足全部需求
     */
    public static boolean matchesCountedIngredients(List<ItemStack> inputs, Map<Ingredient, Long> requiredCounts) {
        List<CountedIngredient> requirements = requiredCounts.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
        return ItemIngredientAllocator.matches(requirements, inputs, 1L);
    }

    /**
     * 比较两个 Ingredient 是否代表相同的材料
     * <p>
     * 比较时忽略候选物品顺序，但要求候选数量一致，且每个候选物品和组件都能在另一方找到。
     *
     * @param a 第一个 Ingredient
     * @param b 第二个 Ingredient
     * @return 两个 Ingredient 的候选物品集合是否等价
     */
    public static boolean areIngredientsEqual(Ingredient a, Ingredient b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;

        // Custom ingredient item lists are display hints, not semantic identities.
        if (a.isCustom() || b.isCustom()) return false;

        ItemStack[] stacksA = a.getItems();
        ItemStack[] stacksB = b.getItems();

        if (stacksA.length == 0 || stacksA.length != stacksB.length) return false;

        for (ItemStack stackA : stacksA) {
            boolean found = false;
            for (ItemStack stackB : stacksB) {
                if (ItemStack.isSameItem(stackA, stackB) && ItemStack.isSameItemSameComponents(stackA, stackB)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }
}
