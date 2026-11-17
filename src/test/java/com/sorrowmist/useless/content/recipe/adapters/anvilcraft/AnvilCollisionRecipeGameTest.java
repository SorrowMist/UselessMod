package com.sorrowmist.useless.content.recipe.adapters.anvilcraft;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeOutputConstraint;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.block.ModBlockTags;
import dev.dubhe.anvilcraft.recipe.anvil.collision.AnvilCollisionCraftRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.providers.number.BinomialDistributionGenerator;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@GameTestHolder("useless_mod")
@PrefixGameTestTemplate(false)
public final class AnvilCollisionRecipeGameTest {
    private AnvilCollisionRecipeGameTest() {
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void collisionMaterialsAndExpectedOutputsMatchSource(GameTestHelper helper) {
        Level level = helper.getLevel();
        List<RecipeHolder<AnvilCollisionCraftRecipe>> sources = sources(level);
        List<AdvancedAlloyFurnaceRecipe> catalog = catalog(level);
        int converted = 0;
        int isolated = 0;
        int variants = 0;
        for (var holder : sources) {
            var source = holder.value();
            List<AdvancedAlloyFurnaceRecipe> recipes = find(catalog, holder.id());
            boolean hasBlockEntityOutput = source.transformBlocks().stream()
                    .anyMatch(transform -> !transform.outputBlock().nbt().isEmpty());
            if (hasBlockEntityOutput) {
                require(recipes.isEmpty(), "带方块实体数据的碰撞配方未隔离：" + holder.id());
                isolated++;
                continue;
            }
            require(!recipes.isEmpty(), "缺少碰撞配方：" + holder.id());
            converted++;
            variants += recipes.size();
            for (var recipe : recipes) {
                int speed = speedFromId(recipe.id());
                ResourceLocation expectedId = AdapterUtils.convertedId(holder.id()).withSuffix("/speed/" + speed);
                require(recipe.id().equals(expectedId), "单撞击方块的碰撞配方 ID 应仅由来源和速度档位构成：" + recipe.id());
                require(recipe.mode() == AlloyFurnaceMode.NORMAL, "碰撞速度不应进入通用配方模式：" + recipe.id());
                require(speed >= source.speed(), "碰撞速度低于原配方：" + recipe.id());
                require(speed == 32 || speed == 128 || speed == 256, "未知碰撞速度档位：" + recipe.id());
                long operations = recipe.processTime() / AdapterUtils.DEFAULT_PROCESS_TIME;
                long expectedEnergy = AdapterUtils.energyForProcessTime(recipe.processTime()) * (speed / 32L);
                require(operations > 0 && recipe.processTime() == operations * AdapterUtils.DEFAULT_PROCESS_TIME
                        && recipe.energy() == expectedEnergy,
                        "碰撞批次能量或耗时无效：" + recipe.id());
                List<Block> hits = source.hitBlock().getBlocks().stream().map(entry -> entry.value())
                        .filter(block -> recipe.inputs().stream().anyMatch(input -> input.ingredient().test(new ItemStack(block)))
                                || recipe.molds().stream().anyMatch(mold -> mold.test(new ItemStack(block)))).toList();
                require(hits.size() == 1, "撞击方块无法对应原配方：" + recipe.id());
                Block hit = hits.getFirst();
                List<Block> anvils = source.anvil().getBlocks().stream().map(entry -> entry.value())
                        .filter(anvil -> sources.stream().noneMatch(peer -> !peer.id().equals(holder.id())
                                && peer.value().speed() >= source.speed() && peer.value().speed() <= speed
                                && peer.value().anvil().getBlocks().stream().anyMatch(entry -> entry.value() == anvil)
                                && peer.value().hitBlock().getBlocks().stream().anyMatch(entry -> entry.value() == hit)))
                        .toList();
                require(!anvils.isEmpty(), "生成了被更高速配方覆盖的变体：" + recipe.id());
                Map<String, Long> expectedInputs = new TreeMap<>();
                List<String> expectedMolds = new ArrayList<>();
                String anvilOptions = options(anvils.stream().map(ItemStack::new).toArray(ItemStack[]::new));
                if (source.consume()) expectedInputs.merge(anvilOptions, operations, Long::sum);
                else expectedMolds.add(anvilOptions);
                String hitOptions = options(new ItemStack[]{new ItemStack(hit)});
                if (hit.defaultBlockState().is(ModBlockTags.COLLISION_IMMUNE)) expectedMolds.add(hitOptions);
                else expectedInputs.merge(hitOptions, operations, Long::sum);
                expectedMolds.add(options(new ItemStack[]{new ItemStack(ModBlocks.ACCELERATION_RING.asItem())}));
                expectedMolds.add(options(new ItemStack[]{new ItemStack(ModBlocks.DEFLECTION_RING.asItem())}));
                Map<String, Double> expectedOutputs = new TreeMap<>();
                for (var transform : source.transformBlocks()) {
                    String inputs = options(transform.inputBlock().getBlocks().stream()
                            .map(entry -> new ItemStack(entry.value())).toArray(ItemStack[]::new));
                    expectedInputs.merge(inputs, transform.maxCount() * operations, Long::sum);
                    expectedOutputs.merge(itemId(new ItemStack(transform.outputBlock().state().getBlock())),
                            (double) transform.maxCount(), Double::sum);
                }
                for (var output : source.outputItems()) {
                    expectedOutputs.merge(itemId(output.stack()), expectedCount(output.count()), Double::sum);
                }
                Map<String, Long> actualInputs = new TreeMap<>();
                recipe.inputs().forEach(input -> actualInputs.merge(options(input.ingredient().getItems()), input.count(), Long::sum));
                require(expectedInputs.equals(actualInputs), "碰撞材料或满批数量不一致：" + recipe.id()
                        + "，预期 " + expectedInputs + "，实际 " + actualInputs);
                List<String> actualMolds = recipe.molds().stream().map(mold -> options(mold.getItems())).sorted().toList();
                require(expectedMolds.stream().sorted().toList().equals(actualMolds), "铁砧消耗或模具不一致：" + recipe.id());
                Map<String, Double> actualOutputs = new TreeMap<>();
                recipe.outputs().forEach(output -> actualOutputs.merge(itemId(output), (double) output.getCount() / operations, Double::sum));
                require(expectedOutputs.keySet().equals(actualOutputs.keySet()), "碰撞产物种类不一致：" + recipe.id());
                expectedOutputs.forEach((item, count) -> require(Math.abs(count - actualOutputs.get(item)) < 0.00001,
                        "碰撞概率期望数量不一致：" + recipe.id() + "，物品 " + item));
            }
        }
        require(converted == 16 && isolated == 2, "碰撞覆盖数量变化：转换 " + converted + "，隔离 " + isolated);
        require(variants == 29, "碰撞速度变体数量变化：" + variants);
        require(variants == catalog.stream().filter(recipe -> hasSpeedVariant(recipe.id())).count(), "碰撞目录存在重复或额外条目");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void collisionRecipesRequireBothRingMolds(GameTestHelper helper) {
        Level level = helper.getLevel();
        List<AdvancedAlloyFurnaceRecipe> catalog = catalog(level);
        var recipe = find(catalog, collisionId("anvil_tier_0_and_corrupted_beacon_32")).getFirst();
        List<ItemStack> inputs = inputStacks(recipe);
        AlloyFurnaceRecipeManager manager = AlloyFurnaceRecipeManager.getInstance();
        manager.clearCache();
        ItemStack accelerationRing = new ItemStack(ModBlocks.ACCELERATION_RING.asItem());
        ItemStack deflectionRing = new ItemStack(ModBlocks.DEFLECTION_RING.asItem());
        require(recipe.molds().stream().filter(mold -> mold.test(accelerationRing)).count() == 1
                        && recipe.molds().stream().filter(mold -> mold.test(deflectionRing)).count() == 1,
                "碰撞配方未将两个环保留为独立模具");
        require(manager.findRecipe(level, inputs, List.of(), List.of(), ItemStack.EMPTY, -1) == null,
                "双模具碰撞配方可通过普通单模具查找执行");
        for (ItemStack ring : List.of(accelerationRing, deflectionRing)) {
            require(manager.findRecipe(level, inputs, List.of(), List.of(), ring, -1) == null,
                    "仅提供一个环时仍可执行碰撞配方");
            require(manager.findRecipeForCraftingWithConstraints(level, inputs, List.of(), List.of(), ring,
                    constraints(recipe), 1, -1) == null, "万象样板缺少另一个环时仍可执行碰撞配方");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void collisionPatternsResolveExactSpeedVariant(GameTestHelper helper) {
        Level level = helper.getLevel();
        int variants = 0;
        for (var entry : AlloyFurnaceRecipeCatalog.entries(level, RecipeSourceIds.ANVILCRAFT)) {
            if (!hasSpeedVariant(entry.recipe().id())) continue;
            int speed = speedFromId(entry.recipe().id());
            var resolved = AlloyFurnaceRecipeCatalog.resolve(level, entry.identity()).orElse(null);
            require(resolved != null && resolved.identity().equals(entry.identity())
                            && resolved.recipe().id().equals(entry.recipe().id())
                            && speedFromId(resolved.recipe().id()) == speed
                            && resolved.recipe().mode() == AlloyFurnaceMode.NORMAL,
                    "万象样板身份未能唯一解析碰撞速度变体：" + entry.recipe().id());
            variants++;
        }
        require(variants == 29, "碰撞速度变体数量变化：" + variants);
        helper.succeed();
    }

    private static List<RecipeOutputConstraint> constraints(AdvancedAlloyFurnaceRecipe recipe) {
        return RecipeOutputConstraint.exact(recipe.outputs().stream()
                .map(output -> new GenericStack(AEItemKey.of(output), output.getCount())).toList());
    }

    private static int speedFromId(ResourceLocation id) {
        String path = id.getPath();
        String marker = "/speed/";
        int markerIndex = path.indexOf(marker);
        if (markerIndex < 0) throw new AssertionError("碰撞配方 ID 缺少速度档位：" + id);
        int start = markerIndex + marker.length();
        int end = path.indexOf('/', start);
        return Integer.parseInt(end < 0 ? path.substring(start) : path.substring(start, end));
    }

    private static boolean hasSpeedVariant(ResourceLocation id) {
        return id.getPath().contains("/speed/");
    }

    private static List<ItemStack> inputStacks(AdvancedAlloyFurnaceRecipe recipe) {
        return new ArrayList<>(recipe.inputs().stream().map(input -> input.ingredient().getItems()[0]
                .copyWithCount(Math.toIntExact(input.count()))).toList());
    }

    @SuppressWarnings("unchecked")
    private static List<RecipeHolder<AnvilCollisionCraftRecipe>> sources(Level level) {
        return level.getRecipeManager().getRecipes().stream().filter(holder -> holder.value() instanceof AnvilCollisionCraftRecipe)
                .map(holder -> (RecipeHolder<AnvilCollisionCraftRecipe>) holder).toList();
    }

    private static List<AdvancedAlloyFurnaceRecipe> catalog(Level level) {
        return AlloyFurnaceRecipeCatalog.entries(level, RecipeSourceIds.ANVILCRAFT).stream()
                .map(AlloyFurnaceRecipeCatalog.Entry::recipe).toList();
    }

    private static List<AdvancedAlloyFurnaceRecipe> find(List<AdvancedAlloyFurnaceRecipe> catalog, ResourceLocation id) {
        String base = id.getPath() + "_converted/";
        return catalog.stream().filter(recipe -> recipe.id().getNamespace().equals(id.getNamespace())
                && recipe.id().getPath().startsWith(base)).toList();
    }

    private static ResourceLocation collisionId(String name) {
        return ResourceLocation.fromNamespaceAndPath("anvilcraft", "anvil_collision/" + name);
    }

    private static String options(ItemStack[] stacks) {
        return Arrays.stream(stacks).map(AnvilCollisionRecipeGameTest::itemId).sorted().toList().toString();
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static double expectedCount(NumberProvider provider) {
        if (provider instanceof ConstantValue constant) return Math.floor(constant.value());
        if (provider instanceof UniformGenerator uniform && uniform.min() instanceof ConstantValue min
                && uniform.max() instanceof ConstantValue max) return (Math.floor(min.value()) + Math.floor(max.value())) / 2;
        if (provider instanceof BinomialDistributionGenerator binomial && binomial.n() instanceof ConstantValue rolls
                && binomial.p() instanceof ConstantValue chance) return Math.floor(rolls.value()) * chance.value();
        return Double.NaN;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
