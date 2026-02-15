package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.ItemStackChemicalToItemStackRecipe;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.resource.PrimaryResource;
import mekanism.common.resource.ResourceType;
import mekanism.common.tags.MekanismTags;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Mekanism 冶金灌注机配方适配器
 * <p>
 * 将冶金灌注机配方（物品+化学品→物品）转换为高级合金熔炉配方
 * 同时生成基础版本和富集版本两种配方
 */
public class MetallurgicInfuserRecipeAdapter implements IRecipeAdapter<ItemStackChemicalToItemStackRecipe> {

    // Mekanism 冶金灌注机基础能量消耗
    // 处理时间：10 ticks (0.5秒)
    private static final int MEK_ENERGY_PER_TICK = 50;
    private static final int MEK_PROCESS_TICKS = 20;
    private static final int TOTAL_ENERGY = MEK_ENERGY_PER_TICK * MEK_PROCESS_TICKS; // 500

    // 化学品到物品的转换比例定义
    // 格式: 基础物品, 基础物品产出化学品量(mb), 富集物品, 富集物品产出化学品量(mb)
    private record ChemicalConversionInfo(
            Ingredient baseItem,
            long baseItemAmount,
            Ingredient enrichedItem,
            long enrichedItemAmount
    ) {
        boolean hasEnrichedVersion() {
            return enrichedItem != null && enrichedItem != Ingredient.EMPTY && enrichedItemAmount > 0;
        }
    }

    @Override
    public Class<ItemStackChemicalToItemStackRecipe> getRecipeClass() {
        return ItemStackChemicalToItemStackRecipe.class;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<ItemStackChemicalToItemStackRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (holder == null) return result;

        ItemStackChemicalToItemStackRecipe originalRecipe = holder.value();

        // 只处理冶金灌注机配方
        if (!originalRecipe.getType().equals(MekanismRecipeTypes.TYPE_METALLURGIC_INFUSING.value())) {
            return result;
        }

        ResourceLocation originalId = holder.id();

        // 获取物品输入
        var itemInput = originalRecipe.getItemInput();
        if (itemInput == null || itemInput.hasNoMatchingInstances()) {
            return result;
        }

        // 获取化学品输入
        var chemicalInput = originalRecipe.getChemicalInput();
        if (chemicalInput == null || chemicalInput.hasNoMatchingInstances()) {
            return result;
        }

        // 获取输出
        List<ItemStack> outputs = originalRecipe.getOutputDefinition();
        if (outputs.isEmpty()) {
            return result;
        }

        // 获取化学品的代表栈和所需数量
        List<ChemicalStack> chemicalRepresentations = chemicalInput.getRepresentations();
        if (chemicalRepresentations.isEmpty()) {
            return result;
        }

        ChemicalStack chemicalStack = chemicalRepresentations.getFirst();
        long requiredChemicalAmount = chemicalInput.amount(); // 配方所需的化学品mb数

        // 获取该化学品的转换信息
        ChemicalConversionInfo conversionInfo = getChemicalConversionInfo(chemicalStack.getChemicalHolder());
        if (conversionInfo == null) {
            // 未知的化学品类型，无法转换
            return result;
        }

        // 获取物品输入的代表栈
        var itemRepresentations = itemInput.getRepresentations();
        if (itemRepresentations.isEmpty()) {
            return result;
        }

        // 创建物品输入 Ingredient
        Ingredient itemIngredient = Ingredient.of(itemRepresentations.stream());

        // 创建冶金灌注机模具要求
        Ingredient moldIngredient = Ingredient.of(new ItemStack(MekanismBlocks.METALLURGIC_INFUSER.get()));

        // ========== 基础版本配方 ==========
        // 计算需要多少个基础物品
        long baseItemCount = requiredChemicalAmount / conversionInfo.baseItemAmount;
        if (baseItemCount <= 0) baseItemCount = 1;
        final long finalBaseItemCount = baseItemCount;

        ResourceLocation baseConvertedId = ResourceLocation.fromNamespaceAndPath(
                originalId.getNamespace(),
                originalId.getPath() + "_converted"
        );

        List<CountedIngredient> baseCountedIngredients = List.of(
                new CountedIngredient(itemIngredient, 1),  // 原配方物品数量保持1
                new CountedIngredient(conversionInfo.baseItem, finalBaseItemCount)
        );

        // 基础版本输出保持原配方输出
        List<ItemStack> baseScaledOutputs = outputs.stream()
                .map(stack -> stack.copyWithCount(stack.getCount()))
                .toList();

        AdvancedAlloyFurnaceRecipe baseRecipe = new AdvancedAlloyFurnaceRecipe(
                baseConvertedId,
                baseCountedIngredients,
                List.of(),
                baseScaledOutputs,
                List.of(),
                TOTAL_ENERGY,      // 使用Mek原配方的总能量消耗
                MEK_PROCESS_TICKS, // 使用Mek原配方的处理时间
                Ingredient.EMPTY,
                0,
                moldIngredient,
                AlloyFurnaceMode.NORMAL
        );
        result.add(baseRecipe);

        // ========== 富集版本配方（使用最小公倍数）==========
        if (conversionInfo.hasEnrichedVersion()) {
            long gcd = gcd(requiredChemicalAmount, conversionInfo.enrichedItemAmount);
            long lcm = (requiredChemicalAmount * conversionInfo.enrichedItemAmount) / gcd;

            // 计算需要多少个富集物品
            long enrichedItemCount = lcm / conversionInfo.enrichedItemAmount;
            // 计算物品输入的倍数
            long itemMultiplier = lcm / requiredChemicalAmount;

            // 只有当富集配方确实更高效（富集物品数量少于基础物品数量×倍数）时才创建
            if (enrichedItemCount < baseItemCount * itemMultiplier) {
                final long finalItemMultiplier = itemMultiplier;

                ResourceLocation enrichedConvertedId = ResourceLocation.fromNamespaceAndPath(
                        originalId.getNamespace(),
                        originalId.getPath() + "_converted_enriched"
                );

                List<CountedIngredient> enrichedCountedIngredients = List.of(
                        new CountedIngredient(itemIngredient, finalItemMultiplier),  // 物品数量×倍数
                        new CountedIngredient(conversionInfo.enrichedItem, enrichedItemCount)  // 富集物品数量
                );

                // 富集版本输出×倍数
                List<ItemStack> enrichedScaledOutputs = outputs.stream()
                        .map(stack -> stack.copyWithCount((int) (stack.getCount() * finalItemMultiplier)))
                        .toList();

                AdvancedAlloyFurnaceRecipe enrichedRecipe = new AdvancedAlloyFurnaceRecipe(
                        enrichedConvertedId,
                        enrichedCountedIngredients,
                        List.of(),
                        enrichedScaledOutputs,
                        List.of(),
                        TOTAL_ENERGY * (int) finalItemMultiplier,      // 总能量×倍数
                        MEK_PROCESS_TICKS * (int) finalItemMultiplier, // 处理时间×倍数
                        Ingredient.EMPTY,
                        0,
                        moldIngredient,
                        AlloyFurnaceMode.NORMAL
                );
                result.add(enrichedRecipe);
            }
        }

        return result;
    }

