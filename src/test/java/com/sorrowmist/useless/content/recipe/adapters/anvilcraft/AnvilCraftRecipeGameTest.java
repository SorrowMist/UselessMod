package com.sorrowmist.useless.content.recipe.adapters.anvilcraft;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.mojang.logging.LogUtils;
import dev.anvilcraft.lib.v2.multiblock.dynamic.definition.DefinitionSerialization;
import dev.dubhe.anvilcraft.api.block.BlockPlacementRules;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.SolidLiquidRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.MeshRecipe;
import dev.dubhe.anvilcraft.recipe.generate.JewelCraftingRecipeGeneratingCache;
import dev.dubhe.anvilcraft.recipe.generate.MeshRecipeGeneratingCache;
import dev.dubhe.anvilcraft.recipe.frost.CustomFrostMaterialPredicate;
import dev.dubhe.anvilcraft.recipe.frost.FrostSmithingRecipeInput;
import dev.dubhe.anvilcraft.recipe.frost.PermutationRecipe;
import dev.dubhe.anvilcraft.recipe.multiple.BaseMultipleToOneSmithingRecipe;
import dev.dubhe.anvilcraft.recipe.multiple.MultipleToOneSmithingRecipeInput;
import dev.dubhe.anvilcraft.recipe.multiblock.MultiblockRecipe;
import dev.dubhe.anvilcraft.recipe.multiblock.MultiblockUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.providers.number.BinomialDistributionGenerator;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.fluids.FluidStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

// 空结构模板来源：AE2 Lightning Tech 的 GameTest empty.nbt。
@GameTestHolder("useless_mod")
@PrefixGameTestTemplate(false)
public final class AnvilCraftRecipeGameTest {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> SOURCE_TYPES = Set.of(
            "Multiblock4DRecipe", "MultiblockRecipe", "MultiblockConversionRecipe",
            "BlockCompressRecipe", "BlockCrushRecipe", "ItemCompressRecipe", "ItemCrushRecipe",
            "BlockSmearRecipe", "ChargerChargingRecipe", "FastCookingRecipe", "FluidMixingRecipe",
            "DeformationRecipe", "PermutationRecipe", "NeutronIrradiationRecipe", "ProceduralProcessRecipe",
            "TimeWarpRecipe", "SolidLiquidRecipe", "SqueezingRecipe", "StampingRecipe", "StampingUniqueItemsRecipe", "UnpackRecipe",
            "SuperHeatingRecipe", "JewelCraftingRecipe", "ItemInjectRecipe", "MassInjectRecipe", "MeshRecipe",
            "TwoToOneSmithingRecipe", "FourToOneSmithingRecipe", "EightToOneSmithingRecipe", "AnvilCollisionCraftRecipe");
    private static final Set<String> ISOLATED_SOURCES = Set.of(
            "ChargerChargingRecipe anvilcraft:charger_charging/empty_capacitor",
            "ChargerChargingRecipe anvilcraft:charger_charging/empty_supercapacitor",
            "ChargerChargingRecipe anvilcraft:charger_charging/neutronium_ingot",
            "ItemCompressRecipe anvilcraft:item_compress/empty_supercapacitor",
            "MultiblockConversionRecipe anvilcraft:multiblock_conversion/spawner",
            "StampingUniqueItemsRecipe anvilcraft:stamping_unique_items/eight_to_one_smithing_template",
            "StampingUniqueItemsRecipe anvilcraft:stamping_unique_items/four_to_one_smithing_template",
            "StampingUniqueItemsRecipe anvilcraft:stamping_unique_items/two_to_one_smithing_template",
            "SuperHeatingRecipe anvilcraft:super_heating/royal_steel_block_base",
            "SuperHeatingRecipe anvilcraft:super_heating/royal_steel_block_bonus_1",
            "SuperHeatingRecipe anvilcraft:super_heating/royal_steel_block_bonus_2",
            "SuperHeatingRecipe anvilcraft:super_heating/royal_steel_ingot_base",
            "SuperHeatingRecipe anvilcraft:super_heating/royal_steel_ingot_bonus_1",
            "SuperHeatingRecipe anvilcraft:super_heating/royal_steel_ingot_bonus_2",
            "TimeWarpRecipe anvilcraft:time_warp/amber_block",
            "UnpackRecipe anvilcraft:unpack/fluid_tank_minecart",
            "AnvilCollisionCraftRecipe anvilcraft:anvil_collision/anvil_tier_2_and_uranium_block_256",
            "AnvilCollisionCraftRecipe anvilcraft:anvil_collision/anvil_tier_2_and_plutonium_block_256");