    /**
     * 获取化学品的转换信息
     * 根据化学品的注册信息返回对应的物品转换比例
     */
    @Nullable
    private ChemicalConversionInfo getChemicalConversionInfo(Holder<Chemical> chemical) {
        var chemicalKey = chemical.getKey();
        if (chemicalKey == null) return null;

        String path = chemicalKey.location().getPath();

        return switch (path) {
            case "redstone" -> new ChemicalConversionInfo(
                    Ingredient.of(Tags.Items.DUSTS_REDSTONE),  // 红石粉
                    10,                                         // 1红石粉 = 10mb
                    Ingredient.of(MekanismTags.Items.ENRICHED_REDSTONE), // 富集红石
                    80                                          // 1富集红石 = 80mb
            );
            case "diamond" -> new ChemicalConversionInfo(
                    Ingredient.of(MekanismTags.Items.DUSTS_DIAMOND), // 钻石粉
                    10,
                    Ingredient.of(MekanismTags.Items.ENRICHED_DIAMOND),
                    80
            );
            case "refined_obsidian" -> new ChemicalConversionInfo(
                    Ingredient.of(MekanismTags.Items.DUSTS_REFINED_OBSIDIAN),
                    10,
                    Ingredient.of(MekanismTags.Items.ENRICHED_OBSIDIAN),
                    80
            );
            case "carbon" -> new ChemicalConversionInfo(
                    Ingredient.of(Items.COAL),  // 煤炭
                    10,
                    Ingredient.of(MekanismTags.Items.ENRICHED_CARBON),
                    80
            );
            case "bio" -> new ChemicalConversionInfo(
                    Ingredient.of(MekanismTags.Items.FUELS_BIO), // 生物燃料
                    5,
                    Ingredient.of(MekanismTags.Items.FUELS_BLOCK_BIO), // 生物燃料块
                    45  // 5 * 9
            );
            case "fungi" -> new ChemicalConversionInfo(
                    Ingredient.of(Tags.Items.MUSHROOMS), // 蘑菇
                    10,
                    Ingredient.EMPTY,  // 无富集版本
                    0
            );
            case "gold" -> new ChemicalConversionInfo(
                    Ingredient.of(MekanismTags.Items.PROCESSED_RESOURCES.get(
                            ResourceType.DUST,
                            PrimaryResource.GOLD)),
                    10,
                    Ingredient.of(MekanismTags.Items.ENRICHED_GOLD),
                    80
            );
            case "tin" -> new ChemicalConversionInfo(
                    Ingredient.of(MekanismTags.Items.PROCESSED_RESOURCES.get(
                            ResourceType.DUST,
                            PrimaryResource.TIN)),
                    10,
                    Ingredient.of(MekanismTags.Items.ENRICHED_TIN),
                    80
            );
            default -> null; // 未知的化学品类型
        };
    }