    private AnvilCraftRecipeGameTest() {
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void sourceRecipeCoverage(GameTestHelper helper) {
        Level level = helper.getLevel();
        List<RecipeHolder<?>> sources = new ArrayList<>(level.getRecipeManager().getRecipes());
        sources.addAll(new MeshRecipeGeneratingCache(level.registryAccess()).buildRecipes().orElse(List.of()));
        sources.addAll(new JewelCraftingRecipeGeneratingCache(level.registryAccess()).buildRecipes().orElse(List.of()));
        List<AdvancedAlloyFurnaceRecipe> converted = AlloyFurnaceRecipeCatalog.entries(level, RecipeSourceIds.ANVILCRAFT)
                .stream().map(AlloyFurnaceRecipeCatalog.Entry::recipe).toList();
        Map<String, Integer> counts = new TreeMap<>();
        Set<String> missing = new TreeSet<>();
        for (RecipeHolder<?> source : sources) {
            String type = source.value().getClass().getSimpleName();
            if (!SOURCE_TYPES.contains(type)) continue;
            counts.merge(type, 1, Integer::sum);
            if (find(converted, source.id()).isEmpty()) missing.add(type + " " + source.id());
        }
        if (counts.isEmpty() || converted.isEmpty()) throw new AssertionError("未加载铁砧工艺配方或兼容目录：" + counts);
        LOGGER.info("铁砧工艺原配方统计：{}；缺少转换：{}", counts, missing);
        Set<String> unexpected = new TreeSet<>(missing);
        unexpected.removeAll(ISOLATED_SOURCES);
        if (!unexpected.isEmpty()) throw new AssertionError("出现未登记的缺失配方：" + unexpected);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void convertedRecipesUseBalancedPower(GameTestHelper helper) {
        List<AdvancedAlloyFurnaceRecipe> recipes = AlloyFurnaceRecipeCatalog.entries(helper.getLevel(), RecipeSourceIds.ANVILCRAFT)
                .stream().map(AlloyFurnaceRecipeCatalog.Entry::recipe).toList();
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (AdvancedAlloyFurnaceRecipe recipe : recipes) {
            if (recipe.id().getPath().startsWith("anvil_collision/")) continue;
            checked++;
            if (recipe.processTime() <= 0) {
                failures.add(recipe.id() + "：处理时长无效");
                continue;
            }
            long expectedEnergy = AdapterUtils.energyForProcessTime(recipe.processTime());
            if (recipe.energy() != expectedEnergy) failures.add(recipe.id() + "：" + recipe.energy() + "/" + recipe.processTime());
        }
        if (checked == 0 || !failures.isEmpty()) {
            throw new AssertionError("普通铁砧工艺配方未保持统一功率：检查数 " + checked + "；异常 " + failures);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void multiblockResultsMatchSource(GameTestHelper helper) {
        Level level = helper.getLevel();
        List<AdvancedAlloyFurnaceRecipe> converted = AlloyFurnaceRecipeCatalog.entries(level, RecipeSourceIds.ANVILCRAFT)
                .stream().map(AlloyFurnaceRecipeCatalog.Entry::recipe).toList();
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof MultiblockRecipe source)) continue;
            checked++;
            List<AdvancedAlloyFurnaceRecipe> matches = find(converted, holder.id());
            if (matches.size() != 1 || matches.getFirst().outputs().size() != 1
                    || !ItemStack.matches(source.getResult(), matches.getFirst().outputs().getFirst())) {
                failures.add(holder.id().toString());
                continue;
            }
            DefinitionSerialization pattern = DefinitionSerialization.fromDefinition(source.getPattern());
            Map<String, Long> expected = new TreeMap<>();
            for (String[] layer : pattern.grid()) for (String row : layer) for (char symbol : row.toCharArray()) {
                if (symbol == ' ') continue;
                var predicate = pattern.mapping().get(symbol);
                if (predicate.getProperties().isEmpty()) {
                    String options = predicate.getBlocks().stream().map(block -> block.value().asItem())
                            .map(item -> BuiltInRegistries.ITEM.getKey(item).toString()).sorted().toList().toString();
                    expected.merge(options, 1L, Long::sum);
                } else {
                    var state = MultiblockUtil.getDefaultState(predicate);
                    for (ItemStack stack : BlockPlacementRules.getPlacementIngredients(level.registryAccess(), state)) {
                        expected.merge(List.of(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).toString(),
                                (long) stack.getCount(), Long::sum);
                    }
                }
            }
            Map<String, Long> actual = new TreeMap<>();
            matches.getFirst().inputs().forEach(input -> actual.merge(Arrays.stream(input.ingredient().getItems())
                    .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).sorted().toList().toString(),
                    input.count(), Long::sum));
            if (!expected.equals(actual)) {
                LOGGER.error("多方块材料不一致 {}：原配方 {}，转换后 {}", holder.id(), expected, actual);
                failures.add(holder.id().toString());
            }
        }
        if (checked == 0 || !failures.isEmpty()) throw new AssertionError("多方块合成产物不一致：" + failures + "；检查数：" + checked);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void solidLiquidWaterRecipesPreserveMaterialsAndFluid(GameTestHelper helper) {
        Level level = helper.getLevel();
        List<AdvancedAlloyFurnaceRecipe> converted = AlloyFurnaceRecipeCatalog.entries(level, RecipeSourceIds.ANVILCRAFT)
                .stream().map(AlloyFurnaceRecipeCatalog.Entry::recipe).toList();
        Map<Item, Item> cases = Map.of(Items.DIRT, Items.MUD, Items.CRIMSON_FUNGUS, Items.NETHER_WART_BLOCK,
                Items.WARPED_FUNGUS, Items.WARPED_WART_BLOCK, Items.SPIDER_EYE, Items.FERMENTED_SPIDER_EYE);
        List<String> failures = new ArrayList<>();
        cases.forEach((input, output) -> {
            List<RecipeHolder<?>> sources = level.getRecipeManager().getRecipes().stream()
                    .filter(holder -> holder.value() instanceof SolidLiquidRecipe source
                            && source.getInputItems().size() == 1 && source.getInputItems().getFirst().test(new ItemStack(input))
                            && source.getResultItems().size() == 1 && source.getResultItems().getFirst().stack().is(output)
                            && source.getHasCauldron().consume() == 0).toList();
            if (sources.size() != 1) {
                failures.add(input + "：原配方数量 " + sources.size());
                return;
            }
            List<AdvancedAlloyFurnaceRecipe> matches = find(converted, sources.getFirst().id());
            boolean valid = matches.stream().anyMatch(recipe -> recipe.inputs().size() == 1
                    && recipe.inputs().getFirst().count() == 1 && recipe.inputs().getFirst().ingredient().test(new ItemStack(input))
                    && recipe.outputs().size() == 1 && recipe.outputs().getFirst().is(output)
                    && recipe.outputs().getFirst().getCount() == 1 && recipe.inputFluids().size() == 1
                    && recipe.inputFluids().getFirst().amount() == 1000
                    && recipe.inputFluids().getFirst().ingredient().test(new FluidStack(Fluids.WATER, 1000))
                    && recipe.outputFluids().size() == 1 && recipe.outputFluids().getFirst().getFluid() == Fluids.WATER
                    && recipe.outputFluids().getFirst().getAmount() == 1000);
            if (!valid) failures.add(sources.getFirst().id().toString());
        });
        if (!failures.isEmpty()) throw new AssertionError("固液反应材料或流体不一致：" + failures);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void meshExpectedOutputsMatchSource(GameTestHelper helper) {
        Level level = helper.getLevel();
        List<RecipeHolder<?>> sources = new ArrayList<>(level.getRecipeManager().getRecipes());
        sources.addAll(new MeshRecipeGeneratingCache(level.registryAccess()).buildRecipes().orElse(List.of()));
        List<AdvancedAlloyFurnaceRecipe> converted = AlloyFurnaceRecipeCatalog.entries(level, RecipeSourceIds.ANVILCRAFT)
                .stream().map(AlloyFurnaceRecipeCatalog.Entry::recipe).toList();
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (RecipeHolder<?> holder : sources) {
            if (!(holder.value() instanceof MeshRecipe source)) continue;
            checked++;
            List<AdvancedAlloyFurnaceRecipe> matches = find(converted, holder.id());
            if (matches.size() != 1) {
                failures.add(holder.id() + "：转换数量 " + matches.size());
                continue;
            }
            AdvancedAlloyFurnaceRecipe recipe = matches.getFirst();
            if (recipe.processTime() <= 0 || recipe.processTime() % AdapterUtils.DEFAULT_PROCESS_TIME != 0
                    || recipe.energy() != AdapterUtils.energyForProcessTime(recipe.processTime())) {
                failures.add(holder.id() + "：批次数无效");
                continue;
            }
            long operations = recipe.processTime() / AdapterUtils.DEFAULT_PROCESS_TIME;
            double[] expected = new double[recipe.outputs().size()];
            boolean valid = true;
            for (var output : source.getResultItems()) {
                int index = -1;
                for (int i = 0; i < recipe.outputs().size(); i++) {
                    if (ItemStack.isSameItemSameComponents(output.stack(), recipe.outputs().get(i))) {
                        index = i;
                        break;
                    }
                }
                double amount = expectedCount(output.count());
                if (!Double.isFinite(amount) || index < 0 && amount > 0) {
                    valid = false;
                    break;
                }
                if (index >= 0) expected[index] += amount;
            }
            for (int i = 0; i < expected.length; i++) {
                if (Math.abs(expected[i] - (double) recipe.outputs().get(i).getCount() / operations) > 0.00001) {
                    valid = false;
                }
            }
            for (var input : source.getInputItems()) {
                long count = recipe.inputs().stream().filter(entry -> Arrays.stream(input.getItems())
                        .anyMatch(entry.ingredient()::test)).mapToLong(entry -> entry.count()).sum();
                if (count != input.count() * operations) valid = false;
            }
            if (!valid) failures.add(holder.id().toString());
        }
        if (checked == 0 || !failures.isEmpty()) {
            LOGGER.error("过筛配方数量或概率不一致：{}", failures);
            throw new AssertionError("过筛配方检查数：" + checked + "；不一致：" + failures.size());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void modifiedSmithingResultsMatchSource(GameTestHelper helper) {
        Level level = helper.getLevel();
        List<AdvancedAlloyFurnaceRecipe> catalog = AlloyFurnaceRecipeCatalog.entries(level, RecipeSourceIds.ANVILCRAFT)
                .stream().map(AlloyFurnaceRecipeCatalog.Entry::recipe).toList();
        RecipeHolder<?> frostHolder = level.getRecipeManager().getRecipes().stream()
                .filter(holder -> holder.id().toString().equals("anvilcraft:permutation/ember_metal_axe"))
                .findFirst().orElseThrow();
        PermutationRecipe frost = (PermutationRecipe) frostHolder.value();
        List<AdvancedAlloyFurnaceRecipe> frostRecipes = find(catalog, frostHolder.id());
        int frostChecked = 0;
        for (ItemStack equipment : frost.possibleInputs()) {
            if (equipment.isEmpty() || !equipment.isComponentsPatchEmpty()) continue;
            for (var option : frost.options(equipment)) {
                if (!(option.material() instanceof CustomFrostMaterialPredicate custom)) continue;
                for (ItemStack candidate : custom.predicate().getItems()) {
                    ItemStack material = candidate.copyWithCount(custom.predicate().count());
                    FrostSmithingRecipeInput input = new FrostSmithingRecipeInput(frost.template().getItems()[0], equipment, material);
                    int index = frost.options(input).indexOf(option);
                    if (index < 0) continue;
                    ItemStack result = frost.assemble(index, input, level);
                    int cost = frost.materialCost(input, index);
                    ItemStack renamed = equipment.copy();
                    renamed.set(DataComponents.CUSTOM_NAME, Component.literal("测试"));
                    boolean found = frostRecipes.stream().anyMatch(recipe -> recipe.outputs().size() == 1
                            && ItemStack.matches(result, recipe.outputs().getFirst())
                            && recipe.inputs().stream().anyMatch(entry -> entry.count() == 1
                            && entry.ingredient().test(equipment) && !entry.ingredient().test(renamed))
                            && recipe.inputs().stream().anyMatch(entry -> entry.count() == cost
                            && entry.ingredient().test(material)));
                    if (!found) throw new AssertionError("浮霜锻造默认组件产物或输入不一致：" + equipment);
                    frostChecked++;
                }
            }
        }
        if (frostChecked == 0) throw new AssertionError("未检查浮霜锻造变体");

        RecipeHolder<?> multiHolder = level.getRecipeManager().getRecipes().stream()
                .filter(holder -> holder.id().toString().equals("anvilcraft:four_to_one_smithing/frost_metal_heavy_halberd"))
                .findFirst().orElseThrow();
        BaseMultipleToOneSmithingRecipe multi = (BaseMultipleToOneSmithingRecipe) multiHolder.value();
        List<AdvancedAlloyFurnaceRecipe> variants = find(catalog, multiHolder.id());
        int multiChecked = 0;
        for (AdvancedAlloyFurnaceRecipe variant : variants) {
            if (!variant.id().getPath().endsWith("/ember")) continue;
            ItemStack template = multi.getTemplate().getItems()[0];
            ItemStack material = multi.getMaterial().getItems()[0].copyWithCount(multi.getMaterial().count());
            List<ItemStack> items = new ArrayList<>();
            for (var predicate : multi.getInputs()) {
                ItemStack selected = Arrays.stream(predicate.getItems()).filter(candidate -> variant.inputs().stream()
                                .anyMatch(entry -> entry.ingredient().test(candidate)))
                        .findFirst().orElseThrow().copyWithCount(predicate.count());
                items.add(selected);
            }
            MultipleToOneSmithingRecipeInput input = new MultipleToOneSmithingRecipeInput(template, material, items);
            if (!multi.matches(input, level) || variant.outputs().size() != 1
                    || !ItemStack.matches(multi.assemble(input, level), variant.outputs().getFirst())) {
                throw new AssertionError("多合一锻造产物不一致：" + variant.id());
            }
            Map<Ingredient, Long> merged = new LinkedHashMap<>();
            variant.inputs().forEach(entry -> AdapterUtils.mergeIngredient(merged, entry.ingredient(), entry.count()));
            ItemStack transcendence = Multiblock4DRecipeAdapter.mold("transcendence_smithing_table").getItems()[0];
            if (new MultipleSmithingRecipeAdapter().findMatchingRecipes(level, merged, Map.of(), transcendence).stream()
                    .noneMatch(holder -> holder.id().equals(multiHolder.id()))) {
                throw new AssertionError("多合一锻造变体无法查找：" + variant.id());
            }
            multiChecked++;
        }
        if (multiChecked == 0) throw new AssertionError("未检查多合一锻造变体");
        helper.succeed();
    }

    private static double expectedCount(NumberProvider provider) {
        if (provider instanceof ConstantValue constant) return Math.floor(constant.value());
        if (provider instanceof UniformGenerator uniform && uniform.min() instanceof ConstantValue min
                && uniform.max() instanceof ConstantValue max) return (Math.floor(min.value()) + Math.floor(max.value())) / 2;
        if (provider instanceof BinomialDistributionGenerator binomial && binomial.n() instanceof ConstantValue rolls
                && binomial.p() instanceof ConstantValue chance) return Math.floor(rolls.value()) * chance.value();
        return Double.NaN;
    }

    private static List<AdvancedAlloyFurnaceRecipe> find(List<AdvancedAlloyFurnaceRecipe> recipes, ResourceLocation source) {
        String base = source.getPath() + "_converted";
        return recipes.stream().filter(recipe -> recipe.id().getNamespace().equals(source.getNamespace())
                && (recipe.id().getPath().equals(base) || recipe.id().getPath().startsWith(base + "/"))).toList();
    }
}