    @Override
    public boolean canHandle(Level level, List<ItemStack> inputs) {
        return findMatchingRecipe(level, inputs) != null;
    }

    @Override
    @Nullable
    public RecipeHolder<ItemStackChemicalToItemStackRecipe> findMatchingRecipe(Level level, List<ItemStack> inputs) {
        if (level == null || inputs.isEmpty()) {
            return null;
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<ItemStackChemicalToItemStackRecipe>> recipes = recipeManager.getAllRecipesFor(
                MekanismRecipeTypes.TYPE_METALLURGIC_INFUSING.value()
        );

        for (RecipeHolder<ItemStackChemicalToItemStackRecipe> holder : recipes) {
            ItemStackChemicalToItemStackRecipe recipe = holder.value();
            var itemInput = recipe.getItemInput();
            var chemicalInput = recipe.getChemicalInput();

            if (itemInput == null || itemInput.hasNoMatchingInstances()) continue;
            if (chemicalInput == null || chemicalInput.hasNoMatchingInstances()) continue;

            // 获取化学品的转换信息
            List<ChemicalStack> chemicalRepresentations = chemicalInput.getRepresentations();
            if (chemicalRepresentations.isEmpty()) continue;

            ChemicalStack chemicalStack = chemicalRepresentations.get(0);
            ChemicalConversionInfo conversionInfo = getChemicalConversionInfo(chemicalStack.getChemicalHolder());
            if (conversionInfo == null) continue;

            long requiredChemicalAmount = chemicalInput.amount();

            // 检查是否匹配基础版本配方
            long baseItemCount = requiredChemicalAmount / conversionInfo.baseItemAmount;
            if (baseItemCount <= 0) baseItemCount = 1;

            boolean matchesBase = false;
            boolean matchesEnriched = false;

            // 检查基础物品匹配
            for (ItemStack stack : inputs) {
                if (stack.isEmpty()) continue;
                if (itemInput.test(stack)) {
                    matchesBase = true;
                }
            }

            // 检查富集版本匹配
            if (conversionInfo.hasEnrichedVersion()) {
                // 计算最小公倍数
                long gcd = gcd(requiredChemicalAmount, conversionInfo.enrichedItemAmount);
                long lcm = (requiredChemicalAmount * conversionInfo.enrichedItemAmount) / gcd;

                long enrichedItemCount = lcm / conversionInfo.enrichedItemAmount;
                long itemMultiplier = lcm / requiredChemicalAmount;

                // 只有当富集配方确实更高效时才检查
                if (enrichedItemCount < baseItemCount * itemMultiplier) {
                    for (ItemStack stack : inputs) {
                        if (stack.isEmpty()) continue;
                        // 检查是否匹配原始物品输入
                        if (itemInput.test(stack) && stack.getCount() >= itemMultiplier) {
                            // 检查是否有足够的富集物品
                            for (ItemStack stack2 : inputs) {
                                if (stack2.isEmpty()) continue;
                                if (conversionInfo.enrichedItem.test(stack2) && stack2.getCount() >= enrichedItemCount) {
                                    matchesEnriched = true;
                                    break;
                                }
                            }
                        }
                        if (matchesEnriched) break;
                    }
                }
            }

            if (matchesBase || matchesEnriched) {
                return holder;
            }
        }

        return null;
    }

    @Override
    public int getPriority() {
        return 75; // 优先级低于EAE和AAE配方
    }

    /**
     * 计算最大公约数 (Greatest Common Divisor)
     */
    private long gcd(long a, long b) {
        while (b != 0) {
            long temp = b;
            b = a % b;
            a = temp;
        }
        return a;
    }
}
